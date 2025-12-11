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
@Table(name = "academic_year",
       indexes = { @Index(name = "uk_year_label", columnList = "label", unique = true) })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcademicYear extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long yearId;

    @Column(length = 9, nullable = false, unique = true)
    private String label; // 2025-26
}

