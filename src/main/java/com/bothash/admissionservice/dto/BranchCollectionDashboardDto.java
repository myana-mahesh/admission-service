package com.bothash.admissionservice.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchCollectionDashboardDto {
    private BranchCashbookDayDto summary;
    private List<BranchCollectionPaymentItemDto> collections;
    private List<BranchCashbookExpenseDto> expenses;
}
