package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class FeePaymentGroupDto {
    private Long admissionId;
    private Long studentId;
    private String studentName;
    private String absId;
    private String mobile;
    private String fatherMobile;
    private String motherMobile;
    private Long branchId;
    private String branchName;
    private Long courseId;
    private String courseName;
    private String batch;
    private String academicYear;
    private String paymentGroupId;
    private LocalDate paidOn;
    private BigDecimal totalAmount;
    private String paymentMode;
    private String paymentType;
    private String txnRef;
    private String remarks;
    private String receivedBy;
    private String status;
    private Boolean verified;
    private Boolean accountHeadVerified;
    private Boolean accountHeadRejected;
    private String rejectionReason;
    private String accountHeadRejectionReason;
    private String receiptUrl;
    private String receiptName;
    private List<ReceiptLinkDto> receipts;
    private Integer allocationCount;
    private String invoiceNumber;
    private String invoiceUrl;
    private List<InvoiceLinkDto> invoices;

    @Data
    public static class InvoiceLinkDto {
        private String invoiceNumber;
        private String invoiceUrl;
    }

    @Data
    public static class ReceiptLinkDto {
        private String name;
        private String url;
    }
}
