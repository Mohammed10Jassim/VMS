package com.rkt.VisitorManagementSystem.serviceImpl;

import com.rkt.VisitorManagementSystem.entity.PassEntity;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import com.rkt.VisitorManagementSystem.entity.enums.PassStatus;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.repository.PassRepository;
import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import com.rkt.VisitorManagementSystem.service.MailService;
import com.rkt.VisitorManagementSystem.service.PassManagerService;
import com.rkt.VisitorManagementSystem.service.PassService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PassManagerServiceImpl implements PassManagerService {

    private final VisitorRepository visitorRepository;
    private final PassRepository passRepository;
    private final PassService passService;   // PDF generator
    private final MailService mailService;   // <-- new (emails the PDF)

    @Value("${app.pass.default-validity-minutes:120}")
    private int defaultValidityMinutes;

    @Value("${app.pass.gate-no:GATE-1}")
    private String defaultGateNo;

    @Value("${app.office.timezone:Asia/Kolkata}")
    private String officeZoneId;

    @Value("${app.pass.web-same-day-only:true}")
    private boolean webSameDayOnly;

    @Value("${app.pass.web-same-day-mode:reject}") // "reject" or "override"
    private String webSameDayMode;

    // Email templates (with fallbacks)
    @Value("${app.mail.subject-template:[Visitor Pass] %s}")
    private String subjectTemplate;

    @Value("${app.mail.body-template:Hi %s,\n\nYour visitor pass is attached.\nShow the QR at the gate.\n\nRegards,\nSecurity Desk}")
    private String bodyTemplate;

    @Override
    public byte[] generateAndPersist(Long visitorId, Integer validityMinutes, String gateNo) {
        VisitorEntity visitor = visitorRepository.findById(visitorId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + visitorId));

        // --- SAME-DAY-ONLY GUARD (WEB) ---
        if (webSameDayOnly) {
            ZoneId officeZone = ZoneId.of(officeZoneId);
            LocalDate today = LocalDate.now(officeZone);
            LocalDate requested = visitor.getDateOfVisiting();
            if (requested == null || !requested.isEqual(today)) {
                if ("override".equalsIgnoreCase(webSameDayMode)) {
                    // quietly normalize to today (and keep going)
                    visitor.setDateOfVisiting(today);
                    visitorRepository.save(visitor);
                } else {
                    // default: reject clearly
                    throw new IllegalArgumentException(
                            "Web passes are same-day only. Set Date of Visit to " + today + " (" + officeZone + ")."
                    );
                }
            }
        }
        // --- END GUARD ---

        int mins = (validityMinutes != null && validityMinutes > 0) ? validityMinutes : defaultValidityMinutes;
        String gate = (gateNo != null && !gateNo.isBlank()) ? gateNo : defaultGateNo;

        // Store timestamps in UTC as Instant
        Instant now = Instant.now();
        Instant deadline = now.plus(Duration.ofMinutes(mins));

        PassEntity pass = passRepository.findByVisitor_Id(visitorId).orElse(
                PassEntity.builder().visitor(visitor).build()
        );

        pass.setStatus(PassStatus.ISSUED);
        pass.setIssuedAt(now);
        pass.setCheckinDeadline(deadline);
        pass.setGateNo(gate);

        if (pass.getQrNonce() == null || pass.getQrNonce().isBlank()) {
            pass.setQrNonce(UUID.randomUUID().toString().replace("-", ""));
        }

        pass = passRepository.save(pass);


        byte[] pdf = passService.generateVisitorPassPdf(visitorId, mins, gate);

        // Email it (fail-soft; MailService itself can be feature-flagged via app.mail.enabled)
        try {
            String to = visitor.getEmail();
            if (to != null && !to.isBlank()) {
                String subject = String.format(subjectTemplate, visitor.getVisitorName());
                String body = String.format(bodyTemplate, visitor.getVisitorName());
                String fileName = "VisitorPass_" + pass.getId() + ".pdf";
                mailService.sendPassPdf(to, subject, body, pdf, fileName);
            }
        } catch (Exception e) {
            System.err.println("Non-fatal: could not send pass email: " + e.getMessage());
        }

        return pdf;
    }
}
