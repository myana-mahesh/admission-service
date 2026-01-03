package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.DiscountReasonMaster;

public interface DiscountReasonMasterRepository extends JpaRepository<DiscountReasonMaster, Long> {
    boolean existsByNameIgnoreCase(String name);
}
