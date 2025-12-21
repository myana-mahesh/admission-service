package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.CollegeCourse;

public interface CollegeCourseRepository extends JpaRepository<CollegeCourse, Long> {
    List<CollegeCourse> findByCollegeCollegeId(Long collegeId);
}
