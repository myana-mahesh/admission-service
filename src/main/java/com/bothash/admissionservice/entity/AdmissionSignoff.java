package com.bothash.admissionservice.entity;


import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admission_signoff",
       uniqueConstraints = @UniqueConstraint(name = "uk_signoff_adm", columnNames = {"admission_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdmissionSignoff extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long signoffId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    private Admission2 admission;

    private OffsetDateTime headSignAt;
    private OffsetDateTime clerkSignAt;
    private OffsetDateTime counsellorSignAt;
}

