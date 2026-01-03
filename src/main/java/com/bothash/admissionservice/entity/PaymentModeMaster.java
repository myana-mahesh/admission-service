package com.bothash.admissionservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "payment_mode")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentModeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentModeId;

    @Column(length = 32, unique = true, nullable = false)
    private String code;

    @Column(length = 64, nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int displayOrder = 0;
}
