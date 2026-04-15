package com.bothash.admissionservice.repository;

import com.bothash.admissionservice.entity.AdmissionOtherPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdmissionOtherPaymentRepository extends JpaRepository<AdmissionOtherPayment, Long> {
    List<AdmissionOtherPayment> findByAdmissionAdmissionIdOrderByPaidOnDescPaymentIdDesc(Long admissionId);
    List<AdmissionOtherPayment> findByReferencePaymentPaymentId(Long referencePaymentId);
    List<AdmissionOtherPayment> findByAdmissionAdmissionId(Long admissionId);
}
