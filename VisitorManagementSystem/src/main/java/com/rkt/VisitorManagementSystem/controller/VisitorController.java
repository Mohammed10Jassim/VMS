package com.rkt.VisitorManagementSystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rkt.VisitorManagementSystem.dto.VisitorDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.PageResponse;
import com.rkt.VisitorManagementSystem.dto.responseDto.VisitorResponseDto;
import com.rkt.VisitorManagementSystem.entity.PassEntity;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import com.rkt.VisitorManagementSystem.entity.enums.PassStatus;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.mapper.VisitorMapper;
import com.rkt.VisitorManagementSystem.repository.PassRepository;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import com.rkt.VisitorManagementSystem.service.PassManagerService;
import com.rkt.VisitorManagementSystem.service.VisitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;


@RestController
@RequiredArgsConstructor
@RequestMapping("/rkt/visitors")
@CrossOrigin(origins = "*")
@Tag(name = "Visitors", description = "Operations for visitor lifecycle, file upload, approval, and pass management")
public class VisitorController {

    private final VisitorService visitorService;
    private final VisitorRepository visitorRepository;
    private final UserRepository userRepository;
    private final PassManagerService passManagerService;
    private final PassRepository passRepository;
    private final VisitorMapper visitorMapper;
    private final ObjectMapper objectMapper;
    private final Validator validator;


    @Value("${app.pass.web-same-day-only:true}")
    private boolean webSameDayOnly;

    @Value("${app.pass.web-same-day-mode:reject}")
    private String webSameDayMode;

    @Value("${app.office.timezone:Asia/Kolkata}")
    private String officeZoneId;

    @Operation(summary = "Search visitors", description = "Search visitors with pagination, sorting and optional filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of visitors returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error / invalid parameters")
    })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/search")
    public ResponseEntity<PageResponse<VisitorResponseDto>> searchVisitors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,

            @RequestParam(required = false) String q,
            @RequestParam(required = false) String visitPurpose,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long hostUserId,
            @RequestParam(required = false) String visitStatus,

            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate
    ) {

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        LocalDate from = null;
        LocalDate to = null;

        if (fromDate != null && !fromDate.isBlank())
            from = LocalDate.parse(fromDate, fmt);

        if (toDate != null && !toDate.isBlank())
            to = LocalDate.parse(toDate, fmt);

        Pageable pageable = PageRequest.of(page, size);

        Page<VisitorResponseDto> result =
                visitorService.searchVisitorsNew(
                        pageable,
                        q,
                        visitPurpose,
                        departmentId,
                        hostUserId,
                        visitStatus,
                        from,
                        to
                );

        PageResponse<VisitorResponseDto> resp =
                new PageResponse<>(
                        result.getContent(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        result.getNumber(),
                        result.getSize(),
                        result.isLast(),
                        result.isFirst()
                );

        return ResponseEntity.ok(resp);
    }



    @Operation(summary = "Get visitor by id", description = "Retrieve visitor details. Set includeBlobs=true to include image/id binary blobs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visitor returned", content = @Content(mediaType = "application/json", schema = @Schema(implementation = VisitorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Visitor not found")
    })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<VisitorResponseDto> get(
            @Parameter(description = "Visitor id") @PathVariable Long id,
            @Parameter(description = "Include binary blobs (warning: large responses)") @RequestParam(value = "includeBlobs", required = false, defaultValue = "false") boolean includeBlobs) {

        if (!includeBlobs) {
            return ResponseEntity.ok(visitorService.get(id)); // existing DTO response path
        } else {
            VisitorEntity e = visitorService.findEntityById(id);// new service method
            VisitorResponseDto dto = visitorMapper.toResponseWithBlobs(e);
            return ResponseEntity.ok(dto);

        }
    }


    @Operation(summary = "Create visitor (multipart)", description = "Create a visitor using multipart form-data (useful for file uploads). Include JSON 'data' part for visitor fields.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visitor created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = VisitorResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Multipart form: 'data' part is the Visitor JSON, optional 'image' and 'idProof' file parts",
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = VisitorDto.class)
            )
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisitorResponseDto> createMultipart(
            @RequestPart("data") String data,
            @RequestPart(name = "image", required = false) MultipartFile image,
            @RequestPart(name = "idProof", required = false) MultipartFile idProof
    ) throws Exception {

        VisitorDto dto = objectMapper.readValue(data, VisitorDto.class);

        Set<ConstraintViolation<VisitorDto>> violations =
                validator.validate(dto, VisitorDto.Create.class);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        validateOrNormalizeDateForWeb(dto);
        return ResponseEntity.ok(visitorService.create(dto, image, idProof));
    }



    @Operation(summary = "Patch visitor", description = "Patch/update some fields of a visitor. Note: date mutation is restricted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visitor updated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = VisitorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Visitor not found")
    })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Visitor patch payload",
            required = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = VisitorDto.class))
    )
    @PatchMapping("/{id}")
    public ResponseEntity<VisitorResponseDto> update(
            @Parameter(description = "Visitor id") @PathVariable Long id,
            @Validated(VisitorDto.Update.class) @RequestBody VisitorDto dto) {
        return ResponseEntity.ok(visitorService.update(id, dto));
    }


    @Operation(summary = "Delete visitor", description = "Delete visitor record")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Visitor id") @PathVariable Long id) {
        visitorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Find visitors by date", description = "Return visitors for a specific date")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "List returned") })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/by-date")
    public ResponseEntity<List<VisitorResponseDto>> byDate(
            @Parameter(description = "Date (dd-MM-yyyy)") @RequestParam("date") @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate date) {
        return ResponseEntity.ok(visitorService.findByVisitDate(date));
    }

    @Operation(summary = "Find by host", description = "Visitors for a specific host")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "List returned") })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/by-host/{hostUserId}")
    public ResponseEntity<List<VisitorResponseDto>> byHost(@Parameter(description = "Host user id") @PathVariable Long hostUserId) {
        return ResponseEntity.ok(visitorService.findByHost(hostUserId));
    }

    // ---------- FILES: Visitor Photo ----------
    @Operation(summary = "Upload visitor image", description = "Upload a visitor photo (multipart file). Returns updated visitor DTO")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Image uploaded") })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisitorResponseDto> uploadImage(@Parameter(description = "Visitor id") @PathVariable Long id, @Parameter(description = "Image file") @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(visitorService.uploadImage(id, file));
    }

    @Operation(summary = "Replace visitor image", description = "Replace existing visitor photo")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Image replaced") })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PutMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisitorResponseDto> replaceImage(@Parameter(description = "Visitor id") @PathVariable Long id, @Parameter(description = "Image file") @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(visitorService.replaceImage(id, file));
    }

    @Operation(summary = "Delete visitor image", description = "Delete visitor image")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Image deleted") })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @DeleteMapping("/{id}/image")
    public ResponseEntity<VisitorResponseDto> deleteImage(@Parameter(description = "Visitor id") @PathVariable Long id) {
        return ResponseEntity.ok(visitorService.deleteImage(id));
    }

    // ---------- FILES: ID Proof ----------
    @Operation(summary = "Upload ID proof", description = "Upload visitor ID proof file")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "ID proof uploaded") })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PostMapping(path = "/{id}/id-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisitorResponseDto> uploadIdProof(@Parameter(description = "Visitor id") @PathVariable Long id, @Parameter(description = "ID proof file") @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(visitorService.uploadIdProof(id, file));
    }

    @Operation(summary = "Replace ID proof", description = "Replace visitor ID proof file")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "ID proof replaced") })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @PutMapping(path = "/{id}/id-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisitorResponseDto> replaceIdProof(@Parameter(description = "Visitor id") @PathVariable Long id, @Parameter(description = "ID proof file") @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(visitorService.replaceIdProof(id, file));
    }

    @Operation(summary = "Delete ID proof", description = "Delete visitor ID proof file")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "ID proof deleted") })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @DeleteMapping("/{id}/id-proof")
    public ResponseEntity<VisitorResponseDto> deleteIdProof(@Parameter(description = "Visitor id") @PathVariable Long id) {
        return ResponseEntity.ok(visitorService.deleteIdProof(id));
    }

    // ==============================================================

    @Operation(summary = "Download visitor image", description = "Download visitor image (inline or attachment)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image returned (binary)"),
            @ApiResponse(responseCode = "404", description = "Image not found")
    })
    //@PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> downloadImage(
            @Parameter(description = "Visitor id") @PathVariable Long id,
            @Parameter(description = "Set download=true to force attachment") @RequestParam(name = "download", required = false, defaultValue = "false") boolean download) {

        VisitorEntity e = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));

        if (e.getImageBlob() == null) {
            throw new ResourceNotFoundException("No image found for visitor with id: " + id);
        }

        String contentType = e.getImageContentType() != null ? e.getImageContentType() : "application/octet-stream";
        String ext = extensionFromContentType(contentType);
        String disposition = download ? "attachment" : "inline";
        String filename = "visitor-" + id + "-photo" + ext;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(e.getImageBlob());
    }

    @Operation(summary = "Download ID proof", description = "Download visitor ID proof (binary)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ID proof returned (binary)"),
            @ApiResponse(responseCode = "404", description = "ID proof not found")
    })
    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping("/{id}/id-proof")
    public ResponseEntity<byte[]> downloadIdProof(
            @Parameter(description = "Visitor id") @PathVariable Long id,
            @Parameter(description = "Set download=true to force attachment") @RequestParam(name = "download", required = false, defaultValue = "false") boolean download) {

        VisitorEntity e = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));

        if (e.getIdProofBlob() == null) {
            throw new ResourceNotFoundException("No ID proof found for visitor with id: " + id);
        }

        String contentType = e.getIdProofContentType() != null ? e.getIdProofContentType() : "application/octet-stream";
        String ext = extensionFromContentType(contentType);
        String disposition = download ? "attachment" : "inline";
        String filename = "visitor-" + id + "-id-proof" + ext;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(e.getIdProofBlob());
    }

    // ==============================================================

    // ----------------------- New endpoints for email approval UX -----------------------

    /**
     * Redirect page rendered when email contains frontend link placeholder.
     */
    @Operation(summary = "Respond redirect page", description = "Render a small HTML page that asks host to sign-in and confirm approve/reject action")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "HTML page returned", content = @Content(mediaType = MediaType.TEXT_HTML_VALUE))
    })
    @GetMapping(path = "/respond-redirect", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> respondRedirect(@Parameter(description = "Visitor id from email", required = false) @RequestParam(value = "visitorId", required = false) Long visitorId,
                                                  @Parameter(description = "Action (approve/reject) from email", required = false) @RequestParam(value = "action", required = false) String action) {

        String safeAction = (action == null) ? "" : action.toLowerCase();
        String visitorLabel = (visitorId == null) ? "unknown visitor" : ("visitor #" + visitorId);

        // Minimal HTML UI with instructions and a button that will POST to /rkt/visitors/{id}/respond
        // It uses fetch(..., credentials: 'include') so session cookies are attached if the host is logged in
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>Respond to visitor</title></head><body style='font-family:Arial,Helvetica,sans-serif'>");
        html.append("<h2>Respond to ").append(escapeHtml(visitorLabel)).append("</h2>");
        html.append("<p>Action: <strong>").append(escapeHtml(safeAction.isBlank() ? "approve / reject" : safeAction)).append("</strong></p>");
        html.append("<p>If you're not signed in, please sign in to the app first. After signing in, come back to this page or click the button below.</p>");

        if (visitorId != null && ( "approve".equalsIgnoreCase(safeAction) || "reject".equalsIgnoreCase(safeAction) )) {
            String encodedAction = URLEncoder.encode(safeAction, StandardCharsets.UTF_8);
            html.append("<div style='margin-top:12px'>");
            html.append("<button id='doAction' style='padding:12px 18px;border-radius:6px;background:#28a745;color:white;border:none;cursor:pointer'>");
            html.append(safeAction.equals("approve") ? "Approve Visit" : "Reject Visit");
            html.append("</button>");
            html.append("</div>");
            html.append("<script>");
            html.append("document.getElementById('doAction').addEventListener('click', function(){");
            html.append("  const url = '/rkt/visitors/").append(visitorId).append("/respond?action=").append(encodedAction).append("';");
            html.append("  fetch(url, {method:'POST', credentials:'include', headers:{'Accept':'application/json'}})");
            html.append("    .then(r => r.text().then(t => ({ok:r.ok, status:r.status, text:t})))");
            html.append("    .then(res => {");
            html.append("       if (res.ok) { document.body.innerHTML = res.text; } else { document.body.innerHTML = '<h3>Error: '+res.status+'</h3><pre>'+res.text+'</pre>'; }");
            html.append("    }).catch(e => { document.body.innerHTML = '<h3>Network Error</h3><pre>'+e.toString()+'</pre>'; });");
            html.append("});");
            html.append("</script>");
        } else {
            html.append("<p>Invalid link. The email link should include visitorId and action parameters.</p>");
        }

        html.append("<hr/><p style='font-size:12px;color:#666'>If this link was emailed to you, and you believe it's malicious, contact Security Desk.</p>");
        html.append("</body></html>");
        return ResponseEntity.ok(html.toString());
    }

    @Operation(summary = "Respond action (approve/reject)", description = "Host performs approve or reject for the visitor. Requires authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Action processed and HTML returned", content = @Content(mediaType = MediaType.TEXT_HTML_VALUE)),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Visitor or User not found")
    })
    @PreAuthorize("isAuthenticated()")
    @PostMapping(path = "/{id}/respond", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> respondAction(@Parameter(description = "Visitor id to respond to") @PathVariable("id") Long id,
                                                @Parameter(description = "Action to perform (approve/reject)") @RequestParam("action") String action,
                                                Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).body("<h3>Unauthorized</h3><p>Please sign in and try again.</p>");
        }

        String email = authentication.getName().trim().toLowerCase();
        UserEntity authUser = userRepository.findWithRoleByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + email));

        VisitorEntity v = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found: " + id));

        // verify host
        if (v.getPersonWhomToMeet() == null || !v.getPersonWhomToMeet().getId().equals(authUser.getId())) {
            return ResponseEntity.status(403).body("<h3>Forbidden</h3><p>You are not the host for this visitor.</p>");
        }

        String act = (action == null) ? "" : action.trim().toLowerCase();
        try {
            if ("approve".equals(act)) {
                v.setVisitStatus(VisitStatus.APPROVED);
                visitorRepository.save(v);

                // generate + persist pass and mail it (existing PassManagerService handles mailing)
                try {
                    passManagerService.generateAndPersist(id, null, null);
                } catch (Exception ex) {
                    // pass generation / mail failed — show friendly message but visitor status remains APPROVED
                    String body = "<html><body><h3>Approved</h3><p>Visit approved but pass generation failed: "
                            + escapeHtml(ex.getMessage()) + "</p></body></html>";
                    return ResponseEntity.ok(body);
                }

                String body = "<html><body><h3>Visit Approved</h3><p>The visit has been approved and the pass has been generated.</p></body></html>";
                return ResponseEntity.ok(body);

            } else if ("reject".equals(act)) {
                v.setVisitStatus(VisitStatus.REJECTED);
                visitorRepository.save(v);

                // revoke pass if exists
                PassEntity pass = passRepository.findByVisitor_Id(id).orElse(null);
                if (pass != null) {
                    pass.setStatus(PassStatus.REVOKED);
                    passRepository.save(pass);
                }

                String body = "<html><body><h3>Visit Rejected</h3><p>The visit has been rejected and any pass (if present) has been revoked.</p></body></html>";
                return ResponseEntity.ok(body);
            } else {
                return ResponseEntity.badRequest().body("<h3>Bad Request</h3><p>Unknown action: " + escapeHtml(action) + "</p>");
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("<h3>Error</h3><p>" + escapeHtml(ex.getMessage()) + "</p>");
        }
    }


    private void validateOrNormalizeDateForWeb(VisitorDto dto) {
        if (!webSameDayOnly) return;

        ZoneId officeZone = ZoneId.of(officeZoneId);
        LocalDate today = LocalDate.now(officeZone);

        LocalDate requested = null;
        try {
            requested = dto.getDateOfVisiting();
        } catch (Exception ignored) { }

        if (requested == null || !requested.isEqual(today)) {
            if ("override".equalsIgnoreCase(webSameDayMode)) {
                try {
                    dto.setDateOfVisiting(today);
                } catch (Exception ignored) {
                    throw new IllegalArgumentException("Date of visit must be " + today + " (" + officeZone +
                            "). Server attempted to normalize but DTO is immutable. Change DTO or set the date to today.");
                }
            } else {
                throw new IllegalArgumentException("Web passes are same-day only. Please set Date of Visit to "
                        + today + " (office timezone: " + officeZone + ").");
            }
        }
    }

    // ... (keep makeImagePreview, bufferedImageToPngBytes, pdfFirstPageToPng, loadPdfDocument, extensionFromContentType, mapSortProperty, etc.)
    // For brevity those helper methods remain unchanged from your original controller file.

    private static String extensionFromContentType(String ct) {
        if (ct == null) return "";
        ct = ct.toLowerCase();
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("pdf")) return ".pdf";
        if (ct.contains("svg")) return ".svg";
        return "";
    }

//    private static String mapSortProperty(String clientProp) {
//        if (clientProp == null || clientProp.isBlank()) return null;
//
//        String p = clientProp.trim();
//
//
//        if ("asc".equalsIgnoreCase(p) || "desc".equalsIgnoreCase(p)) {
//            return null;
//        }
//
//        return switch (p) {
//            case "visitDate", "visit_date", "date" -> "dateOfVisiting";
//            case "visitorName", "name" -> "visitorName";
//            case "company" -> "company";
//            case "visitPurpose" -> "visitPurpose";
//            case "vehicleNumber", "vehicle" -> "vehicleNumber";
//            case "hostUserId", "hostId" -> "personWhomToMeet.id";
//            case "hostName", "personWhomToMeetName", "personName" -> "personWhomToMeet.userName";
//            case "departmentId" -> "department.id";
//            case "departmentName", "deptName" -> "department.name";
//            default -> p; // assume valid JPA path
//        };
//    }


    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
