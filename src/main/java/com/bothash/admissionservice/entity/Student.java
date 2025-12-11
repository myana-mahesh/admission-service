package com.bothash.admissionservice.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.bothash.admissionservice.enumpackage.Gender;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Index;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student",
       indexes = {
           @Index(name = "idx_student_aadhaar", columnList = "aadhaar"),
           @Index(name = "idx_student_mobile", columnList = "mobile")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student extends Auditable {
	
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studentId;

    @Column(length = 32, unique = true)
    private String absId; // ABS-000000

    @Column(length = 150, nullable = false)
    private String fullName;

    @Column(nullable = false)
    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
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

    // Relations
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference("student-guardians")
    private List<Guardian> guardians = new ArrayList<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference("student-addresses")
    private List<StudentAddress> addresses = new ArrayList<>();
}

