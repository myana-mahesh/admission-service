package com.bothash.admissionservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "course_fee_template_installment")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CourseFeeTemplateInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long courseFeeTemplateInstallmentId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "course_fee_template_id", nullable = false)
    private CourseFeeTemplate template;

    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer dueDaysFromAdmission;

    // 🔥 NEW: which academic year this installment belongs to (1..course.years)
    @Column(name = "year_number", nullable = false)
    private Integer yearNumber;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

