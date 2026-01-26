package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.AdmissionAudit;

public interface AdmissionAuditRepository extends JpaRepository<AdmissionAudit, Long> {
    java.util.List<AdmissionAudit> findByAdmissionAdmissionIdOrderByChangedAtDesc(Long admissionId);
}
