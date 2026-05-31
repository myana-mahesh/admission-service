package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class AdmissionInstallmentSummaryDto {
    private Long installmentId;
    private Integer studyYear;
    private Integer installmentNo;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private LocalDate dueDate;
}

