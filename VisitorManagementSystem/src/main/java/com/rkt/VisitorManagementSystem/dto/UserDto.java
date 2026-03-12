package com.rkt.VisitorManagementSystem.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class UserDto {

    public interface Create {}
    public interface Update {}

    private Long id;

    @NotBlank(message = "User name is required")
    @Size(max = 80, message = "User name must be ≤ 80 characters")
    @Pattern(regexp = "^[\\p{L}0-9 .'-]+$", message = "User name has invalid characters")
    private String userName;

    @NotBlank(message = "Employee code is required")
    @Size(min=5,max=5, message = "Employee code must be ≤ 20 digits")
    @Pattern(regexp = "^[0-9]+$", message = "Employee code must contain only digits")
    private String employeeCode;


    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    @Size(max = 120, message = "Email must be ≤ 120 characters")
    private String email;

    /** For create/update: validate at boundary; hash in service later */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 200, message = "Password must be between 8 and 200 characters")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** Optional toggle; defaults to true if null */

    @JsonAlias("enabled")
    private Boolean isActive;

    /** Optional; blank allowed OR E.164 like +919876543210 */
    @Pattern(regexp = "^$|^\\+?[1-9]\\d{1,14}$",
            message = "Mobile number must be E.164 (e.g., +919876543210)")
    @Size(max = 20, message = "Mobile number must be ≤ 20 characters")
    private String mobileNumber;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long departmentId;   // optional here; required in service on create

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long roleId;         // optional here; required in service on create
}
