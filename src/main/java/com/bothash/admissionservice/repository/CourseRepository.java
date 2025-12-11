package com.bothash.admissionservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.Course;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
	  Optional<Course> findByCode(String code);

}
