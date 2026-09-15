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

import com.bothash.admissionservice.dto.AdmissionSectionDto;
import com.bothash.admissionservice.service.AdmissionSectionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admission-sections")
@RequiredArgsConstructor
public class AdmissionSectionController {

    private final AdmissionSectionService service;

    @GetMapping
    public List<AdmissionSectionDto> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @PostMapping
    public AdmissionSectionDto create(@RequestBody AdmissionSectionDto req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public AdmissionSectionDto update(@PathVariable Long id, @RequestBody AdmissionSectionDto req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
