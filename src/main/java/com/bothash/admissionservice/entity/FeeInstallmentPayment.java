package com.bothash.admissionservice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fee_installment_payment",
       indexes = {
           @Index(name = "idx_fee_pay_installment", columnList = "installment_id"),
           @Index(name = "idx_fee_pay_group", columnList = "payment_group_id"),
           @Index(name = "idx_fee_pay_mode", columnList = "payment_mode_id"),
           @Index(name = "idx_fee_pay_verified", columnList = "is_verified"),
           @Index(name = "idx_fee_pay_txn_ref", columnList = "txn_ref"),
           @Index(name = "idx_fee_pay_created", columnList = "created_at")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeInstallmentPayment extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id", nullable = false)
    private FeeInstallment installment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_group_id", length = 36)
    private String paymentGroupId;

    /**
     * Null = not yet remitted, still available for selection on the branch
     * collections dashboard. When stamped, points at the
     * {@code branch_cashbook_remittance} row this payment was sent with.
     */
    @Column(name = "remittance_id")
    private Long remittanceId;

    /**
     * Running total consumed by branch expenses and petty topups (positive)
     * minus restorations from petty returns (negative). Denormalized cache of
     * {@code sum(fee_payment_allocation.amount)} for this payment. The fee's
     * still-available amount is {@code amount - consumedAmount}.
     */
    @Column(name = "consumed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal consumedAmount = BigDecimal.ZERO;

    @PrePersist
    void defaultConsumedAmount() {
        if (consumedAmount == null) {
            consumedAmount = BigDecimal.ZERO;
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_mode_id")
    private PaymentModeMaster paymentMode;

    @Column(length = 120)
    private String txnRef;

    @Column(length = 500)
    private String remarks;

    @Column(length = 120)
    private String receivedBy;

    @Column(name = "payment_type", length = 20)
    private String paymentType;

    @Column(length = 40)
    private String status;

    private Boolean isVerified;

    @Column(length = 120)
    private String verifiedBy;

    private java.time.LocalDateTime verifiedAt;

    @Column(name = "is_account_head_verified")
    private Boolean isAccountHeadVerified;

    @Column(name = "account_head_verified_at")
    private java.time.LocalDateTime accountHeadVerifiedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "rejected_by", length = 120)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private java.time.LocalDateTime rejectedAt;

    @Column(name = "account_head_rejection_reason", length = 500)
    private String accountHeadRejectionReason;

    @Column(name = "account_head_rejected_by", length = 120)
    private String accountHeadRejectedBy;

    @Column(name = "account_head_rejected_at")
    private java.time.LocalDateTime accountHeadRejectedAt;

    private LocalDate paidOn;
}
