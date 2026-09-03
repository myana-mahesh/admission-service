package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.BranchRemittancePayment;

public interface BranchRemittancePaymentRepository extends JpaRepository<BranchRemittancePayment, Long> {
    List<BranchRemittancePayment> findByRemittance_IdOrderByIdAsc(Long remittanceId);
}
