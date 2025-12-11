package com.bothash.admissionservice.service;

import java.util.List;

import com.bothash.admissionservice.entity.AcademicYear;
import com.bothash.admissionservice.entity.Caste;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.DocumentType;

public interface LookupService {
	Course getOrCreateCourse(String code, String name);

	AcademicYear getOrCreateYear(String label);

	DocumentType getOrCreateDocType(String code, String name);
	
	List<Course> getAllCourses();
    List<AcademicYear> getAllYears();
    List<DocumentType> getAllDocumentTypes();
    List<Caste> getAllCastes();
}
