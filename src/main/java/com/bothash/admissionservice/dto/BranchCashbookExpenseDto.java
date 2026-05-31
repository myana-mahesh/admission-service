package com.bothash.admissionservice.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchCashbookExpenseDto {
    private Long id;
    private String title;
    private String note;
    private String sourceType;
    private BigDecimal amount;
}

