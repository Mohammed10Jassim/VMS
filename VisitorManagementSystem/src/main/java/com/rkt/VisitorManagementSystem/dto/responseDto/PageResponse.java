
package com.rkt.VisitorManagementSystem.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
    private boolean last;
    private boolean first;

    public static <T> PageResponse<T> error(String message) {
        PageResponse<T> p = new PageResponse<>();
        p.content = List.of();
        p.totalElements = 0;
        p.totalPages = 0;
        p.pageNumber = 0;
        p.pageSize = 0;
        p.last = true;
        p.first = true;
        return p;
    }
}
