// src/main/java/com/bothash/admissionservice/dto/FeeInvoiceDto.java
package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class FeeInvoiceDto {

    private Long invoiceId;
    private Long installmentId;
    private String invoiceNumber;
    private BigDecimal amount;
    private String downloadUrl;
    private OffsetDateTime createdAt;
}
