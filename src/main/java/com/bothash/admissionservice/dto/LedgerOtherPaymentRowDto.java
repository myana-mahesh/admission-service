package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class LedgerOtherPaymentRowDto {
    private Long paymentId;
    private Long admissionId;
    private Long studentId;
    private String studentName;
    private String absId;
    private String mobile;
    private Long branchId;
    private String branchName;
    private Long courseId;
    private String courseName;
    private String batch;
    private String academicYear;

    private LocalDate paidOn;
    private BigDecimal amount;
    private BigDecimal returnedAmount;
    private BigDecimal netAmount;
    private String paymentMode;
    private String paymentType;
    private String txnRef;
    private String category;
    private String remarks;
    private String receivedBy;
    private Long referencePaymentId;
    private String receiptName;
    private String receiptUrl;
    private String invoiceNumber;
    private String invoiceUrl;
}
