package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.ExamMaster;

public interface ExamMasterRepository extends JpaRepository<ExamMaster, Long> {
    List<ExamMaster> findByCourseCourseIdOrderByExamNameAsc(Long courseId);
}
