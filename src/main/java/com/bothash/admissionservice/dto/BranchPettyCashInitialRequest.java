package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * Records the petty cash a branch already had on hand before they began
 * using the system. Unlike a regular top-up, this is NOT drawn from any
 * collected student fee, so {@code feePaymentIds} is intentionally absent.
 */
@Data
public class BranchPettyCashInitialRequest {
    private Long branchId;
    private LocalDate businessDate;
    private BigDecimal amount;
    private String note;
}
