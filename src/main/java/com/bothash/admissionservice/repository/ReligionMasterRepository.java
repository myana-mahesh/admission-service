package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.ReligionMaster;

public interface ReligionMasterRepository extends JpaRepository<ReligionMaster, Long> {
    boolean existsByNameIgnoreCase(String name);
}
