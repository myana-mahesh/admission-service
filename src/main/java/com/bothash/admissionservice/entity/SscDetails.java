package com.bothash.admissionservice.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "student_ssc_details")
@Data
public class SscDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Double percentage; // 0–100

    @Column(length = 100)
    private String board;

    @Column(name = "passing_year")
    private Integer passingYear;

    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

 /*   // 🔥 OWNING SIDE
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false, unique = true)
    private Admission2 admission;*/

    // 🔥 OWNING SIDE
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    @JsonBackReference
    private Student student;
}
