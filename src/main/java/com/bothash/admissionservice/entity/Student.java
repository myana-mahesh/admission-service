package com.bothash.admissionservice.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.bothash.admissionservice.enumpackage.Gender;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student",
       indexes = {
           @Index(name = "idx_student_aadhaar", columnList = "aadhaar"),
           @Index(name = "idx_student_mobile", columnList = "mobile"),
           @Index(name = "idx_student_full_name", columnList = "full_name"),
           @Index(name = "idx_student_gender", columnList = "gender")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student extends Auditable {
	
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studentId;

    @Column(length = 32, unique = true)
    private String absId; // ABS-000000

    @Column(length = 150, nullable = false)
    private String fullName;

    @Column
    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(length = 12)
    private String aadhaar; // digits only

    @Column(length = 64)
    private String nationality;

    @Column(length = 64)
    private String religion;

    @Column(length = 64)
    private String caste;

    @Column(length = 160)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Column(length = 5)
    private String bloodGroup;

    // Relations
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference("student-guardians")
    private List<Guardian> guardians = new ArrayList<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference("student-addresses")
    private List<StudentAddress> addresses = new ArrayList<>();

    private Integer age;

    @Column(name = "batch")
    private String batch;

    @Column(name = "registration_number", unique = true)
    private String registrationNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id")
    private Course course;

    // 🔥 INVERSE SIDE
    @OneToOne(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private SscDetails sscDetails;

    @OneToOne(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private HscDetails hscDetails;

/*    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    private String academicYearLabel; // e.g., 2025-26*/
}
