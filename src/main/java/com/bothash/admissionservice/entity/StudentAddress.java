package com.bothash.admissionservice.entity;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_address",
       uniqueConstraints = @UniqueConstraint(name = "uk_student_type", columnNames = {"student_id", "type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentAddress extends Auditable {
    @Id @GeneratedValue
    (strategy = GenerationType.IDENTITY)
    private Long studentAddressId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonBackReference("student-addresses")
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id", nullable = false)
    
    private Address address;
    

    @Column(length = 10, nullable = false)
    private String type; // current / permanent
}
