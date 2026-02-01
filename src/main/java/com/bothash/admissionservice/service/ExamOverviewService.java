package com.bothash.admissionservice.service;

import java.util.List;

import com.bothash.admissionservice.dto.ExamMarksRequest;
import com.bothash.admissionservice.dto.ExamOverviewDto;

public interface ExamOverviewService {
    ExamOverviewDto getOverview(Long examId, Long collegeId, List<Long> branchIds, List<String> batchCodes, String query,
                                String status, Boolean absentOnly, int page, int size);
    void saveMarks(Long examId, ExamMarksRequest request);
}
