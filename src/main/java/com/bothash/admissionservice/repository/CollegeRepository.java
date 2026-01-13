package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import com.bothash.admissionservice.entity.College;

public interface CollegeRepository extends JpaRepository<College, Long> {
    boolean existsByCode(String code);

    boolean existsByCodeAndCollegeIdNot(String code, Long collegeId);

    Optional<College> findByCodeIgnoreCase(String code);
    Optional<College> findByNameIgnoreCase(String name);
}
