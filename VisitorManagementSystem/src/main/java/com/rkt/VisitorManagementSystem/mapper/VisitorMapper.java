
package com.rkt.VisitorManagementSystem.mapper;
import com.rkt.VisitorManagementSystem.dto.VisitorDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.VisitorResponseDto;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.entity.DepartmentEntity;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class VisitorMapper {

    public VisitorEntity toEntity(VisitorDto d, UserEntity host, DepartmentEntity dept) {
        if (d == null) return null;

        VisitorEntity e = VisitorEntity.builder()
                .id(d.getId())
                .visitorName(d.getVisitorName())
                .company(d.getCompany())
                .visitPurpose(d.getVisitPurpose())
                .email(d.getEmail())
                .phone(d.getPhone())
                .personWhomToMeet(host)
                .department(dept)
                .vehicleNumber(d.getVehicleNumber())
                .dateOfVisiting(d.getDateOfVisiting())
                .build();

        if (e.getVisitStatus() == null) {
            e.setVisitStatus(VisitStatus.PENDING);
        }

        // ===== Handle ID Proof (Base64 → BLOB) =====
        if (d.getIdProofBase64() != null && !d.getIdProofBase64().isBlank()) {
            try {
                byte[] idProofBytes = Base64.getDecoder().decode(d.getIdProofBase64());
                e.setIdProofBlob(idProofBytes);
                e.setIdProofSizeBytes((long) idProofBytes.length);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid Base64 data for ID proof");
            }
        }
        e.setIdProofContentType(d.getIdProofContentType());

        // ===== Handle Image (Base64 → BLOB) =====
        if (d.getImageBase64() != null && !d.getImageBase64().isBlank()) {
            try {
                byte[] imageBytes = Base64.getDecoder().decode(d.getImageBase64());
                e.setImageBlob(imageBytes);
                e.setImageSizeBytes((long) imageBytes.length);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid Base64 data for visitor image");
            }
        }
        e.setImageContentType(d.getImageContentType());

        return e;
    }

    /**
     * Default small response used in lists / normal GETs.
     * IMPORTANT: Do NOT access e.getImageBlob() or e.getIdProofBlob() here (lazy load).
     */
    public VisitorResponseDto toResponse(VisitorEntity e) {
        if (e == null) return null;

        return VisitorResponseDto.builder()
                .id(e.getId())
                .visitorName(e.getVisitorName())
                .company(e.getCompany())
                .visitPurpose(e.getVisitPurpose())
                .email(e.getEmail())
                .phone(e.getPhone())
                .personWhomToMeetId(e.getPersonWhomToMeet() != null ? e.getPersonWhomToMeet().getId() : null)
                .personWhomToMeetName(e.getPersonWhomToMeet() != null ? safeName(e.getPersonWhomToMeet()) : null)
                .departmentId(e.getDepartment() != null ? e.getDepartment().getId() : null)
                .departmentName(e.getDepartment() != null ? e.getDepartment().getName() : null)

                // id proof metadata (no blob)
                .idProofContentType(e.getIdProofContentType())
                .idProofSizeBytes(e.getIdProofSizeBytes())
                .hasIdProof(e.getIdProofSizeBytes() != null && e.getIdProofSizeBytes() > 0)
                .idProofDownloadUrl("/rkt/visitors/" + e.getId() + "/id-proof")

                // image metadata (no blob)
                .imageContentType(e.getImageContentType())
                .imageSizeBytes(e.getImageSizeBytes())
                .hasImage(e.getImageSizeBytes() != null && e.getImageSizeBytes() > 0)
                .imageDownloadUrl("/rkt/visitors/" + e.getId() + "/image")

                .vehicleNumber(e.getVehicleNumber())
                .dateOfVisiting(e.getDateOfVisiting())
                .visitStatus(e.getVisitStatus())
                .build();
    }

    /**
     * Back-compat method: returns blobs (expensive). Use only when controller receives includeBlobs=true.
     * This will cause LOBs to be loaded for each entity.
     */
    public VisitorResponseDto toResponseWithBlobs(VisitorEntity e) {
        if (e == null) return null;

        VisitorResponseDto dto = this.toResponse(e); // metadata populated
        // now add blobs (may trigger lazy load)
        dto.setIdProofBlob(e.getIdProofBlob());
        dto.setImageBlob(e.getImageBlob());
        return dto;
    }

    private String safeName(UserEntity u) {
        try {
            return u.getUserName();
        } catch (Exception ex) {
            return null;
        }
    }
}

