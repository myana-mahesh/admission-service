package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TelecallerAssignmentDto {
    private Long id;
    private String telecallerUserId;
    private String paidAmountOp;
    private BigDecimal paidAmountValue;
    private String batchCode;
    private Long courseId;
    /** Populated best-effort by the service for display. Null if the course was deleted. */
    private String courseName;
    private Boolean active;
    private String createdBy;
    private LocalDateTime createdAt;
    private String deactivatedBy;
    private LocalDateTime deactivatedAt;
}
