package com.rkt.VisitorManagementSystem.controller.dev;

import com.rkt.VisitorManagementSystem.entity.PassEntity;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.repository.PassRepository;
import com.rkt.VisitorManagementSystem.service.security.QrSigner; // <- your QrSigner
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Profile("dev") // only active in 'dev' profile
@RestController
@RequiredArgsConstructor
@RequestMapping("/rkt/dev/passes")
public class PassTokenDevController {

    private final PassRepository passRepository;

    @Value("${app.pass.scan-base-url}")
    private String scanBaseUrl;

    @Value("${app.pass.qr-secret}")
    private String qrSecret;

    @Value("${app.pass.qr-ttl-seconds:7200}")
    private long qrTtlSeconds;

    /** Get token + scanUrl by passId */
    @GetMapping("/{passId}/token")
    public ResponseEntity<Map<String, String>> byPassId(@PathVariable Long passId) {
        PassEntity pass = passRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("Pass not found: " + passId));

        String token = QrSigner.sign(
                Map.of(
                        "passId", String.valueOf(pass.getId()),
                        "visitorId", String.valueOf(pass.getVisitor().getId()),
                        "nonce", pass.getQrNonce()
                ),
                qrSecret,
                qrTtlSeconds
        );

        String url = scanBaseUrl + "?token=" + token;
        return ResponseEntity.ok(Map.of(
                "passId", String.valueOf(pass.getId()),
                "visitorId", String.valueOf(pass.getVisitor().getId()),
                "token", token,
                "scanUrl", url
        ));
    }

    /** Get token + scanUrl by visitorId */
    @GetMapping("/by-visitor/{visitorId}/token")
    public ResponseEntity<Map<String, String>> byVisitorId(@PathVariable Long visitorId) {
        PassEntity pass = passRepository.findByVisitor_Id(visitorId)
                .orElseThrow(() -> new ResourceNotFoundException("Pass not found for visitor: " + visitorId));

        String token = QrSigner.sign(
                Map.of(
                        "passId", String.valueOf(pass.getId()),
                        "visitorId", String.valueOf(visitorId),
                        "nonce", pass.getQrNonce()
                ),
                qrSecret,
                qrTtlSeconds
        );

        String url = scanBaseUrl + "?token=" + token;
        return ResponseEntity.ok(Map.of(
                "passId", String.valueOf(pass.getId()),
                "visitorId", String.valueOf(visitorId),
                "token", token,
                "scanUrl", url
        ));
    }
}
