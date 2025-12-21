package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.College;

public interface CollegeRepository extends JpaRepository<College, Long> {
    boolean existsByCode(String code);

    boolean existsByCodeAndCollegeIdNot(String code, Long collegeId);
}
