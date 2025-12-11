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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admission_document",
       uniqueConstraints = @UniqueConstraint(name = "uk_adm_doctype", columnNames = {"admission_id", "doc_type_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdmissionDocument extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long admissionDocId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    private Admission2 admission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_type_id", nullable = false)
    private DocumentType docType;

    @Column(nullable = false)
    private boolean received = false;
    
    private String documentPath;
}