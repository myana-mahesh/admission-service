package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.StudentOtherPaymentValue;

public interface StudentOtherPaymentValueRepository extends JpaRepository<StudentOtherPaymentValue, Long> {
    List<StudentOtherPaymentValue> findByStudentStudentId(Long studentId);
    void deleteByStudentStudentIdAndField_Id(Long studentId, Long fieldId);
}
