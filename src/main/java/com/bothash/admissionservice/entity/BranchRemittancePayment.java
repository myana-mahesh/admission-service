package com.bothash.admissionservice.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Historical snapshot of which payments were included in a given remittance
 * when it was Mark-Sent. Survives reject (which releases the live
 * {@code fee_installment_payment.remittance_id}) so the history-detail view
 * can still display the original payment list.
 */
@Entity
@Table(name = "branch_remittance_payment",
        indexes = {
                @Index(name = "idx_brp_remittance", columnList = "remittance_id"),
                @Index(name = "idx_brp_payment", columnList = "payment_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class BranchRemittancePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remittance_id", nullable = false)
    private BranchCashbookRemittance remittance;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    public BranchRemittancePayment(BranchCashbookRemittance remittance, Long paymentId) {
        this.remittance = remittance;
        this.paymentId = paymentId;
    }
}
