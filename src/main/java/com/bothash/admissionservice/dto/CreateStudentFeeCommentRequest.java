package com.bothash.admissionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStudentFeeCommentRequest {

    private Long studentId;
    private String comment;
    private String commentedBy;
    private String commentType; // Optional: GENERAL, PAYMENT_REMINDER, PAYMENT_PLAN, ISSUE
}
