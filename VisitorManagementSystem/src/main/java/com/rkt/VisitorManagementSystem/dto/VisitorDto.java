package com.rkt.VisitorManagementSystem.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rkt.VisitorManagementSystem.entity.enums.VisitPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VisitorRequest", description = "Payload to create or partially update a visitor")
public class VisitorDto {

    /*
     * ID
     * Must be null during create.
     * Not required for PATCH (since id comes from path variable).
     */
    @Null(groups = Create.class, message = "ID must be null when creating")
    private Long id;

    // ================= CREATE REQUIRED =================

    @NotBlank(groups = Create.class, message = "Visitor name is required")
    @Length(max = 100)
    @Pattern(regexp = "^[A-Za-z][A-Za-z .'-]{1,99}$")
    private String visitorName;

    @Length(max = 120)
    @Pattern(regexp = "^[A-Za-z0-9&()/' .,-]*$")
    private String company;

    @NotNull(groups = Create.class, message = "Visit purpose is required")
    private VisitPurpose visitPurpose;

    @NotBlank(groups = Create.class, message = "Email is required")
    @Email
    @Length(max = 120)
    private String email;

    @NotBlank(groups = Create.class, message = "Phone is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$")
    private String phone;

    @NotNull(groups = Create.class, message = "Person to meet is required")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long personWhomToMeetId;

    @NotNull(groups = Create.class, message = "Department is required")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long departmentId;

    // ================= FILE FIELDS =================

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String idProofBase64;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String imageBase64;

    private String idProofContentType;
    private Long idProofSizeBytes;

    private String imageContentType;
    private Long imageSizeBytes;

    // ================= OPTIONAL PATCH FIELDS =================

    @Length(max = 20)
    @Pattern(regexp = "^[A-Za-z0-9 -]*$")
    private String vehicleNumber;

    /*
     * Required only for CREATE.
     * PATCH does not require it.
     */
    @NotNull(groups = Create.class, message = "Date of visiting is required")
    @JsonAlias({"date", "dateOfVisiting"})
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfVisiting;

    // ================= VALIDATION GROUPS =================
    public interface Create {}
    public interface Update {}
}
