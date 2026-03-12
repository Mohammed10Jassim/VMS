package com.rkt.VisitorManagementSystem.dto.responseDto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {
    private Long id;
    private String userName;
    private String employeeCode;
    private String email;
    private Boolean isActive;
    private String mobileNumber;
    private String departmentName;
    private String roleName;

    //private Long departmentId;
}
