package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchRemittanceHistoryDto {
    private Long id;
    private String businessDate;
    private BigDecimal sentAmount;
    private String sentBy;
    private OffsetDateTime sentAt;
    private String notes;
}

