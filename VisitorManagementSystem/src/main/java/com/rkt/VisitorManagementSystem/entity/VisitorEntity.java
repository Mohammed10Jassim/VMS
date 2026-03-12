package com.rkt.VisitorManagementSystem.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rkt.VisitorManagementSystem.entity.enums.VisitPurpose;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "visitors",
        indexes = {
                @Index(name = "idx_visitors_person_to_meet", columnList = "person_to_meet_id"),
                @Index(name = "idx_visitors_department", columnList = "department_id"),
                @Index(name = "idx_visitors_visit_date", columnList = "visit_date")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VisitorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visitor_id")
    private Long id;

    @NotBlank(message = "Visitor name is required")
    @Length(max = 100, message = "Visitor name must be at most 100 characters")
    @Pattern(
            regexp = "^[A-Za-z][A-Za-z .'-]{1,99}$",
            message = "Visitor name contains invalid characters"
    )
    @Column(name = "visitor_name", nullable = false, length = 100)
    private String visitorName;

    @Length(max = 120, message = "Company must be at most 120 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9&()/' .,-]*$",
            message = "Company contains invalid characters"
    )
    @Column(name = "company", length = 120)
    private String company;

    @NotNull(message = "Visit purpose is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "visit_purpose", nullable = false, length = 20)
    private VisitPurpose visitPurpose;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Length(max = 120, message = "Email must be at most 120 characters")
    @Column(name = "email", nullable = false, length = 120)
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(
            regexp = "^\\+?[1-9]\\d{7,14}$",
            message = "Phone must be in valid E.164 format (e.g., +15551234567)"
    )
    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @NotNull(message = "Person to meet is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "person_to_meet_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_visitor_user")
    )
    private UserEntity personWhomToMeet;

    @NotNull(message = "Department is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "department_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_visitor_department")
    )
    private DepartmentEntity department;

    // ================== ID PROOF (stored as BLOB) ==================
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "id_proof_blob", columnDefinition = "LONGBLOB")
    @JsonIgnore
    private byte[] idProofBlob;

    @Length(max = 120)
    @Column(name = "id_proof_content_type", length = 120)
    private String idProofContentType;

    @PositiveOrZero
    @Column(name = "id_proof_size_bytes")
    private Long idProofSizeBytes;

    // ================== VISITOR PHOTO (stored as BLOB) ==================
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "image_blob", columnDefinition = "LONGBLOB")
    @JsonIgnore
    private byte[] imageBlob;

    @Length(max = 120)
    @Column(name = "image_content_type", length = 120)
    private String imageContentType;

    @PositiveOrZero
    @Column(name = "image_size_bytes")
    private Long imageSizeBytes;

    // ================== OTHER OPTIONALS ==================
    @Length(max = 20, message = "Vehicle number must be at most 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9 -]*$", message = "Vehicle number has invalid characters")
    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @NotNull(message = "Date of visiting is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Column(name = "visit_date", nullable = false)
    private LocalDate dateOfVisiting;

    // ================== VISIT STATUS (default PENDING) ==================
    @Builder.Default
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visit_status", nullable = false, length = 20)
    private VisitStatus visitStatus = VisitStatus.PENDING;

    // ===== Cross-field validation =====
    @AssertTrue(message = "Company is required when purpose is OFFICIAL or INTERVIEW")
    public boolean isCompanyValidForPurpose() {
        if (visitPurpose == null) return true;
        return switch (visitPurpose) {
            case OFFICIAL, INTERVIEW -> company != null && !company.isBlank();
            case PERSONAL -> true;
        };
    }

    // ===== Normalize inputs =====
    @PrePersist
    @PreUpdate
    private void normalizeAndValidate() {
        // Normalize common fields
        if (email != null) email = email.trim().toLowerCase();
        if (visitorName != null) visitorName = visitorName.trim().replaceAll("\\s+", " ");
        if (company != null) company = company.trim().replaceAll("\\s+", " ");
        if (vehicleNumber != null) vehicleNumber = vehicleNumber.trim().toUpperCase();
        if (phone != null) phone = phone.trim();

        // Defensive: ensure visitStatus is set to PENDING if absent
        if (visitStatus == null) {
            visitStatus = VisitStatus.PENDING;
        }

        // Validate required non-null fields explicitly and fail fast with meaningful messages
        List<String> missing = new ArrayList<>();
        if (isNullOrBlank(visitorName)) missing.add("visitorName");
        if (visitPurpose == null) missing.add("visitPurpose");
        if (isNullOrBlank(email)) missing.add("email");
        if (isNullOrBlank(phone)) missing.add("phone");
        if (personWhomToMeet == null) missing.add("personWhomToMeet");
        if (department == null) missing.add("department");
        if (dateOfVisiting == null) missing.add("dateOfVisiting");
        if (visitStatus == null) missing.add("visitStatus");

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required fields for VisitorEntity: " + String.join(", ", missing));
        }
    }

    private static boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }


    @Column(name = "approval_key_hash", length = 200)
    private String approvalKeyHash;

    @Column(name = "approval_key_expiry")
    private Instant approvalKeyExpiry;

    @Column(name = "approval_for_user_id")
    private Long approvalForUserId;
}
