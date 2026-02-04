package com.bothash.admissionservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class AdmissionOtherPaymentDto {
    private Long paymentId;
    private Long admissionId;
    private BigDecimal amount;
    private BigDecimal returnedAmount;
    private BigDecimal netAmount;
    private LocalDate paidOn;
    private String paymentMode;
    private String txnRef;
    private String category;
    private String remarks;
    private String receivedBy;
    private Long referencePaymentId;
    private String receiptName;
    private String receiptUrl;
}

