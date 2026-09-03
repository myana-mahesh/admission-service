package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchRemittanceHistoryDto {
    private Long id;
    private Long branchId;
    private String branchName;
    private String businessDate;
    private BigDecimal sentAmount;
    private String sentBy;
    private OffsetDateTime sentAt;
    private String notes;
    /** PENDING / ACCEPTED / REJECTED — HO acceptance state. */
    private String status;
    private String handlerName;
    private String handlerRemark;
    private String handledBy;
    private OffsetDateTime handledAt;
    /** PETTY_CASH or COLLECTION. */
    private String source;
}

