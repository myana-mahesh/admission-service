package com.bothash.admissionservice.entity;

import com.bothash.admissionservice.enumpackage.GuardianRelation;
import com.fasterxml.jackson.annotation.JsonBackReference;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity
@Table(name = "guardian")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Guardian extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long guardianId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonBackReference("student-guardians")
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private GuardianRelation relation;

    @Column(length = 150)
    private String fullName;

    @Column(length = 20)
    private String mobile;
}
