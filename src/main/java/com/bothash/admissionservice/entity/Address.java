package com.bothash.admissionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "address", indexes = { @Index(name = "idx_addr_pincode", columnList = "pincode") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Address extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;

    @Column(length = 200)
    private String line1;

    @Column(length = 120)
    private String area;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 6)
    private String pincode;
}
