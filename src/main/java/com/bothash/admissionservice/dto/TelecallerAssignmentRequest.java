package com.bothash.admissionservice.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class TelecallerAssignmentRequest {
    /** Keycloak subject / user-id of the telecaller receiving the assignment. Required. */
    private String telecallerUserId;

    /** LT, LTE, EQ, GTE, GT. Optional. If set, {@link #paidAmountValue} must also be set. */
    private String paidAmountOp;

    /** Comparison threshold for the running paid-amount total. Optional. */
    private BigDecimal paidAmountValue;

    /** Batch code (matches {@code admission2.batch}). Optional. */
    private String batchCode;

    /** Course id (matches {@code admission2.course_id}). Optional. */
    private Long courseId;
}
