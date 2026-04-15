package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.SkydiveAudit;

public interface SkydiveAuditRepository extends JpaRepository<SkydiveAudit, Long> {
    List<SkydiveAudit> findTop100ByOrderByTriggeredAtDesc();
}
