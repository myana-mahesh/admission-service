package com.bothash.admissionservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.BatchMaster;

public interface BatchMasterRepository extends JpaRepository<BatchMaster, Long> {
    Optional<BatchMaster> findByCodeIgnoreCase(String code);
    Optional<BatchMaster> findByLabelIgnoreCase(String label);
}
