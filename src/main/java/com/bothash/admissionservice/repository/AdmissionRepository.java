package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.Admission;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {
    boolean existsByCourseAndBranch(String course, String branch);
}
