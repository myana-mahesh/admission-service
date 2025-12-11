package com.bothash.admissionservice.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InstallmentUpsertRequest {
  private int studyYear;
  private BigDecimal amountDue;
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate dueDate;
  
  
  private Long id;

  /** Client-generated key for *new* installments (required if id is null). e.g., "inst-1" */
  private String tempId;

  // Minimal common fields (add what you need)
  private Integer installmentNo;

  // Optional: if you plan to capture payment metadata at creation
  private LocalDate paidOn;
  private String mode;     // e.g., CASH/UPI/CARD
  private String remarks;
  private String status;
  private String receivedBy;
  private Double yearlyFees;
}
