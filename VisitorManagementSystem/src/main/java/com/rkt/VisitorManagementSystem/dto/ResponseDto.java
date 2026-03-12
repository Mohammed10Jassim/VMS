// src/main/java/com/rkt/VisitorManagementSystem/dto/ResponseDto.java
package com.rkt.VisitorManagementSystem.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseDto<T> {
    private String message;
    private T data;

    public static <T> ResponseDto<T> of(String message, T data) {
        return ResponseDto.<T>builder().message(message).data(data).build();
    }
}
