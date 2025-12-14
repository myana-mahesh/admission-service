package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bothash.admissionservice.entity.AdmissionCancellation;

public interface AdmissionCancellationRepository extends JpaRepository<AdmissionCancellation, Long> {

    AdmissionCancellation findByAdmissionAdmissionId(Long admissionId);
}
