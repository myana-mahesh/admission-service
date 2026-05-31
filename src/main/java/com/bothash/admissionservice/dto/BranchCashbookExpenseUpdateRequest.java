package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class BranchCashbookExpenseUpdateRequest {
    private Long branchId;
    private String title;
    private String note;
    private String sourceType;
    private BigDecimal amount;
    /** Cash fees this expense is drawn from. Required for COLLECTION source. */
    private List<Long> feePaymentIds;
}
