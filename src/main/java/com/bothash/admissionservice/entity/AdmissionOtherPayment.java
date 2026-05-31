package com.bothash.admissionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "admission_other_payment")
@Getter
@Setter
public class AdmissionOtherPayment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_id", nullable = false)
    private Admission2 admission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_mode_id")
    private PaymentModeMaster paymentMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_payment_id")
    private AdmissionOtherPayment referencePayment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "returned_amount", precision = 12, scale = 2)
    private BigDecimal returnedAmount;

    @Column(nullable = false)
    private LocalDate paidOn;

    @Column(length = 64)
    private String txnRef;

    @Column(length = 128)
    private String category;

    @Column(length = 256)
    private String remarks;

    @Column(length = 120)
    private String receivedBy;

    @Column(name = "payment_type", length = 20)
    private String paymentType;

    @Column(length = 255)
    private String receiptName;

    @Column(length = 120)
    private String receiptMimeType;

    @Column
    private Integer receiptSizeBytes;

    @Column(length = 600)
    private String receiptStorageUrl;

    @Column(length = 64)
    private String receiptSha256;

    @Column(length = 120)
    private String invoiceNumber;

    @Column(length = 600)
    private String invoiceFilePath;

    @Column(length = 600)
    private String invoiceDownloadUrl;
}
