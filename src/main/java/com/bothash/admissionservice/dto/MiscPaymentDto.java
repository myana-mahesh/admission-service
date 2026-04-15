package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MiscPaymentDto {
    private Long paymentId;
    private String studentName;
    private String contactNumber;
    private String batch;
    private Long courseId;
    private String courseName;
    private String collegeName;
    private String feeType;
    private BigDecimal amount;
    private String paymentMode;
    private LocalDate paymentDate;
    private String receiptName;
    private String receiptUrl;
    private String invoiceNumber;
    private String invoiceUrl;
    private String remark;
    private String createdBy;
    private OffsetDateTime createdAt;
}
