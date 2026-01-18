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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_mode_id")
    private PaymentModeMaster paymentMode;

    @Column(length = 120)
    private String txnRef;

    @Column(length = 120)
    private String receivedBy;

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

    private LocalDate paidOn;
}
