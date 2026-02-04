package com.bothash.admissionservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AdmissionOtherPaymentReturnRequest extends AdmissionOtherPaymentRequest {
    private Long referencePaymentId;
    private BigDecimal returnAmount;
}

