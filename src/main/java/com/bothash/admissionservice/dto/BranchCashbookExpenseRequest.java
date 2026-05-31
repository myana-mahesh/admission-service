package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class BranchCashbookExpenseRequest {
    private Long branchId;
    private LocalDate businessDate;
    private String title;
    private String note;
    private String sourceType;
    private BigDecimal amount;
    /** Cash fees this expense is drawn from. Required when sourceType=COLLECTION;
     *  ignored when sourceType=PETTY (petty expenses come from the petty box). */
    private List<Long> feePaymentIds;
}

