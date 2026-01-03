package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.FeeInstallmentPayment;

public interface FeeInstallmentPaymentRepository extends JpaRepository<FeeInstallmentPayment, Long> {
    List<FeeInstallmentPayment> findByInstallment_InstallmentIdOrderByCreatedAtAsc(Long installmentId);
}
