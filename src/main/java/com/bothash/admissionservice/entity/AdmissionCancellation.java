package com.bothash.admissionservice.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admission_cancellation")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdmissionCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Relation to Admission2 ---
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false, unique = true)
    @JsonBackReference("admission-cancellation")
    private Admission2 admission;

    private Double cancelCharges;

    @Column
    private String remark;

    @Column
    private String handlingPerson;

    // File proof - it will link with your FileUpload table
    private String refundProofFileName;

    @CreationTimestamp
    private LocalDateTime createdOn;

    @UpdateTimestamp
    private LocalDateTime modifiedOn;
}
