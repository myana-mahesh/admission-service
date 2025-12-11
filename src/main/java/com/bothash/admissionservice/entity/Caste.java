package com.bothash.admissionservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "caste")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Caste {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long casteId;
    @Column(nullable = false)
    private String casteName;
}
