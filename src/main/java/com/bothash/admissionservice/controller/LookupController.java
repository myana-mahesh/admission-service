package com.bothash.admissionservice.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.bothash.admissionservice.dto.LookupCreateRequest;
import com.bothash.admissionservice.dto.DocumentTypeOptionDto;
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

  @GetMapping("/doc-types")
  public ResponseEntity<List<DocumentTypeOptionDto>> listDocTypes() {
    List<DocumentTypeOptionDto> docTypes = lookupService.getAllDocumentTypes().stream()
        .filter(dt -> Boolean.TRUE.equals(dt.getIsMainDoc()))
        .filter(dt -> dt.getCode() == null || !dt.getCode().toUpperCase().contains("OTHER"))
        .map(dt -> DocumentTypeOptionDto.builder()
            .id(dt.getDocTypeId())
            .code(dt.getCode())
            .name(dt.getName())
            .build())
        .toList();
    return ResponseEntity.ok(docTypes);
  }
}
