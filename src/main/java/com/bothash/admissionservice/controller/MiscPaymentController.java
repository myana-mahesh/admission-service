package com.bothash.admissionservice.controller;

import java.util.List;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.MiscPaymentDto;
import com.bothash.admissionservice.dto.MiscPaymentPageResponse;
import com.bothash.admissionservice.dto.MiscPaymentRequest;
import com.bothash.admissionservice.service.impl.MiscPaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/misc-payments")
@RequiredArgsConstructor
public class MiscPaymentController {

    private final MiscPaymentService miscPaymentService;

    @GetMapping
    public List<MiscPaymentDto> listRecent() {
        return miscPaymentService.listRecent();
    }

    @GetMapping("/search")
    public MiscPaymentPageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return miscPaymentService.search(q, courseId, batch, feeType, paymentMode, startDate, endDate, page, size);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody MiscPaymentRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(miscPaymentService.create(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PutMapping("/{paymentId}")
    public ResponseEntity<?> update(@PathVariable Long paymentId, @RequestBody MiscPaymentRequest request) {
        try {
            return ResponseEntity.ok(miscPaymentService.update(paymentId, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @DeleteMapping("/{paymentId}")
    public ResponseEntity<?> delete(@PathVariable Long paymentId) {
        try {
            miscPaymentService.delete(paymentId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
