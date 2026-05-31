package com.bothash.admissionservice.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

    private List<T> content;

    private int number;            // current page index (0-based)
    private int size;              // page size
    private int totalPages;
    private long totalElements;

    private boolean first;
    private boolean last;
    private int numberOfElements;
}
