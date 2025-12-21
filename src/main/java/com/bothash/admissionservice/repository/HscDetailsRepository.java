package com.bothash.admissionservice.repository;

import com.bothash.admissionservice.entity.HscDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HscDetailsRepository extends JpaRepository<HscDetails, Long> {

   /* Optional<HscDetails> findByAdmission_AdmissionId(Long admissionId);*/
    Optional<HscDetails> findByStudent_StudentId(Long studentId);
}
