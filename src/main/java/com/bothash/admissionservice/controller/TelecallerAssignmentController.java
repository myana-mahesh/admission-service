package com.bothash.admissionservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.TelecallerAssignmentDto;
import com.bothash.admissionservice.dto.TelecallerAssignmentRequest;
import com.bothash.admissionservice.service.TelecallerAssignmentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/telecaller-assignments")
@RequiredArgsConstructor
public class TelecallerAssignmentController {

    private final TelecallerAssignmentService service;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody TelecallerAssignmentRequest request,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        try {
            TelecallerAssignmentDto created = service.create(request, actor);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<TelecallerAssignmentDto>> list(
            @RequestParam(value = "active", required = false) Boolean activeOnly
    ) {
        return ResponseEntity.ok(service.listAll(activeOnly));
    }

    @GetMapping("/active")
    public ResponseEntity<List<TelecallerAssignmentDto>> listActiveForTelecaller(
            @RequestParam("telecallerUserId") String telecallerUserId
    ) {
        return ResponseEntity.ok(service.listActiveForTelecaller(telecallerUserId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        try {
            service.deactivate(id, actor);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
