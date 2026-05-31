package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchRemittanceDetailDto {
    private Long remittanceId;
    private String businessDate;
    private String cycleStart;
    private String cycleEnd;

    private BigDecimal totalStudentFeeCollected;
    private BigDecimal cashCollected;
    private BigDecimal chequeCollected;

    private BigDecimal collectionExpenses;
    private BigDecimal pettyExpenses;
    private BigDecimal totalBranchExpenses;

    private BigDecimal totalInHandCollection;

    private BigDecimal pettyTopupTotal;
    private BigDecimal pettyReturnTotal;

    private BigDecimal sentAmount;
    private String sentBy;
    private OffsetDateTime sentAt;
    private String notes;

    private List<BranchCollectionPaymentItemDto> collections;
    private List<BranchCashbookExpenseDto> expenses;
    private List<BranchCashbookExpenseDto> pettyTransactions;
}
