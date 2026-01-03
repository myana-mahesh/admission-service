package com.bothash.admissionservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.CourseDocumentRequirement;

public interface CourseDocumentRequirementRepository extends JpaRepository<CourseDocumentRequirement, Long> {
    boolean existsByCourseCourseIdAndDocumentTypeDocTypeId(Long courseId, Long docTypeId);

    Optional<CourseDocumentRequirement> findByCourseCourseIdAndDocumentTypeDocTypeId(Long courseId, Long docTypeId);
}
