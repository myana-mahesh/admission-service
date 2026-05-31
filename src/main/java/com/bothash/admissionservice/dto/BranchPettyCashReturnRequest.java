package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class BranchPettyCashReturnRequest {
    private Long branchId;
    private LocalDate businessDate;
    private BigDecimal amount;
    private String note;
}

