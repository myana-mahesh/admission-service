package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.OtherPaymentUpload;

public interface OtherPaymentUploadRepository extends JpaRepository<OtherPaymentUpload, Long> {
    List<OtherPaymentUpload> findByAdmission_AdmissionId(Long admissionId);
    List<OtherPaymentUpload> findByAdmission_AdmissionIdAndField_Id(Long admissionId, Long fieldId);
}
