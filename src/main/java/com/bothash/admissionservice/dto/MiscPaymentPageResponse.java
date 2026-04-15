package com.bothash.admissionservice.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MiscPaymentPageResponse {
    private List<MiscPaymentDto> content;
    private int number;
    private int size;
    private int totalPages;
    private long totalElements;
    private int numberOfElements;
    private boolean first;
    private boolean last;
}
