package com.bothash.admissionservice.controller;

import java.util.Map;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bothash.admissionservice.service.impl.FeeInstallmentServiceImpl;


@RestController
@RequestMapping("/api/fee-installments")
@RequiredArgsConstructor
public class FeeInstallmentController {

    private final FeeInstallmentServiceImpl service;

    // DELETE /api/fee-installments/{id}?deleteFilesAlso=true
    @DeleteMapping("/{installmentId}")
    public ResponseEntity<?> deleteInstallment(
            @PathVariable Long installmentId,
            @RequestParam(defaultValue = "false") boolean deleteFilesAlso
    ) {
        service.deleteInstallment(installmentId, deleteFilesAlso);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Installment deleted successfully",
                "installmentId", installmentId,
                "deleteFilesAlso", deleteFilesAlso
        ));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> notFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("success", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> badRequest(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
    }
}
