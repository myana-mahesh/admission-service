package com.bothash.admissionservice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.bothash.admissionservice.enumpackage.PaymentMode;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fee_installment",
       uniqueConstraints = @UniqueConstraint(name = "uk_fee_unique", columnNames = {"admission_id","study_year","installment_no"}),
       indexes = { @Index(name = "idx_fee_due", columnList = "due_date,amount_due") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeeInstallment extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long installmentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "admission_id", nullable = false)
    @JsonBackReference("admission-installments")
    private Admission2 admission;

    @Column(nullable = false)
    private Integer studyYear; // 1..4

    @Column(nullable = false)
    private Integer installmentNo; // 1 or 2

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @Column(nullable = true, precision = 10, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    private LocalDate paidOn;

//    @Enumerated(EnumType.STRING)
//    @Column(length = 20)
//    private PaymentMode paymentMode;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_mode_id")
    @JsonBackReference
    private PaymentModeMaster paymentMode;

    @Column(length = 100)
    private String txnRef;
    
    private String status;
    
    private String receivedBy;
    
    @Column(name = "is_verified", nullable = false, columnDefinition = "boolean default false")
    private Boolean isVerified = false;

}
