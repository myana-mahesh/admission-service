package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.TelecallerAssignment;

public interface TelecallerAssignmentRepository extends JpaRepository<TelecallerAssignment, Long> {
    List<TelecallerAssignment> findByTelecallerUserIdAndActiveTrue(String telecallerUserId);
    List<TelecallerAssignment> findByActiveTrueOrderByCreatedAtDesc();
    List<TelecallerAssignment> findAllByOrderByCreatedAtDesc();
}
