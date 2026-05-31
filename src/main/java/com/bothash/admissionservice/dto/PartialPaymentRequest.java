package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class PartialPaymentRequest {
    private BigDecimal amount;
    private String mode;
    private String paymentType;
    private String txnRef;
    private String remarks;
    private String receivedBy;
    private LocalDate paidOn;
    private UploadRequest receipt;
    private List<UploadRequest> receipts;
}
