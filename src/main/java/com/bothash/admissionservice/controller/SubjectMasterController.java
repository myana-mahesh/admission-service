package com.bothash.admissionservice.controller;

import java.util.List;
import java.util.Map;

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

import com.bothash.admissionservice.dto.SubjectMasterDto;
import com.bothash.admissionservice.service.SubjectMasterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectMasterController {

    private final SubjectMasterService subjectService;

    @GetMapping
    public ResponseEntity<List<SubjectMasterDto>> list(@RequestParam(name = "courseId", required = false) Long courseId) {
        return ResponseEntity.ok(subjectService.listSubjects(courseId));
    }

    @PostMapping
    public ResponseEntity<SubjectMasterDto> create(@RequestBody Map<String, String> body) {
        Long courseId = body.get("courseId") != null ? Long.valueOf(body.get("courseId")) : null;
        String name = body.get("name");
        return ResponseEntity.ok(subjectService.createSubject(courseId, name));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubjectMasterDto> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        return ResponseEntity.ok(subjectService.updateSubject(id, name));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }
}
