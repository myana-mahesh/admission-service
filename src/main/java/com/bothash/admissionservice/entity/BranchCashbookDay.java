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
@Table(name = "branch_cashbook_day",
        indexes = {
                @Index(name = "idx_cashbook_branch_date", columnList = "branch_id,business_date", unique = true)
        })
@Getter
@Setter
public class BranchCashbookDay extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private BranchMaster branch;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "expenses_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal expensesAmount = BigDecimal.ZERO;

    @Column(name = "petty_cash_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal pettyCashAmount = BigDecimal.ZERO;

    @Column(name = "sent_to_ho_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sentToHoAmount = BigDecimal.ZERO;

    @Column(name = "sent_to_ho_by", length = 120)
    private String sentToHoBy;

    @Column(name = "sent_to_ho_at")
    private OffsetDateTime sentToHoAt;

    @Column(name = "notes", length = 500)
    private String notes;
}

