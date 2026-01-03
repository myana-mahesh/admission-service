package com.bothash.admissionservice.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bothash.admissionservice.dto.CourseDocumentRequirementDto;
import com.bothash.admissionservice.dto.CourseDocumentRequirementRequest;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.CourseDocumentRequirement;
import com.bothash.admissionservice.entity.DocumentType;
import com.bothash.admissionservice.repository.CourseDocumentRequirementRepository;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.DocumentTypeRepository;
import com.bothash.admissionservice.service.CourseDocumentRequirementService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseDocumentRequirementServiceImpl implements CourseDocumentRequirementService {

    private final CourseDocumentRequirementRepository requirementRepository;
    private final CourseRepository courseRepository;
    private final DocumentTypeRepository documentTypeRepository;

    @Override
    public List<CourseDocumentRequirementDto> listAll() {
        return requirementRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public CourseDocumentRequirementDto create(CourseDocumentRequirementRequest request) {
        validateRequest(request);
        if (requirementRepository.existsByCourseCourseIdAndDocumentTypeDocTypeId(request.getCourseId(),
                request.getDocTypeId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document requirement already exists for course");
        }
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        DocumentType docType = documentTypeRepository.findById(request.getDocTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document type not found"));

        CourseDocumentRequirement requirement = CourseDocumentRequirement.builder()
                .course(course)
                .documentType(docType)
                .optional(request.isOptional())
                .build();
        return toDto(requirementRepository.save(requirement));
    }

    @Override
    public CourseDocumentRequirementDto update(Long id, CourseDocumentRequirementRequest request) {
        validateRequest(request);
        CourseDocumentRequirement existing = requirementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found"));

        if (!existing.getCourse().getCourseId().equals(request.getCourseId())
                || !existing.getDocumentType().getDocTypeId().equals(request.getDocTypeId())) {
            if (requirementRepository.existsByCourseCourseIdAndDocumentTypeDocTypeId(request.getCourseId(),
                    request.getDocTypeId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Document requirement already exists for course");
            }
        }

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        DocumentType docType = documentTypeRepository.findById(request.getDocTypeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document type not found"));

        existing.setCourse(course);
        existing.setDocumentType(docType);
        existing.setOptional(request.isOptional());
        return toDto(existing);
    }

    @Override
    public void delete(Long id) {
        if (!requirementRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found");
        }
        requirementRepository.deleteById(id);
    }

    private void validateRequest(CourseDocumentRequirementRequest request) {
        if (request == null || request.getCourseId() == null || request.getDocTypeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course and document type are required");
        }
    }

    private CourseDocumentRequirementDto toDto(CourseDocumentRequirement requirement) {
        return CourseDocumentRequirementDto.builder()
                .id(requirement.getId())
                .courseId(requirement.getCourse().getCourseId())
                .courseName(requirement.getCourse().getName())
                .docTypeId(requirement.getDocumentType().getDocTypeId())
                .docTypeCode(requirement.getDocumentType().getCode())
                .docTypeName(requirement.getDocumentType().getName())
                .optional(requirement.isOptional())
                .build();
    }
}
