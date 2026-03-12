package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.PassScanResultDto;
import com.rkt.VisitorManagementSystem.service.PassScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rkt/public/passes")
public class PublicPassScanController {

    private final PassScanService passScanService;

    @GetMapping("/scan")
    public ResponseEntity<PassScanResultDto> scan(@RequestParam("token") String token) {
        PassScanResultDto dto = passScanService.handleScanToken(token);
        return ResponseEntity.ok(dto);
    }

    @GetMapping(value = "/scan/view", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> scanView(@RequestParam("passId") Long passId) {

        PassScanResultDto dto = passScanService.handleScanByPassId(passId);

        return ResponseEntity.ok(buildMobileHtml(dto));
    }

//    @GetMapping(value = "/scan/view", produces = MediaType.TEXT_HTML_VALUE)
//    public ResponseEntity<String> scanView(@RequestParam("token") String token) {
//
//        PassScanResultDto dto = passScanService.handleScanToken(token);
//
//        return ResponseEntity.ok(buildMobileHtml(dto));
//    }


    private String buildMobileHtml(PassScanResultDto dto) {

        String statusColor = "green";
        String msg = dto.getMessage().toLowerCase();

        if (msg.contains("expired") || msg.contains("revoked")) statusColor = "red";
        if (msg.contains("already")) statusColor = "orange";

        return """
        <!doctype html>
        <html>
        <head>
            <meta charset='utf-8'/>
            <meta name='viewport' content='width=device-width, initial-scale=1'/>
            <title>Visitor Scan Result</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    margin: 0;
                    background: #f5f7fa;
                    padding: 20px;
                }
                .card {
                    max-width: 500px;
                    margin: auto;
                    background: white;
                    padding: 20px;
                    border-radius: 12px;
                    box-shadow: 0 4px 15px rgba(0,0,0,0.08);
                }
                .status {
                    font-size: 20px;
                    font-weight: 600;
                    margin-bottom: 15px;
                    color: %s;
                }
                .row {
                    margin-bottom: 8px;
                    font-size: 15px;
                }
                .label {
                    font-weight: 600;
                    color: #555;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="status">✔ %s</div>

                <div class="row"><span class="label">Visitor:</span> %s</div>
                <div class="row"><span class="label">Company:</span> %s</div>
                <div class="row"><span class="label">Purpose:</span> %s</div>
                <div class="row"><span class="label">Department:</span> %s</div>
                <div class="row"><span class="label">Host:</span> %s</div>
                <div class="row"><span class="label">Gate:</span> %s</div>
                <div class="row"><span class="label">Visit Date:</span> %s</div>
                <div class="row"><span class="label">Check-In:</span> %s</div>
                <div class="row"><span class="label">Check-Out:</span> %s</div>
            </div>
        </body>
        </html>
        """.formatted(
                statusColor,
                dto.getMessage(),
                dto.getVisitorName(),
                safe(dto.getCompany()),
                dto.getVisitPurpose(),
                dto.getDepartment(),
                dto.getHostName(),
                dto.getGateNo(),
                dto.getDateOfVisit(),
                safe(dto.getCheckinAt()),
                safe(dto.getCheckoutAt())
        );
    }

    private String safe(String v) {
        return v == null ? "-" : v;
    }
}
