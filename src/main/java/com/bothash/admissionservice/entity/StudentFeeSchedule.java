package com.bothash.admissionservice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_fee_schedule",
       indexes = {
           @Index(name = "idx_fee_schedule_student", columnList = "student_id"),
           @Index(name = "idx_fee_schedule_date", columnList = "scheduled_date"),
           @Index(name = "idx_fee_schedule_status", columnList = "status")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentFeeSchedule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal expectedAmount;

    @Column(length = 20, nullable = false)
    private String scheduleType; // PAYMENT_PROMISE, REMINDER, FOLLOW_UP

    @Column(length = 20, nullable = false)
    private String status; // PENDING, COMPLETED, CANCELLED, OVERDUE

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 100, nullable = false)
    private String createdByUser;

    @Column(length = 100)
    private String completedBy;

    private LocalDate completedDate;
}
