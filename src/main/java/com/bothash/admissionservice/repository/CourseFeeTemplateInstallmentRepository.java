package com.bothash.admissionservice.repository;

import com.bothash.admissionservice.entity.CourseFeeTemplateInstallment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseFeeTemplateInstallmentRepository
        extends JpaRepository<CourseFeeTemplateInstallment, Long> {
}
