package com.bothash.admissionservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A rule assigning a slice of the fees ledger to a specific telecaller user.
 * When the assigned user opens Fees Overview, the ledger query is filtered
 * to admissions matching at least one active rule. Every non-null criterion
 * on a rule must hold; nulls are treated as "no restriction".
 *
 * <p>Op codes align with {@code FeeLedgerService.applyPaidAmountFilter}:
 * LT, LTE, EQ, GTE, GT.
 */
@Entity
@Table(name = "telecaller_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelecallerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telecaller_user_id", nullable = false, length = 64)
    private String telecallerUserId;

    @Column(name = "paid_amount_op", length = 4)
    private String paidAmountOp;

    @Column(name = "paid_amount_value", precision = 15, scale = 2)
    private BigDecimal paidAmountValue;

    @Column(name = "batch_code", length = 64)
    private String batchCode;

    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deactivated_by", length = 64)
    private String deactivatedBy;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;
}
