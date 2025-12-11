package com.bothash.admissionservice.entity;

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
@Table(name = "document_type",
       indexes = { @Index(name = "uk_doc_code", columnList = "code", unique = true) })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentType extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long docTypeId;

    @Column(length = 32, unique = true)
    private String code; // SSC, HSC, LC_TC, MIG, AADHAAR, PHOTO

    @Column(length = 120, nullable = false)
    private String name;
}