package com.bothash.admissionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "batch_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long batchId;

    @Column(length = 32, unique = true, nullable = false)
    private String code;

    @Column(length = 120)
    private String label;

    @Column(nullable = false)
    private boolean active = true;
}
