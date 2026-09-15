package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.AdmissionSection;

public interface AdmissionSectionRepository extends JpaRepository<AdmissionSection, Long> {
    List<AdmissionSection> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<AdmissionSection> findAllByOrderBySortOrderAscNameAsc();
}
