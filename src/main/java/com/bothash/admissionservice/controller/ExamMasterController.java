package com.bothash.admissionservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.ExamMasterDto;
import com.bothash.admissionservice.dto.ExamMasterRequest;
import com.bothash.admissionservice.service.ExamMasterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamMasterController {

    private final ExamMasterService examService;

    @GetMapping
    public ResponseEntity<List<ExamMasterDto>> list(@RequestParam(name = "courseId", required = false) Long courseId) {
        return ResponseEntity.ok(examService.listExams(courseId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamMasterDto> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getExam(id));
    }

    @PostMapping
    public ResponseEntity<ExamMasterDto> create(@RequestBody ExamMasterRequest request) {
        return ResponseEntity.ok(examService.createExam(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExamMasterDto> update(@PathVariable Long id, @RequestBody ExamMasterRequest request) {
        return ResponseEntity.ok(examService.updateExam(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        examService.deleteExam(id);
        return ResponseEntity.noContent().build();
    }
}
