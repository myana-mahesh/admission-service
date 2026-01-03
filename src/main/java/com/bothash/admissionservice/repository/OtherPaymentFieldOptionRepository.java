package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.OtherPaymentFieldOption;

public interface OtherPaymentFieldOptionRepository extends JpaRepository<OtherPaymentFieldOption, Long> {
    List<OtherPaymentFieldOption> findByField_IdAndActiveTrueOrderBySortOrderAscLabelAsc(Long fieldId);
    List<OtherPaymentFieldOption> findByField_IdOrderBySortOrderAscLabelAsc(Long fieldId);
}
