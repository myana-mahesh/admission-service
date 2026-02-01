package com.bothash.admissionservice.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.dto.SubjectMasterDto;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.SubjectMaster;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.SubjectMasterRepository;
import com.bothash.admissionservice.service.SubjectMasterService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubjectMasterServiceImpl implements SubjectMasterService {

    private final SubjectMasterRepository subjectRepository;
    private final CourseRepository courseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SubjectMasterDto> listSubjects(Long courseId) {
        if (courseId == null) {
            return Collections.emptyList();
        }
        return subjectRepository.findByCourseCourseIdOrderByNameAsc(courseId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubjectMasterDto createSubject(Long courseId, String name) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Subject name is required");
        }
        SubjectMaster subject = new SubjectMaster();
        subject.setCourse(course);
        subject.setName(name.trim());
        return toDto(subjectRepository.save(subject));
    }

    @Override
    @Transactional
    public SubjectMasterDto updateSubject(Long subjectId, String name) {
        SubjectMaster subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Subject name is required");
        }
        subject.setName(name.trim());
        return toDto(subjectRepository.save(subject));
    }

    @Override
    @Transactional
    public void deleteSubject(Long subjectId) {
        subjectRepository.deleteById(subjectId);
    }

    private SubjectMasterDto toDto(SubjectMaster subject) {
        SubjectMasterDto dto = new SubjectMasterDto();
        dto.setSubjectId(subject.getSubjectId());
        if (subject.getCourse() != null) {
            dto.setCourseId(subject.getCourse().getCourseId());
            dto.setCourseName(subject.getCourse().getName());
        }
        dto.setName(subject.getName());
        return dto;
    }
}
