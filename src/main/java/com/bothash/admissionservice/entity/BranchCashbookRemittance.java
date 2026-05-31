package com.bothash.admissionservice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
import lombok.Setter;

@Entity
@Table(name = "branch_cashbook_remittance",
        indexes = {
                @Index(name = "idx_bcr_branch_sent_at", columnList = "branch_id,sent_at")
        })
@Getter
@Setter
public class BranchCashbookRemittance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private BranchMaster branch;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "sent_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sentAmount;

    @Column(name = "sent_by", length = 120)
    private String sentBy;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Column(name = "notes", length = 500)
    private String notes;
}
