package com.rkt.VisitorManagementSystem.serviceImpl;

import com.rkt.VisitorManagementSystem.dto.PassScanResultDto;
import com.rkt.VisitorManagementSystem.entity.PassEntity;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import com.rkt.VisitorManagementSystem.entity.enums.PassStatus;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.repository.PassRepository;
import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import com.rkt.VisitorManagementSystem.service.PassScanService;
import com.rkt.VisitorManagementSystem.service.security.QrSigner;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PassScanServiceImpl implements PassScanService {

    private final PassRepository passRepository;
    private final VisitorRepository visitorRepository; // kept in case you need extra validations

    @Value("${app.pass.qr-secret}")
    private String qrSecret;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter D  = DateTimeFormatter.ofPattern("dd-MM-yyyy");

//    @Override
//    public PassScanResultDto handleScanToken(String token) {
//        // Your QrSigner returns Map<String, String>
//        Map<String, String> claims = QrSigner.verifyAndParse(token, qrSecret);
//
//        Long passId    = Long.valueOf(claims.get("passId"));
//        Long visitorId = Long.valueOf(claims.get("visitorId"));
//        String nonce   = claims.get("nonce");
//
//        PassEntity pass = passRepository.findById(passId)
//                .orElseThrow(() -> new ResourceNotFoundException("Pass not found: " + passId));
//
//        // Sanity checks against pass
//        if (!Objects.equals(pass.getVisitor().getId(), visitorId)) {
//            throw new IllegalArgumentException("Token/Pass visitor mismatch");
//        }
//        if (!Objects.equals(pass.getQrNonce(), nonce)) {
//            throw new IllegalArgumentException("Token nonce mismatch");
//        }
//        if (pass.getStatus() == PassStatus.REVOKED) {
//            return build(pass, "Pass revoked");
//        }
//        if (pass.getStatus() == PassStatus.EXPIRED) {
//            return build(pass, "Pass expired");
//        }
//
//        Instant now = Instant.now();
//
//        // First scan => check-in (if within deadline), else expire
//        if (pass.getCheckinAt() == null) {
//            Instant deadline = pass.getCheckinDeadline();
//            if (deadline != null && now.isAfter(deadline)) {
//                pass.setStatus(PassStatus.EXPIRED);
//                passRepository.save(pass);
//                return build(pass, "Missed check-in deadline");
//            }
//
//            // Attempt to atomically transition visitor from APPROVED -> CHECKED_IN
//            Long vid = pass.getVisitor() != null ? pass.getVisitor().getId() : null;
//            if (vid == null) {
//                return build(pass, "Invalid pass (no visitor attached)");
//            }
//
//            int updated = 0;
//            try {
//                updated = visitorRepository.markCheckedIn(vid);
//            } catch (Exception ex) {
//                // log if you have logging here; keep behavior minimal
//                updated = 0;
//            }
//
//            if (updated != 1) {
//                // not allowed (maybe not approved, or already checked in)
//                return build(pass, "Cannot check in: visitor not in APPROVED state");
//            }
//
//            // mark pass record as checked-in
//            pass.setCheckinAt(now);
//            passRepository.save(pass);
//            return build(pass, "Checked in");
//        }
//
//        // Second scan => check-out
//        if (pass.getCheckoutAt() == null) {
//            Long vid = pass.getVisitor() != null ? pass.getVisitor().getId() : null;
//            if (vid == null) {
//                return build(pass, "Invalid pass (no visitor attached)");
//            }
//
//            int updatedOut = 0;
//            try {
//                updatedOut = visitorRepository.markCheckedOut(vid);
//            } catch (Exception ex) {
//                updatedOut = 0;
//            }
//
//            if (updatedOut != 1) {
//                return build(pass, "Cannot check out: visitor not in CHECKED_IN state");
//            }
//
//            pass.setCheckoutAt(now);
//            passRepository.save(pass);
//            return build(pass, "Checked out");
//        }
//
//        // Subsequent scans
//        return build(pass, "Already checked out");
//    }

    @Override
    public PassScanResultDto handleScanToken(String token) {

        Map<String, String> claims = QrSigner.verifyAndParse(token, qrSecret);

        Long passId = Long.valueOf(claims.get("passId"));
        String nonce = claims.get("nonce");

        PassEntity pass = passRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("Pass not found: " + passId));

        if (!pass.getQrNonce().equals(nonce)) {
            throw new IllegalArgumentException("Invalid QR token");
        }

        if (pass.getStatus() == PassStatus.REVOKED) {
            return build(pass, "Pass revoked");
        }

        if (pass.getStatus() == PassStatus.EXPIRED) {
            return build(pass, "Pass expired");
        }

        Instant now = Instant.now();

        // FIRST SCAN -> CHECK-IN
        if (pass.getCheckinAt() == null) {

            if (pass.getCheckinDeadline() != null && now.isAfter(pass.getCheckinDeadline())) {
                pass.setStatus(PassStatus.EXPIRED);
                passRepository.save(pass);
                return build(pass, "Missed check-in deadline");
            }

            pass.setCheckinAt(now);
            pass.getVisitor().setVisitStatus(VisitStatus.CHECKED_IN);

            passRepository.save(pass);
            return build(pass, "Checked in");
        }

        // SECOND SCAN -> CHECK-OUT
        if (pass.getCheckoutAt() == null) {

            pass.setCheckoutAt(now);
            pass.getVisitor().setVisitStatus(VisitStatus.CHECKED_OUT);

            passRepository.save(pass);
            return build(pass, "Checked out");
        }

        // THIRD SCAN
        return build(pass, "Already checked out");
    }

    public PassScanResultDto handleScanByPassId(Long passId) {

        PassEntity pass = passRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("Pass not found"));

        Instant now = Instant.now();

        if (pass.getStatus() == PassStatus.REVOKED)
            return build(pass, "Pass revoked");

        if (pass.getStatus() == PassStatus.EXPIRED)
            return build(pass, "Pass expired");

        // First scan
        if (pass.getCheckinAt() == null) {

            if (pass.getCheckinDeadline() != null && now.isAfter(pass.getCheckinDeadline())) {
                pass.setStatus(PassStatus.EXPIRED);
                passRepository.save(pass);
                return build(pass, "Missed check-in deadline");
            }

            pass.setCheckinAt(now);
            pass.getVisitor().setVisitStatus(VisitStatus.CHECKED_IN);

            passRepository.save(pass);
            return build(pass, "Checked in");
        }

        // Second scan
        if (pass.getCheckoutAt() == null) {

            pass.setCheckoutAt(now);
            pass.getVisitor().setVisitStatus(VisitStatus.CHECKED_OUT);

            passRepository.save(pass);
            return build(pass, "Checked out");
        }

        return build(pass, "Already checked out");
    }



    private PassScanResultDto build(PassEntity pass, String msg) {
        VisitorEntity v = pass.getVisitor();

        return PassScanResultDto.builder()
                .passId(pass.getId())
                .visitorId(v.getId())

                .visitorName(v.getVisitorName())
                .visitPurpose(String.valueOf(v.getVisitPurpose()))
                .company(v.getCompany())
                .hostName(v.getPersonWhomToMeet() != null ? v.getPersonWhomToMeet().getUserName() : "-")
                .department(v.getDepartment() != null ? v.getDepartment().getName() : "-")
                .dateOfVisit(v.getDateOfVisiting() != null ? v.getDateOfVisiting().format(D) : "-")

                .gateNo(pass.getGateNo())
                .issuedAt(fmtOrDash(pass.getIssuedAt()))
                .checkinDeadline(fmtOrDash(pass.getCheckinDeadline()))

                .checkinAt(fmtOrNull(pass.getCheckinAt()))
                .checkoutAt(fmtOrNull(pass.getCheckoutAt()))

                .status(pass.getStatus() != null ? pass.getStatus().name() : null)
                .message(msg)
                .build();
    }

    private static String fmtOrDash(Instant instant) {
        return instant == null ? "-" : DT.format(instant.atZone(ZONE));
    }

    private static String fmtOrNull(Instant instant) {
        return instant == null ? null : DT.format(instant.atZone(ZONE));
    }
}
