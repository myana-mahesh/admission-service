package com.bothash.admissionservice.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeeLedgerResponseDto {
    private List<FeeLedgerRowDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private FeeLedgerSummaryDto summary;
}
