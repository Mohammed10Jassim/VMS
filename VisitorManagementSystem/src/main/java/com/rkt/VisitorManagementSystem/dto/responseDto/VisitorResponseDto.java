package com.rkt.VisitorManagementSystem.dto.responseDto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rkt.VisitorManagementSystem.entity.enums.VisitPurpose;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "VisitorResponse", description = "Visitor details returned by the API")
public class VisitorResponseDto {

    @Schema(description = "Visitor id", example = "123", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "Visitor full name", example = "John Doe")
    private String visitorName;

    @Schema(description = "Visitor company", example = "Acme Corp")
    private String company;

    @Schema(description = "Purpose of visit", example = "OFFICIAL")
    private VisitPurpose visitPurpose;

    @Schema(description = "Visitor email", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Visitor phone number", example = "+918925530039")
    private String phone;

    // Host & department info
    @Schema(description = "Host (person to meet) id", example = "42")
    private Long personWhomToMeetId;

    @Schema(description = "Host (person to meet) name", example = "Alice Smith")
    private String personWhomToMeetName;

    @Schema(description = "Department id", example = "3")
    private Long departmentId;

    @Schema(description = "Department name", example = "TECHNICAL")
    private String departmentName;

    // ID proof metadata
    @Schema(description = "ID proof content type", example = "image/png")
    private String idProofContentType;

    @Schema(description = "ID proof size in bytes", example = "45231")
    private Long idProofSizeBytes;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "ID proof file as Base64 (read-only). Encoded as string (Base64).",
            type = "string", format = "byte", accessMode = Schema.AccessMode.READ_ONLY)
    private byte[] idProofBlob;

    @Schema(description = "Indicates if ID proof exists", example = "true")
    private Boolean hasIdProof;

    @Schema(description = "Direct download URL for ID proof (if available)", example = "/api/v1/visitors/123/id-proof")
    private String idProofDownloadUrl;

    // Visitor photo metadata
    @Schema(description = "Visitor image content type", example = "image/jpeg")
    private String imageContentType;

    @Schema(description = "Visitor image size in bytes", example = "12345")
    private Long imageSizeBytes;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "Visitor image file as Base64 (read-only). Encoded as string (Base64).",
            type = "string", format = "byte", accessMode = Schema.AccessMode.READ_ONLY)
    private byte[] imageBlob;

    @Schema(description = "Indicates if visitor image exists", example = "true")
    private Boolean hasImage;

    @Schema(description = "Direct download URL for visitor image (if available)", example = "/api/v1/visitors/123/image")
    private String imageDownloadUrl;

    @Schema(description = "Vehicle number", example = "KA-01-AB-1234")
    private String vehicleNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(description = "Date of visiting (yyyy-MM-dd)", example = "2025-11-01", type = "string", format = "date")
    private LocalDate dateOfVisiting;

    // --- NEW: visit status (read-only in responses; defaults to PENDING) ---
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Builder.Default
    @Schema(description = "Current visit status", example = "PENDING", accessMode = Schema.AccessMode.READ_ONLY)
    private VisitStatus visitStatus = VisitStatus.PENDING;
}
