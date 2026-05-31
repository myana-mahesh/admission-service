package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class BranchCashbookDayUpsertRequest {
    private Long branchId;
    private LocalDate businessDate;
    private BigDecimal expensesAmount;
    private BigDecimal pettyCashAmount;
    private BigDecimal sentToHoAmount;
    private String sentToHoBy;
    private String notes;
    /** Payment IDs the user picked for this remittance. Required when sending. */
    private List<Long> paymentIds;
}

