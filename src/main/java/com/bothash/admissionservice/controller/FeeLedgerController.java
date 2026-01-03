package com.bothash.admissionservice.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.FeeLedgerResponseDto;
import com.bothash.admissionservice.service.FeeLedgerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fees")
@RequiredArgsConstructor
public class FeeLedgerController {

    private final FeeLedgerService feeLedgerService;

    @GetMapping("/ledger")
    public ResponseEntity<FeeLedgerResponseDto> ledger(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String branchIds,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DUE") String dateType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dueStatus,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String verification,
            @RequestParam(required = false) String proofAttached,
            @RequestParam(required = false) String txnPresent,
            @RequestParam(required = false) String paidAmountOp,
            @RequestParam(required = false) BigDecimal paidAmount,
            @RequestParam(required = false) BigDecimal pendingMin,
            @RequestParam(required = false) BigDecimal pendingMax
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "dueDate"));

        List<String> statusList = splitCsv(status);
        List<String> paymentModes = splitCsv(paymentMode);
        List<Long> branchIdList = splitLongCsv(branchIds);
        if (branchId != null) {
            branchIdList = List.of(branchId);
        }

        FeeLedgerResponseDto response = feeLedgerService.search(
                q, branchIdList, courseId, batch, academicYearId,
                startDate, endDate, dateType,
                statusList, dueStatus, paymentModes,
                verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount,
                pendingMin, pendingMax, pageable
        );

        return ResponseEntity.ok(response);
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<Long> splitLongCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseLongOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Long parseLongOrNull(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
