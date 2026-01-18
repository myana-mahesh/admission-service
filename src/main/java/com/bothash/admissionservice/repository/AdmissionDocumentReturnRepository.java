package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.AdmissionDocumentReturn;

public interface AdmissionDocumentReturnRepository extends JpaRepository<AdmissionDocumentReturn, Long> {
    List<AdmissionDocumentReturn> findByAdmissionAdmissionIdOrderByReturnedOnDescReturnIdDesc(Long admissionId);
}
