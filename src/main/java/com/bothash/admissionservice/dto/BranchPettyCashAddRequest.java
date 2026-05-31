package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class BranchPettyCashAddRequest {
    private Long branchId;
    private LocalDate businessDate;
    private BigDecimal amount;
    private String note;
    /** Cash fees this petty topup is drawn from. Required. */
    private List<Long> feePaymentIds;
}

