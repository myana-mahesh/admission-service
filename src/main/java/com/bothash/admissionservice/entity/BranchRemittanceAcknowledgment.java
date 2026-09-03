package com.bothash.admissionservice.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user acknowledgment of a rejected remittance notification — the
 * "I've seen this, stop showing it" record. Branch users only see rejected
 * notifications they haven't acked themselves; other branch staff still
 * see the same notification until each acks it for themselves.
 */
@Entity
@Table(name = "branch_remittance_acknowledgment",
        indexes = {
                @Index(name = "idx_bra_remittance", columnList = "remittance_id"),
                @Index(name = "idx_bra_user", columnList = "acknowledged_by")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_bra_remittance_user",
                        columnNames = {"remittance_id", "acknowledged_by"})
        })
@Getter
@Setter
@NoArgsConstructor
public class BranchRemittanceAcknowledgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "remittance_id", nullable = false)
    private Long remittanceId;

    @Column(name = "acknowledged_by", nullable = false, length = 120)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at", nullable = false)
    private OffsetDateTime acknowledgedAt;

    public BranchRemittanceAcknowledgment(Long remittanceId, String acknowledgedBy) {
        this.remittanceId = remittanceId;
        this.acknowledgedBy = acknowledgedBy;
    }

    @PrePersist
    void defaultAcknowledgedAt() {
        if (acknowledgedAt == null) {
            acknowledgedAt = OffsetDateTime.now();
        }
    }
}
