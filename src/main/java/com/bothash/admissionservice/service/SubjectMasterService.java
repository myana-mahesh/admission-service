package com.bothash.admissionservice.service;

import java.util.List;

import com.bothash.admissionservice.dto.SubjectMasterDto;

public interface SubjectMasterService {
    List<SubjectMasterDto> listSubjects(Long courseId);
    SubjectMasterDto createSubject(Long courseId, String name);
    SubjectMasterDto updateSubject(Long subjectId, String name);
    void deleteSubject(Long subjectId);
}
