package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchRemittancesGroupDto {
    private Long branchId;
    private String branchName;
    private long totalCount;
    private BigDecimal totalSentAmount;
    private OffsetDateTime lastSentAt;
    private List<BranchRemittanceHistoryDto> remittances;
}
