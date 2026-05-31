package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class MiscPaymentRequest {

    private Long paymentId;

    private String studentName;

    private String contactNumber;

    private String batch;

    private Long courseId;

    private String collegeName;

    private String feeType;

    private BigDecimal amount;

    private String paymentMode;
    private String paymentType;

    private LocalDate paymentDate;

    private UploadRequest receipt;

    private String remark;

    private String createdBy;
}
