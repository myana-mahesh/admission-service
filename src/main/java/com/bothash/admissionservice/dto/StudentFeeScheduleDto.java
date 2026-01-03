package com.bothash.admissionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentFeeScheduleDto {

    private Long scheduleId;
    private Long studentId;
    private String studentName;
    private LocalDate scheduledDate;
    private BigDecimal expectedAmount;
    private String scheduleType;
    private String status;
    private String notes;
    private String createdByUser;
    private String completedBy;
    private LocalDate completedDate;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
