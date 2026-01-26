package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.StudentAdditionalQualification;

public interface StudentAdditionalQualificationRepository extends JpaRepository<StudentAdditionalQualification, Long> {
    List<StudentAdditionalQualification> findByAdmissionAdmissionIdOrderByQualificationIdAsc(Long admissionId);

    void deleteByAdmissionAdmissionId(Long admissionId);
}
