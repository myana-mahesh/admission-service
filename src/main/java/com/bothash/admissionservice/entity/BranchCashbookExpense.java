package com.bothash.admissionservice.entity;

import java.math.BigDecimal;

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
@Table(name = "branch_cashbook_expense",
        indexes = {
                @Index(name = "idx_branch_expense_cashbook", columnList = "cashbook_day_id")
        })
@Getter
@Setter
public class BranchCashbookExpense extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashbook_day_id", nullable = false)
    private BranchCashbookDay cashbookDay;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "source_type", length = 20, nullable = false)
    private String sourceType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}

