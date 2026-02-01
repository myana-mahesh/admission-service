package com.bothash.admissionservice.service;

import java.util.List;

import com.bothash.admissionservice.dto.ExamMasterDto;
import com.bothash.admissionservice.dto.ExamMasterRequest;

public interface ExamMasterService {
    List<ExamMasterDto> listExams(Long courseId);
    ExamMasterDto getExam(Long examId);
    ExamMasterDto createExam(ExamMasterRequest request);
    ExamMasterDto updateExam(Long examId, ExamMasterRequest request);
    void deleteExam(Long examId);
}
