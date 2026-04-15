package com.bothash.admissionservice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "misc_payment", indexes = {
        @Index(name = "idx_misc_payment_date", columnList = "payment_date"),
        @Index(name = "idx_misc_payment_contact", columnList = "contact_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MiscPayment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "student_name", length = 160, nullable = false)
    private String studentName;

    @Column(name = "contact_number", length = 16, nullable = false)
    private String contactNumber;

    @Column(name = "batch", length = 64, nullable = false)
    private String batch;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "course_name", length = 160, nullable = false)
    private String courseName;

    @Column(name = "college_name", length = 160)
    private String collegeName;

    @Column(name = "fee_type", length = 80, nullable = false)
    private String feeType;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_mode", length = 32, nullable = false)
    private String paymentMode;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "receipt_name", length = 255)
    private String receiptName;

    @Column(name = "receipt_mime_type", length = 120)
    private String receiptMimeType;

    @Column(name = "receipt_size_bytes")
    private Integer receiptSizeBytes;

    @Column(name = "receipt_storage_url", length = 600)
    private String receiptStorageUrl;

    @Column(name = "receipt_sha256", length = 64)
    private String receiptSha256;

    @Column(name = "invoice_number", length = 120)
    private String invoiceNumber;

    @Column(name = "invoice_file_path", length = 600)
    private String invoiceFilePath;

    @Column(name = "invoice_download_url", length = 600)
    private String invoiceDownloadUrl;

    @Column(name = "remark", length = 1000)
    private String remark;

    @Column(name = "created_by", length = 120)
    private String createdBy;
}
