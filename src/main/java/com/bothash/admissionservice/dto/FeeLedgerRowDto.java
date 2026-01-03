package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeeLedgerRowDto {
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
    private BigDecimal totalFeeAmount;
    private BigDecimal paidAmount;
    private BigDecimal pendingAmount;
    private LocalDate dueNextDate;
    private BigDecimal dueNextAmount;
    private String statusSummary;
}
