package com.bothash.admissionservice.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import com.bothash.admissionservice.enumpackage.PaymentMode;

import java.time.LocalDate;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentRequest {
  private BigDecimal amountPaid;
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate paidOn;
  private PaymentMode paymentMode; // Cash/UPI/Card/BankTransfer
  private String txnRef;
}
