package com.bothash.admissionservice.repository;

import com.bothash.admissionservice.entity.SscDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SscDetailsRepository extends JpaRepository<SscDetails, Long> {

    /*Optional<SscDetails> findByAdmission_AdmissionId(Long admissionId);*/
    Optional<SscDetails> findByStudent_StudentId(Long studentId);
}

