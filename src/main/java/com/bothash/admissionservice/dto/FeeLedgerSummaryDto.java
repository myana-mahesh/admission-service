package com.bothash.admissionservice.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeeLedgerSummaryDto {
    private BigDecimal totalFeeAmount;
    private BigDecimal totalCollected;
    private BigDecimal totalPending;
    private BigDecimal overdueAmount;
    private BigDecimal dueNext7DaysAmount;
    private Long underVerificationCount;
}
