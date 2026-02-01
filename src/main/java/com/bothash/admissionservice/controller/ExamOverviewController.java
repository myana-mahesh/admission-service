package com.bothash.admissionservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.ExamMarksRequest;
import com.bothash.admissionservice.dto.ExamOverviewDto;
import com.bothash.admissionservice.service.ExamOverviewService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamOverviewController {

    private final ExamOverviewService overviewService;

    @GetMapping("/{examId}/overview")
    public ResponseEntity<ExamOverviewDto> getOverview(@PathVariable Long examId,
                                                       @RequestParam(required = false) Long collegeId,
                                                       @RequestParam(required = false) java.util.List<Long> branchIds,
                                                       @RequestParam(required = false) java.util.List<String> batchCodes,
                                                       @RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Boolean absentOnly,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(overviewService.getOverview(
                examId, collegeId, branchIds, batchCodes, q, status, absentOnly, page, size));
    }

    @PostMapping("/{examId}/marks")
    public ResponseEntity<Void> saveMarks(@PathVariable Long examId, @RequestBody ExamMarksRequest request) {
        overviewService.saveMarks(examId, request);
        return ResponseEntity.ok().build();
    }
}
