package com.bothash.admissionservice.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class PartialPaymentRequest {
    private BigDecimal amount;
    private String mode;
    private String txnRef;
    private String receivedBy;
    private UploadRequest receipt;
}
