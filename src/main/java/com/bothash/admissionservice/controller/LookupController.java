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
  public ResponseEntity<List<DocumentTypeOptionDto>> listDocTypes(
      @RequestParam(name = "includeOthers", required = false, defaultValue = "false") boolean includeOthers) {
    List<DocumentTypeOptionDto> docTypes = lookupService.getAllDocumentTypes().stream()
        // Always exclude the legacy OTHERS<n> placeholder codes that the form
        // briefly created when "Add Other Document" was clicked but never named.
        .filter(dt -> dt.getCode() == null
            || !dt.getCode().toUpperCase().matches("OTHERS\\d+"))
        .filter(dt -> {
            if (includeOthers) {
                // Caller wants the full directory including custom (non-main) docs
                // promoted from past "Other Document" uploads.
                return dt.getCode() == null
                    || !dt.getCode().toUpperCase().contains("OTHER")
                    || !Boolean.TRUE.equals(dt.getIsMainDoc());
            }
            // Default: curated main docs only.
            return Boolean.TRUE.equals(dt.getIsMainDoc())
                && (dt.getCode() == null || !dt.getCode().toUpperCase().contains("OTHER"));
        })
        .map(dt -> DocumentTypeOptionDto.builder()
            .id(dt.getDocTypeId())
            .code(dt.getCode())
            .name(dt.getName())
            .build())
        .toList();
    return ResponseEntity.ok(docTypes);
  }
}
