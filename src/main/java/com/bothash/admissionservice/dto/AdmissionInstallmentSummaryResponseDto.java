package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class AdmissionInstallmentSummaryResponseDto {
    private BigDecimal totalFee;
    private BigDecimal totalPaid;
    private BigDecimal totalPending;
    private List<AdmissionInstallmentSummaryDto> installments;
}

