package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.NationalityMaster;

public interface NationalityMasterRepository extends JpaRepository<NationalityMaster, Long> {
    boolean existsByNameIgnoreCase(String name);
}
