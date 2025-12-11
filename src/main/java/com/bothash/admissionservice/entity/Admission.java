package com.bothash.admissionservice.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String applicationNumber;
    private String studentName;
    private String email;
    private String phone;
    private String course;
    private String branch;
    private String admissionStatus; // PENDING, APPROVED, REJECTED
    private LocalDate admissionDate;

    private String aadharUrl;
    private String marksheetUrl;
    private String photoUrl;

    private String createdBy;
    private LocalDateTime createdAt;

    private String referralName;
    private String referralContact;
    private String feePlanCode;
    private String seatStatus; // CONFIRMED, WAITLISTED
}
