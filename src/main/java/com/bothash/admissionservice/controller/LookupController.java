package com.bothash.admissionservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.bothash.admissionservice.dto.LookupCreateRequest;
import com.bothash.admissionservice.entity.AcademicYear;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.DocumentType;
import com.bothash.admissionservice.service.LookupService;

@RestController
@RequestMapping("/api/lookup")
@RequiredArgsConstructor
public class LookupController {
  private final LookupService lookupService;

  @PostMapping("/courses")
  public ResponseEntity<Course> addCourse( @RequestBody LookupCreateRequest req){
    return ResponseEntity.ok(lookupService.getOrCreateCourse(req.getCode(), req.getName()));
  }

  @PostMapping("/years")
  public ResponseEntity<AcademicYear> addYear( @RequestBody LookupCreateRequest req){
    // here req.code carries label like 2025-26
    return ResponseEntity.ok(lookupService.getOrCreateYear(req.getCode()));
  }

  @PostMapping("/doc-types")
  public ResponseEntity<DocumentType> addDocType( @RequestBody LookupCreateRequest req){
    return ResponseEntity.ok(lookupService.getOrCreateDocType(req.getCode(), req.getName()));
  }
}
