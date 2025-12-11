package com.bothash.admissionservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.dto.PaymentModeDto;
import com.bothash.admissionservice.entity.PaymentModeMaster;

public interface PaymentModeRepository extends JpaRepository<PaymentModeMaster, Long> {

    List<PaymentModeMaster> findByActiveTrueOrderByDisplayOrderAsc();

    PaymentModeMaster findByCodeAndActiveTrueOrderByDisplayOrderAsc(String mode);
    
    Optional<PaymentModeMaster> findByCode(String code);

    boolean existsByCodeAndPaymentModeIdNot(String code, Long id);

    boolean existsByCode(String code);
    
    PaymentModeMaster  findByPaymentModeId(Long id);
}
