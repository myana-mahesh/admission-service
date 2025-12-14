package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.dto.AdmissionDTO;
import com.bothash.admissionservice.dto.CancelAdmissionDTO;
import com.bothash.admissionservice.entity.Admission;
import com.bothash.admissionservice.repository.Admission2Repository;
import com.bothash.admissionservice.service.Admission2Service;
import com.bothash.admissionservice.service.AdmissionCancellationService;
import com.bothash.admissionservice.service.AdmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admissions")
@RequiredArgsConstructor
public class AdmissionController {

    private final AdmissionService admissionService;
    private final Admission2Repository admission2Repository;
    private final AdmissionCancellationService cancellationService;

    @PostMapping
    public ResponseEntity<Admission> createAdmission(@RequestBody AdmissionDTO dto) {
        return ResponseEntity.ok(admissionService.createAdmission(dto));
    }


    @GetMapping
    public ResponseEntity<List<Admission>> getAll() {
        return ResponseEntity.ok(admissionService.getAllAdmissions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Admission> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(admissionService.getAdmissionById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Admission> update(@PathVariable Long id, @RequestBody AdmissionDTO dto) {
        return ResponseEntity.ok(admissionService.updateAdmission(id, dto));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        admissionService.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check-seat")
    public ResponseEntity<Boolean> checkSeat(@RequestParam String course, @RequestParam String branch) {
        return ResponseEntity.ok(admissionService.checkSeatAvailability(course, branch));
    }

    // ADMISSION CANCELLATION API
    @PostMapping("/cancel-admission")
    public ResponseEntity<?> confirmCancellation(@RequestBody CancelAdmissionDTO dto) {
        cancellationService.confirmCancellation(dto);
        return ResponseEntity.ok("Admission cancelled successfully");
    }

    @GetMapping("/fetch-cancel-admission-details/{admissionId}")
    public ResponseEntity<CancelAdmissionDTO> cancelAdmissionDetails(
            @PathVariable Long admissionId) {
        return ResponseEntity.ok(cancellationService.fetchCancelAdmDetails(admissionId));
    }

    @PutMapping("/cancel-admission-details-update")
    public ResponseEntity<CancelAdmissionDTO> updateCancelAdmissionDetails(
            @RequestBody CancelAdmissionDTO dto
    ) {

        CancelAdmissionDTO updated =
                cancellationService.updateCancelAdmissionDetails(dto);

        if (updated != null) {
            return ResponseEntity.ok(updated);
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build();
    }
}
