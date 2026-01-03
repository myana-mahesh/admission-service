package com.bothash.admissionservice.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "student_hsc_details")
@Data
public class HscDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer physicsMarks; // 0–100

    @Column(nullable = false)
    private Integer chemistryMarks; // 0–100

    @Column(nullable = false)
    private Integer biologyMarks; // 0–100

    @Column(nullable = false)
    private Double pcbPercentage; // derived

    @Column
    private Double percentage; // 0-100

    @Column(length = 150, nullable = false)
    private String collegeName;

    @Column(length = 150)
    private String subjects; // optional text

    @Column(length = 50)
    private String registrationNumber;

    @Column(nullable = false)
    private Integer passingYear;

/*    @OneToOne
    @JoinColumn(name = "admission_id", nullable = false, unique = true)
    private Admission2 admission;*/

    @OneToOne
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    @JsonBackReference
    private Student student;
}
