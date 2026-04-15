package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.bothash.admissionservice.entity.MiscPayment;

public interface MiscPaymentRepository extends JpaRepository<MiscPayment, Long>, JpaSpecificationExecutor<MiscPayment> {
    List<MiscPayment> findTop20ByOrderByPaymentDateDescCreatedAtDesc();
}
