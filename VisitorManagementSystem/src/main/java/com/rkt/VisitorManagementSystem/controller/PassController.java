package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.service.PassManagerService;
import com.rkt.VisitorManagementSystem.service.PassService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rkt/passes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PassController {

    private final PassService passService;               // preview-only
    private final PassManagerService passManagerService; // persists + returns PDF (and will handle emailing)

    /** Preview (no DB write). */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/visitor/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable("id") Long id,
                                          @RequestParam(value = "minutes", required = false) Integer minutes,
                                          @RequestParam(value = "gateNo", required = false) String gateNo) {
        byte[] pdf = passService.generateVisitorPassPdf(id, minutes, gateNo);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=visitor-pass-" + id + ".pdf")
                .body(pdf);
    }

    /** Generate & persist a pass, then return the PDF. Email is handled inside the service. */
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping("/visitor/{id}/generate")
    public ResponseEntity<byte[]> generate(@PathVariable("id") Long visitorId,
                                           @RequestParam(value = "minutes", required = false) Integer minutes,
                                           @RequestParam(value = "gateNo", required = false) String gateNo) {
        byte[] pdf = passManagerService.generateAndPersist(visitorId, minutes, gateNo);
        String fileName = "visitor-pass-" + visitorId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + fileName)
                .body(pdf);
    }
}
