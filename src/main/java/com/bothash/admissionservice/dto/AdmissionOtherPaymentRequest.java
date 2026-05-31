package com.bothash.admissionservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class AdmissionOtherPaymentRequest {
    private BigDecimal amount;
    private String mode;
    private String paymentType;
    private String txnRef;
    private String category;
    private String remarks;
    private String receivedBy;
    private LocalDate paidOn;
    private UploadRequest receipt;
}
