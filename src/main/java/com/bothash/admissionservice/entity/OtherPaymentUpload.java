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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "other_paymen_upload")
@Getter
@Setter
@NoArgsConstructor
public class OtherPaymentUpload extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long uploadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    private Admission2 admission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private OtherPaymentField field;

    @Column(length = 200, nullable = false)
    private String filename;

    @Column(length = 80)
    private String mimeType;

    @Column
    private Integer sizeBytes;

    @Column(length = 500, nullable = false)
    private String storageUrl;

    @Column(length = 64)
    private String sha256;
}
