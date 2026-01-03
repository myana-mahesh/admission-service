package com.bothash.admissionservice.service;

import java.util.List;

import com.bothash.admissionservice.dto.CourseDocumentRequirementDto;
import com.bothash.admissionservice.dto.CourseDocumentRequirementRequest;

public interface CourseDocumentRequirementService {
    List<CourseDocumentRequirementDto> listAll();

    CourseDocumentRequirementDto create(CourseDocumentRequirementRequest request);

    CourseDocumentRequirementDto update(Long id, CourseDocumentRequirementRequest request);

    void delete(Long id);
}
