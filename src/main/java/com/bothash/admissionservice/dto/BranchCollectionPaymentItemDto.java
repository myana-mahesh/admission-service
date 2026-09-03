package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchCollectionPaymentItemDto {
    private Long paymentId;
    /** All payment IDs in this grouped row (one row = one physical txn that may
     *  span multiple installments). Used to stamp every payment when the row
     *  is selected for remittance. */
    private List<Long> paymentIds;
    /** Non-null when this grouped row has already been sent to HO; the UI uses
     *  this to lock the checkbox and show a "Sent" badge. */
    private Long remittanceId;
    private Long admissionId;
    private String studentName;
    private String courseName;
    private String paymentType;
    /** Transaction date as recorded on the payment. */
    private LocalDate paidOn;
    /** Original amount as collected from the student. */
    private BigDecimal amount;
    /** Total amount consumed by expenses / petty topups, net of petty returns. */
    private BigDecimal consumedAmount;
    /** {@code amount - consumedAmount}. What's still available to remit or to draw from. */
    private BigDecimal remainingAmount;
    private String txnRef;
    private String receivedBy;
}

