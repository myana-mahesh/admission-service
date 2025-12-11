package com.bothash.admissionservice.service.impl;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bothash.admissionservice.entity.AcademicYear;
import com.bothash.admissionservice.entity.Caste;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.DocumentType;
import com.bothash.admissionservice.repository.AcademicYearRepository;
import com.bothash.admissionservice.repository.CasteRepository;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.DocumentTypeRepository;
import com.bothash.admissionservice.service.LookupService;

@Service
@RequiredArgsConstructor
@Transactional
public class LookupServiceImpl implements LookupService {
	private final CourseRepository courseRepo;
	private final AcademicYearRepository yearRepo;
	private final DocumentTypeRepository docTypeRepo;
	private final DocumentTypeRepository docRepo;
	private final CasteRepository casteRepo;

	@Override
	public Course getOrCreateCourse(String code, String name) {
		return courseRepo.findByCode(code)
				.orElseGet(() -> courseRepo.save(Course.builder().code(code).name(name).build()));
	}

	@Override
	public AcademicYear getOrCreateYear(String label) {
		return yearRepo.findByLabel(label).orElseGet(() -> yearRepo.save(AcademicYear.builder().label(label).build()));
	}

	@Override
	public DocumentType getOrCreateDocType(String code, String name) {
		return docTypeRepo.findByCode(code)
				.orElseGet(() -> docTypeRepo.save(DocumentType.builder().code(code).name(name).build()));
	}

	@Override
	public List<Course> getAllCourses() {
		return courseRepo.findAll();
	}

	@Override
	public List<AcademicYear> getAllYears() {
		return yearRepo.findAll();
	}

	@Override
	public List<DocumentType> getAllDocumentTypes() {
		return docRepo.findAll();
	}

	@Override
	public List<Caste> getAllCastes() {
		return casteRepo.findAll();
	}
}
