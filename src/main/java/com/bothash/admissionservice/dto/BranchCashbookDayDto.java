package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchCashbookDayDto {
    private Long branchId;
    private String branchName;
    private String businessDate;
    private BigDecimal openingBalance;
    private BigDecimal cashCollected;
    private BigDecimal chequeCollected;
    private BigDecimal totalCollected;
    /** Sum of ORIGINAL amounts of every fee visible on the dashboard
     *  (both already-remitted and still-unremitted). Used for the
     *  "Student Fees Collected" panel header. Not used in the in-hand
     *  math — those keep working off the unremitted-remaining sums. */
    private BigDecimal grossStudentFeesCollected;
    private BigDecimal expensesAmount;
    private BigDecimal pettyCashAmount;
    private BigDecimal sentToHoAmount;
    private BigDecimal pendingToSendAmount;
    private BigDecimal closingBalance;
    private String sentToHoBy;
    private OffsetDateTime sentToHoAt;
    private String notes;
}
