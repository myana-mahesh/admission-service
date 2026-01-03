package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.OtherPaymentField;

public interface OtherPaymentFieldRepository extends JpaRepository<OtherPaymentField, Long> {
    List<OtherPaymentField> findByActiveTrueOrderBySortOrderAscLabelAsc();
    List<OtherPaymentField> findAllByOrderBySortOrderAscLabelAsc();
}
