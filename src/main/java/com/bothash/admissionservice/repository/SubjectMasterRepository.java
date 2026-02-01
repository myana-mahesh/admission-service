package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.SubjectMaster;

public interface SubjectMasterRepository extends JpaRepository<SubjectMaster, Long> {
    List<SubjectMaster> findByCourseCourseIdOrderByNameAsc(Long courseId);
}
