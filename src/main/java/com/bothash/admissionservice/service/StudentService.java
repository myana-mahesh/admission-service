package com.bothash.admissionservice.service;

import java.util.Optional;

import com.bothash.admissionservice.enumpackage.Gender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bothash.admissionservice.dto.StudentDto;
import com.bothash.admissionservice.entity.Student;

public interface StudentService {
	Student createOrUpdateStudent(Student s);
	Optional<Student> getById(Long id);
	Optional<Student> getByAbsId(String absId);
	Student getByAbsIdorPhoneNumber(String absId, String mobile);
	Student getPhoneNumber(String absId, String mobile);
	Page<Student> getStudents(String q, Pageable pageable);
	StudentDto toDto(Student s);
	Page<Student> getStudents(
			String q,
			Long course,
			String batch,
			Integer year,
			Gender gender,
			Pageable pageable
	);

}
