package com.rkt.VisitorManagementSystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileResource {
    private byte[] bytes;
    private String contentType;
    private Long size;
}
