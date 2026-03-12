package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.entity.PassEntity;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.entity.VisitorApprovalAudit;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import com.rkt.VisitorManagementSystem.entity.enums.PassStatus;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.repository.PassRepository;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import com.rkt.VisitorManagementSystem.repository.VisitorApprovalAuditRepository;
import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import com.rkt.VisitorManagementSystem.service.MailService;
import com.rkt.VisitorManagementSystem.service.PassManagerService;
import com.rkt.VisitorManagementSystem.service.security.QrSigner;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@CrossOrigin("*")
@RestController
@RequiredArgsConstructor
@Slf4j
public class ApprovalController {

    private final VisitorRepository visitorRepository;
    private final PassRepository passRepository;
    private final PassManagerService passManagerService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final VisitorApprovalAuditRepository auditRepository;

    @Value("${app.mail.approval-base-url:http://192.168.0.224:8099/rkt/visitors/respond}")
    private String approvalBaseUrl;

    // Configurable TTLs
    @Value("${app.mail.confirmation-ttl-minutes:15}")
    private long confirmationTtlMinutes;

    // Rate limiting config (in-memory implementation)
    @Value("${app.security.rate-limit.ip.maxAttemptsPerHour:200}")
    private int maxAttemptsPerIpPerHour;

    @Value("${app.security.rate-limit.visitor.maxInvalidAttempts:5}")
    private int maxInvalidAttemptsPerVisitor;

    @Value("${app.security.rate-limit.visitor.invalidWindowMinutes:15}")
    private int invalidWindowMinutes;

    // Simple in-memory state for rate-limiting/metrics
    private final ConcurrentHashMap<String, IpWindow> ipWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VisitorInvalidWindow> visitorInvalidWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Instant> visitorLockoutUntil = new ConcurrentHashMap<>();

    // Simple counters for quick metrics (replace with Micrometer in future)
    private final AtomicInteger totalInitialRequests = new AtomicInteger(0);
    private final AtomicInteger totalConfirmations = new AtomicInteger(0);
    private final AtomicInteger totalInvalidAttempts = new AtomicInteger(0);

    private static final int KEY_BYTES = 24; // same entropy

    /**
     * Approve/Reject endpoint (two-stage flow)
     *
     * Note: added optional `expectedAction` parameter on confirm links to detect mismatched confirmations.
     */
    @GetMapping(path = "/rkt/visitors/respond", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional
    public ResponseEntity<String> respond(HttpServletRequest request,
                                          @RequestParam(value = "token", required = false) String tokenParam,
                                          @RequestParam(value = "visitorId", required = false) Long visitorIdParam,
                                          @RequestParam(value = "action", required = false) String actionParam,
                                          @RequestParam(value = "expectedAction", required = false) String expectedActionParam,
                                          @RequestParam(value = "key", required = false) String keyParam,
                                          @RequestParam(value = "hostId", required = false) Long hostIdParam,
                                          @RequestParam(value = "confirm", required = false) Boolean confirmParam) {

        String requesterIp = extractClientIp(request);
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");

        // Rate-limit: per-IP hourly
        if (isIpRateLimited(requesterIp)) {
            log.warn("IP rate-limited: {}", requesterIp);
            recordAudit(visitorIdParam, "initial_request", actionParam, requesterIp, userAgent, "blocked", "IP rate-limited");
            return htmlBadRequest("Too many requests from your network. Please try again later.");
        }

        // 1) Try token flow (legacy)
        String visitorIdStr = null;
        String action = null;
        boolean tokenValidated = false;

        if (tokenParam != null && !tokenParam.isBlank()) {
            try {
                Map<String, String> claims = QrSigner.verifyAndParse(tokenParam, null);
                if (claims != null) {
                    visitorIdStr = claims.get("visitorId");
                    action = claims.get("action");
                    tokenValidated = (visitorIdStr != null && action != null);
                }
            } catch (Exception ex) {
                log.debug("Token parse/verify failed (falling back to query-params): {}", ex.getMessage());
            }
        }

        if ((visitorIdStr == null || visitorIdStr.isBlank()) && visitorIdParam != null) {
            visitorIdStr = String.valueOf(visitorIdParam);
        }
        if (action == null && actionParam != null) {
            action = actionParam;
        }

        if (visitorIdStr == null || action == null) {
            return htmlBadRequest("Missing required parameters. Please use the approve/reject link from the email.");
        }

        Long visitorId;
        try {
            visitorId = Long.valueOf(visitorIdStr);
        } catch (NumberFormatException nfe) {
            return htmlBadRequest("Invalid visitorId parameter.");
        }

        // Check visitor lockout
        if (isVisitorLockedOut(visitorId)) {
            recordAudit(visitorId, "any", action, requesterIp, userAgent, "blocked", "visitor locked-out");
            return htmlBadRequest("This visitor's approval is temporarily locked due to repeated invalid attempts. Please contact Security Desk.");
        }

        // Load visitor
        VisitorEntity v = visitorRepository.findById(visitorId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found: " + visitorId));

        // Already processed?
        if (v.getVisitStatus() != null && v.getVisitStatus() != VisitStatus.PENDING) {
            recordAudit(visitorId, "any", action, requesterIp, userAgent, "already_processed", null);
            return ResponseEntity.ok(alreadyProcessedHtml(v));
        }

        // Validate provided key (if token not used)
        if (!tokenValidated) {
            if (keyParam == null || keyParam.isBlank()) {
                recordInvalidAttempt(visitorId, requesterIp);
                return htmlBadRequest("Missing approval key. Please use the link provided in the email.");
            }

            if (v.getApprovalKeyExpiry() == null || Instant.now().isAfter(v.getApprovalKeyExpiry())) {
                recordInvalidAttempt(visitorId, requesterIp);
                recordAudit(visitorId, "initial_request", action, requesterIp, userAgent, "expired", null);
                return htmlBadRequest("This approval link has expired.");
            }

            if (v.getApprovalForUserId() != null && hostIdParam != null && !v.getApprovalForUserId().equals(hostIdParam)) {
                recordInvalidAttempt(visitorId, requesterIp);
                recordAudit(visitorId, "initial_request", action, requesterIp, userAgent, "invalid_hostid", null);
                return htmlBadRequest("Invalid approver.");
            }

            String storedHash = v.getApprovalKeyHash();
            if (storedHash == null || !passwordEncoder.matches(keyParam, storedHash)) {
                recordInvalidAttempt(visitorId, requesterIp);
                totalInvalidAttempts.incrementAndGet();
                recordAudit(visitorId, "initial_request", action, requesterIp, userAgent, "invalid_key", null);
                return htmlBadRequest("Invalid approval key.");
            }
        }

        // At this point: initial key validated for initial request or confirm stage.
        boolean isConfirmStage = (confirmParam != null && confirmParam);

        if (!isConfirmStage) {
            // INITIAL request stage: consume initial key atomically, generate confirmation key, send confirmation email to host
            totalInitialRequests.incrementAndGet();

            // Attempt to atomically consume initial key
            int consumed = 0;
            try {
                consumed = visitorRepository.consumeInitialKey(visitorId, Instant.now());
            } catch (Exception ex) {
                log.warn("Error consuming initial key for visitor {}: {}", visitorId, ex.getMessage());
            }
            if (consumed != 1) {
                // token was already consumed / expired / cleared by cleanup -> show expired
                recordAudit(visitorId, "initial_request", action, requesterIp, userAgent, "expired_or_consumed", null);
                return htmlBadRequest("This approval link has expired or was already used.");
            }

            // Generate confirmation key
            final String rawConfirmKey = generateRawKey();
            final String confirmHash = passwordEncoder.encode(rawConfirmKey);
            final Instant confirmExpiry = Instant.now().plus(Duration.ofMinutes(confirmationTtlMinutes));
            final Long approverId = v.getPersonWhomToMeet() != null ? v.getPersonWhomToMeet().getId() : null;

            // Atomically set confirmation key
            int setConfirm = 0;
            try {
                setConfirm = visitorRepository.setConfirmationKey(visitorId, confirmHash, confirmExpiry, approverId);
            } catch (Exception ex) {
                log.warn("Failed to set confirmation key for visitor {}: {}", visitorId, ex.getMessage());
            }
            if (setConfirm != 1) {
                // unlikely, but handle gracefully
                log.warn("Failed to set confirmation key for visitor {} after consuming initial key", visitorId);
                return htmlBadRequest("Unable to process request right now. Try again later.");
            }

            // Record audit (initial request success)
            recordAudit(visitorId, "initial_request", action, requesterIp, userAgent, "requested", null);

            // Build confirmation links and include expectedAction (the original action) so mismatched confirms can be detected
            String backendBase = approvalBaseUrl;
            final String keyParamEnc = URLEncoder.encode(rawConfirmKey, StandardCharsets.UTF_8);
            final String hostIdParamEnc = approverId != null ? URLEncoder.encode(String.valueOf(approverId), StandardCharsets.UTF_8) : "";
            final String visitorIdEnc = URLEncoder.encode(String.valueOf(visitorId), StandardCharsets.UTF_8);

            // NOTE: expectedAction is set to the original 'action' value
            final String approveConfirmUrl = backendBase + (backendBase.contains("?") ? "&" : "?")
                    + "visitorId=" + visitorIdEnc
                    + "&action=approve"
                    + "&expectedAction=" + URLEncoder.encode(action, StandardCharsets.UTF_8)
                    + "&key=" + keyParamEnc
                    + "&hostId=" + hostIdParamEnc
                    + "&confirm=true";
            final String rejectConfirmUrl = backendBase + (backendBase.contains("?") ? "&" : "?")
                    + "visitorId=" + visitorIdEnc
                    + "&action=reject"
                    + "&expectedAction=" + URLEncoder.encode(action, StandardCharsets.UTF_8)
                    + "&key=" + keyParamEnc
                    + "&hostId=" + hostIdParamEnc
                    + "&confirm=true";

            // Compose confirmation email including requester info (professional, clean HTML)
            try {
                if (v.getPersonWhomToMeet() != null) {
                    UserEntity host = userRepository.findById(v.getPersonWhomToMeet().getId()).orElse(null);
                    if (host != null && host.getEmail() != null && !host.getEmail().isBlank()) {
                        String subj = "Please confirm visitor action — " + v.getVisitorName();

                        StringBuilder plain = new StringBuilder();
                        plain.append("Hello ").append(host.getUserName() != null ? host.getUserName() : "Host").append(",\n\n");
                        plain.append("A request to ").append(action.toUpperCase()).append(" the visitor '").append(v.getVisitorName()).append("' has been made.\n\n");
                        plain.append("Requestor details:\n");
                        plain.append("  IP: ").append(requesterIp).append("\n");
                        plain.append("  User-Agent: ").append(userAgent).append("\n");
                        plain.append("  Time (UTC): ").append(Instant.now().toString()).append("\n\n");
                        plain.append("If this was you, confirm by clicking one of the links below (expires in ").append(confirmationTtlMinutes).append(" minutes):\n");
                        plain.append("Confirm Approve: ").append(approveConfirmUrl).append("\n");
                        plain.append("Confirm Reject:  ").append(rejectConfirmUrl).append("\n\n");
                        plain.append("If you did not initiate this request, do NOT click the links. Contact Security Desk immediately.\n");

                        StringBuilder html = new StringBuilder();
                        html.append("<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>");
                        html.append("<style>");
                        html.append("body{font-family: 'Segoe UI', Roboto, Arial, sans-serif; color:#202124; margin:0; padding:0;}");
                        html.append(".container{max-width:640px;margin:20px auto;padding:20px;border:1px solid #e5e7eb;border-radius:8px;background:#ffffff}");
                        html.append(".header{font-size:18px;font-weight:600;margin-bottom:12px;color:#111827}");
                        html.append(".muted{color:#6b7280;font-size:13px;margin-bottom:16px}");
                        html.append(".card{padding:14px;border-radius:6px;background:#f8fafc;border:1px solid #eef2f7;margin-bottom:16px}");
                        html.append(".key-actions{display:flex;gap:12px;align-items:center}");
                        html.append(".btn{display:inline-block;padding:10px 16px;border-radius:6px;text-decoration:none;font-weight:600}");
                        html.append(".btn-approve{background:#0f766e;color:#ffffff;border:1px solid #0f766e}");
                        html.append(".btn-reject{background:#ef4444;color:#ffffff;border:1px solid #ef4444}");
                        html.append(".footer{font-size:13px;color:#6b7280;margin-top:18px}");
                        html.append("</style></head><body>");
                        html.append("<div class='container'>");
                        html.append("<div class='header'>Please confirm visitor action</div>");
                        html.append("<div class='muted'>A request was made to <strong>").append(escapeHtml(action.toUpperCase())).append("</strong> the visitor <strong>").append(escapeHtml(v.getVisitorName())).append("</strong>.</div>");
                        html.append("<div class='card'>");
                        html.append("<div style='font-weight:600;margin-bottom:8px'>Requestor details</div>");
                        html.append("<div style='font-size:13px;color:#374151'>IP: ").append(escapeHtml(requesterIp)).append("</div>");
                        html.append("<div style='font-size:13px;color:#374151'>User-Agent: ").append(escapeHtml(userAgent)).append("</div>");
                        html.append("<div style='font-size:13px;color:#374151'>Time (UTC): ").append(escapeHtml(Instant.now().toString())).append("</div>");
                        html.append("</div>");
                        html.append("<div style='margin-bottom:12px'>Please confirm your choice (expires in ").append(confirmationTtlMinutes).append(" minutes):</div>");
                        html.append("<div class='key-actions'>");
                        html.append("<a class='btn btn-approve' href='").append(approveConfirmUrl).append("'>Confirm Approve</a>");
                        html.append("<a class='btn btn-reject' href='").append(rejectConfirmUrl).append("'>Confirm Reject</a>");
                        html.append("</div>");
                        html.append("<div class='footer'>If you did not initiate this request, do not click the links and contact Security Desk immediately.</div>");
                        html.append("</div></body></html>");

                        mailService.sendSimple(host.getEmail(), subj, html.toString(), plain.toString());
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to send confirmation email to host for visitor {}: {}", visitorId, ex.getMessage());
            }

            // initial page response (clean, professional)
            String info = "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>"
                    + "body{font-family: 'Segoe UI', Roboto, Arial, sans-serif;color:#202124;margin:0;padding:20px;background:#fafafa}"
                    + ".panel{max-width:760px;margin:32px auto;background:#fff;padding:24px;border-radius:8px;border:1px solid #e6edf3}"
                    + "h2{margin:0 0 12px 0;color:#0f172a;font-size:20px}"
                    + "p{color:#4b5563;line-height:1.6}"
                    + "</style></head><body>"
                    + "<div class='panel'>"
                    + "<h2>Confirmation requested</h2>"
                    + "<p>We've sent a confirmation email to the host. The action will be completed only after the host confirms from their mailbox.</p>"
                    + "<p>If you are the host, please check your inbox (and spam folder) and click the confirmation link. The confirmation link expires in "
                    + confirmationTtlMinutes + " minutes.</p>"
                    + "</div></body></html>";
            return ResponseEntity.ok(info);
        }

        // CONFIRM stage: if expectedActionParam is present, enforce it matches the requested confirm action
        if (expectedActionParam != null && !expectedActionParam.isBlank()) {
            if (!expectedActionParam.equalsIgnoreCase(action)) {
                // Mismatch: do not process state change; show inline modal/banner on same page (no redirect)
                recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "mismatched_action", "expected=" + expectedActionParam);
                return ResponseEntity.ok(invalidActionHtml(v, expectedActionParam, action));
            }
        }

        // CONFIRM stage: validate confirm key (already validated above for token flow), re-check stored hash to be safe
        if (!tokenValidated) {
            if (v.getApprovalKeyExpiry() == null || Instant.now().isAfter(v.getApprovalKeyExpiry())) {
                recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "expired", null);
                return htmlBadRequest("This confirmation link has expired.");
            }
            if (v.getApprovalForUserId() != null && hostIdParam != null && !v.getApprovalForUserId().equals(hostIdParam)) {
                recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "invalid_hostid", null);
                return htmlBadRequest("Invalid approver.");
            }
            String storedHash = v.getApprovalKeyHash();
            if (storedHash == null || !passwordEncoder.matches(keyParam, storedHash)) {
                recordInvalidAttempt(visitorId, requesterIp);
                recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "invalid_confirmation_key", null);
                return htmlBadRequest("Invalid confirmation key.");
            }
        }

        // Execute action using atomic repository methods (consume confirmation key + set status)
        try {
            if ("approve".equalsIgnoreCase(action)) {
                int rows = 0;
                try {
                    rows = visitorRepository.confirmApproveByVisitor(visitorId, Instant.now());
                } catch (Exception ex) {
                    log.warn("Error executing confirmApproveByVisitor for {}: {}", visitorId, ex.getMessage());
                }
                if (rows != 1) {
                    recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "expired_or_invalid", null);
                    return htmlBadRequest("This confirmation link has expired or is invalid.");
                }

                // reload visitor for side-effects and page content
                VisitorEntity updated = visitorRepository.findById(visitorId).orElse(v);

                PassEntity existing = passRepository.findByVisitor_Id(visitorId).orElse(null);
                boolean shouldGenerate = (existing == null) || (existing.getStatus() == null) || (existing.getStatus() == PassStatus.REVOKED);
                if (shouldGenerate) {
                    try {
                        passManagerService.generateAndPersist(visitorId, null, null);
                    } catch (Exception ex) {
                        log.warn("Pass generation/email failed for visitor {}: {}", visitorId, ex.getMessage());
                    }
                }

                totalConfirmations.incrementAndGet();
                recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "confirmed", null);

                // Notify host (final)
                try {
                    if (updated.getPersonWhomToMeet() != null) {
                        UserEntity host = userRepository.findById(updated.getPersonWhomToMeet().getId()).orElse(null);
                        if (host != null) {
                            String subj = "Visitor approved — confirmation";
                            String txt = String.format("Your visitor '%s' was APPROVED. If you did not initiate this action, contact Security Desk.", updated.getVisitorName());
                            mailService.sendSimple(host.getEmail(), subj, buildSimpleHtml(txt), txt);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Failed to send host confirmation email after approval for visitor {}: {}", visitorId, ex.getMessage());
                }

                return ResponseEntity.ok(approvedHtml(updated));

            } else if ("reject".equalsIgnoreCase(action)) {
                int rows = 0;
                try {
                    rows = visitorRepository.confirmRejectByVisitor(visitorId, Instant.now());
                } catch (Exception ex) {
                    log.warn("Error executing confirmRejectByVisitor for {}: {}", visitorId, ex.getMessage());
                }
                if (rows != 1) {
                    recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "expired_or_invalid", null);
                    return htmlBadRequest("This confirmation link has expired or is invalid.");
                }

                // reload visitor for side-effects and page content
                VisitorEntity updated = visitorRepository.findById(visitorId).orElse(v);

                PassEntity pass = passRepository.findByVisitor_Id(visitorId).orElse(null);
                if (pass != null) {
                    pass.setStatus(PassStatus.REVOKED);
                    passRepository.save(pass);
                }

                totalConfirmations.incrementAndGet();
                recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "confirmed", null);

                try {
                    if (updated.getPersonWhomToMeet() != null) {
                        UserEntity host = userRepository.findById(updated.getPersonWhomToMeet().getId()).orElse(null);
                        if (host != null) {
                            String subj = "Visitor rejected — confirmation";
                            String txt = String.format("Your visitor '%s' was REJECTED. If you did not initiate this action, contact Security Desk.", updated.getVisitorName());
                            mailService.sendSimple(host.getEmail(), subj, buildSimpleHtml(txt), txt);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Failed to send host confirmation email after rejection for visitor {}: {}", visitorId, ex.getMessage());
                }

                return ResponseEntity.ok(rejectedHtml(updated));
            } else {
                return htmlBadRequest("Unknown action: " + escapeHtml(action));
            }
        } catch (Exception ex) {
            log.error("Error processing approval action for visitor {}: {}", visitorId, ex.getMessage(), ex);
            recordAudit(visitorId, "confirmation", action, requesterIp, userAgent, "failed", ex.getMessage());
            String body = "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>body{font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#202124;margin:0;padding:20px;background:#fafafa}.panel{max-width:760px;margin:32px auto;background:#fff;padding:24px;border-radius:8px;border:1px solid #e6edf3}h2{margin:0 0 12px 0;color:#0f172a;font-size:20px}p{color:#4b5563;line-height:1.6}</style>"
                    + "</head><body>"
                    + "<div class='panel'><h2>Action processed (with warnings)</h2>"
                    + "<p>The visit status was updated, but there was an error during follow-up processing: " + escapeHtml(ex.getMessage()) + "</p>"
                    + "<p>Please contact Security Desk if the issue persists.</p>"
                    + "</div></body></html>";
            return ResponseEntity.ok(body);
        }
    }

    /* ----------------- rate limiting & lockout helpers ----------------- */

    private boolean isIpRateLimited(String ip) {
        if (ip == null) return false;
        IpWindow w = ipWindows.computeIfAbsent(ip, k -> new IpWindow());
        synchronized (w) {
            Instant now = Instant.now();
            if (now.isAfter(w.windowStart.plus(Duration.ofHours(1)))) {
                w.windowStart = now;
                w.count.set(0);
            }
            int curr = w.count.incrementAndGet();
            return curr > maxAttemptsPerIpPerHour;
        }
    }

    private void recordInvalidAttempt(Long visitorId, String ip) {
        totalInvalidAttempts.incrementAndGet();

        if (visitorId != null) {
            VisitorInvalidWindow win = visitorInvalidWindows.computeIfAbsent(visitorId, k -> new VisitorInvalidWindow());
            synchronized (win) {
                Instant now = Instant.now();
                if (win.windowStart == null || now.isAfter(win.windowStart.plus(Duration.ofMinutes(invalidWindowMinutes)))) {
                    win.windowStart = now;
                    win.count.set(0);
                }
                int c = win.count.incrementAndGet();
                if (c > maxInvalidAttemptsPerVisitor) {
                    visitorLockoutUntil.put(visitorId, now.plus(Duration.ofMinutes(invalidWindowMinutes)));
                    log.warn("Visitor {} locked out until {}", visitorId, visitorLockoutUntil.get(visitorId));
                }
            }
        }
    }

    private boolean isVisitorLockedOut(Long visitorId) {
        Instant until = visitorLockoutUntil.get(visitorId);
        if (until == null) return false;
        if (Instant.now().isAfter(until)) {
            visitorLockoutUntil.remove(visitorId);
            return false;
        }
        return true;
    }

    private static class IpWindow {
        Instant windowStart = Instant.now();
        AtomicInteger count = new AtomicInteger(0);
    }

    private static class VisitorInvalidWindow {
        Instant windowStart;
        AtomicInteger count = new AtomicInteger(0);
    }

    /* ----------------- audit helpers ----------------- */

    private void recordAudit(Long visitorId, String stage, String action, String ip, String ua, String outcome, String note) {
        try {
            VisitorApprovalAudit a = VisitorApprovalAudit.builder()
                    .visitorId(visitorId != null ? visitorId : -1L)
                    .stage(stage)
                    .action(action != null ? action : "unknown")
                    .requesterIp(ip)
                    .userAgent(ua)
                    .outcome(outcome)
                    .note(note)
                    .createdAt(Instant.now())
                    .build();
            auditRepository.save(a);
        } catch (Exception ex) {
            log.warn("Failed to write approval audit: {}", ex.getMessage());
        }
    }

    /* ----------------- helpers (friendly/mobile-ready pages) ----------------- */

    private ResponseEntity<String> htmlBadRequest(String msg) {
        String body = "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#202124;margin:0;padding:20px;background:#fafafa}"
                + ".panel{max-width:760px;margin:32px auto;background:#fff;padding:24px;border-radius:8px;border:1px solid #f3d4d4}"
                + "h2{margin:0 0 12px 0;color:#0f172a;font-size:20px}"
                + "p{color:#4b5563;line-height:1.6}"
                + "</style></head><body>"
                + "<div class='panel'>"
                + "<h2>Bad Request</h2>"
                + "<p>" + escapeHtml(msg) + "</p>"
                + "</div></body></html>";
        return ResponseEntity.badRequest().body(body);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String approvedHtml(VisitorEntity v) {
        String body = "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#202124;margin:0;padding:20px;background:#f7f9fb}"
                + ".panel{max-width:760px;margin:32px auto;background:#fff;padding:28px;border-radius:10px;border:1px solid #e6eef6}"
                + "h2{margin:0 0 12px 0;color:#0f172a;font-size:22px}"
                + "p{color:#374151;line-height:1.6}"
                + ".muted{color:#6b7280;font-size:13px;margin-top:10px}"
                + "</style></head><body>"
                + "<div class='panel'>"
                + "<h2>Visit Approved</h2>"
                + "<p>The visit for <strong>" + escapeHtml(v.getVisitorName()) + "</strong> has been approved.</p>"
                + "<p class='muted'>A visitor pass has been generated and emailed to the visitor (if a valid email address was provided).</p>"
                + "</div></body></html>";
        return body;
    }

    private String rejectedHtml(VisitorEntity v) {
        String body = "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#202124;margin:0;padding:20px;background:#f7f9fb}"
                + ".panel{max-width:760px;margin:32px auto;background:#fff;padding:28px;border-radius:10px;border:1px solid #fdecea}"
                + "h2{margin:0 0 12px 0;color:#0f172a;font-size:22px}"
                + "p{color:#374151;line-height:1.6}"
                + ".muted{color:#6b7280;font-size:13px;margin-top:10px}"
                + "</style></head><body>"
                + "<div class='panel'>"
                + "<h2>Visit Rejected</h2>"
                + "<p>The visit for <strong>" + escapeHtml(v.getVisitorName()) + "</strong> has been rejected.</p>"
                + "<p class='muted'>If you need to inform the visitor, use the application or contact Security Desk.</p>"
                + "</div></body></html>";
        return body;
    }

    private String alreadyProcessedHtml(VisitorEntity v) {
        String already = v.getVisitStatus() != null ? v.getVisitStatus().name() : "processed";
        String body = "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#202124;margin:0;padding:20px;background:#f7f9fb}"
                + ".panel{max-width:760px;margin:32px auto;background:#fff;padding:28px;border-radius:10px;border:1px solid #e6eef6}"
                + "h2{margin:0 0 12px 0;color:#0f172a;font-size:22px}"
                + "p{color:#374151;line-height:1.6}"
                + ".muted{color:#6b7280;font-size:13px;margin-top:10px}"
                + "</style></head><body>"
                + "<div class='panel'>"
                + "<h2>Already processed</h2>"
                + "<p>This visitor invitation was already <strong>" + escapeHtml(already) + "</strong>.</p>"
                + "<p>Visitor: <strong>" + escapeHtml(v.getVisitorName()) + "</strong></p>"
                + "<p class='muted'>If you believe this is an error, please contact Security Desk.</p>"
                + "</div></body></html>";
        return body;
    }

    /**
     * Render a clean page which shows an inline non-redirecting modal/banner indicating the confirm action
     * does not match the original requested action. This preserves functionality (no DB change).
     */
    private String invalidActionHtml(VisitorEntity v, String expectedAction, String actualAction) {
        String body = "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Roboto,Arial,sans-serif;color:#202124;margin:0;padding:20px;background:#f7f9fb}"
                + ".panel{max-width:820px;margin:28px auto;background:#fff;padding:20px;border-radius:10px;border:1px solid #e6eef6;position:relative}"
                + ".title{font-size:20px;font-weight:700;color:#0f172a;margin-bottom:8px}"
                + ".desc{color:#374151;line-height:1.5;margin-bottom:16px}"
                + ".badge{display:inline-block;padding:8px 12px;border-radius:6px;background:#fff;border:1px solid #e5e7eb;color:#374151;font-weight:600;margin-right:8px}"
                + ".modal{position:relative;background:#fff;border-left:4px solid #f59e0b;padding:14px;margin-bottom:14px;border-radius:6px}"
                + ".modal strong{color:#b45309}"
                + ".actions{margin-top:12px}"
                + ".btn{display:inline-block;padding:10px 14px;border-radius:6px;text-decoration:none;font-weight:600}"
                + ".btn-close{background:#e5e7eb;color:#111827;border:1px solid #d1d5db}"
                + ".meta{font-size:13px;color:#6b7280;margin-top:12px}"
                + "</style>"
                + "<script>"
                + "function closeNotice(){var el=document.getElementById('notice'); el.style.display='none';}"
                + "</script>"
                + "</head><body>"
                + "<div class='panel'>"
                + "<div class='title'>Confirmation mismatch — action not allowed</div>"
                + "<div class='desc'>The confirmation you attempted does not match the originally requested action for this visitor.</div>"
                + "<div id='notice' class='modal'><div><strong>Invalid confirmation</strong></div>"
                + "<div style='margin-top:8px;color:#374151'>Expected: <span class='badge'>" + escapeHtml(expectedAction.toUpperCase()) + "</span>"
                + "You attempted: <span class='badge'>" + escapeHtml(actualAction.toUpperCase()) + "</span></div>"
                + "<div style='margin-top:8px;color:#374151'>No action was performed. If you intended to change the request, ask the requester to re-initiate with the desired action.</div>"
                + "<div class='actions'><a class='btn btn-close' href='javascript:void(0)' onclick='closeNotice()'>Dismiss</a></div>"
                + "</div>"
                + "<div class='meta'>Visitor: <strong>" + escapeHtml(v.getVisitorName()) + "</strong> • Request time: <span>" + escapeHtml(Instant.now().toString()) + "</span></div>"
                + "</div></body></html>";
        return body;
    }

    private String buildSimpleHtml(String shortText) {
        return "<!doctype html><html><head><meta charset='utf-8'/><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:'Segoe UI',Roboto,Arial,sans-serif;padding:16px;color:#202124}</style></head><body>"
                + "<div style='max-width:640px;margin:0 auto;padding:12px 0;'>" +
                "<p style='color:#374151;margin:0'>" + escapeHtml(shortText) + "</p></div></body></html>";
    }

    // helper to generate raw confirmation key (same entropy as in service)
    private String generateRawKey() {
        byte[] b = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // extract client IP with common headers fallback
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            if (parts.length > 0) return parts[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp;
        return request.getRemoteAddr();
    }
}
