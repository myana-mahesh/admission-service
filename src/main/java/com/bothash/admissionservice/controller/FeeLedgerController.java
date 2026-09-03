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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.FeeLedgerPaymentResponseDto;
import com.bothash.admissionservice.dto.FeeLedgerResponseDto;
import com.bothash.admissionservice.entity.TelecallerAssignment;
import com.bothash.admissionservice.service.FeeLedgerService;
import com.bothash.admissionservice.service.TelecallerAssignmentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fees")
@RequiredArgsConstructor
public class FeeLedgerController {

    private final FeeLedgerService feeLedgerService;
    private final TelecallerAssignmentService telecallerAssignmentService;

    /**
     * Loads the active telecaller rules for the caller if the UI signalled that the
     * request is being made in a TELECALLER scope. Returns {@code null} when the
     * header is absent (unrestricted query for HO/SUPER_ADMIN/etc.), and an empty
     * list when the header is present but no rules exist (scope active — the
     * service will block every row so an unassigned telecaller sees nothing).
     */
    private List<TelecallerAssignment> telecallerRules(String telecallerUserId) {
        if (telecallerUserId == null || telecallerUserId.isBlank()) {
            return null;
        }
        return telecallerAssignmentService.findActiveEntitiesForTelecaller(telecallerUserId);
    }

    @GetMapping("/ledger")
    public ResponseEntity<FeeLedgerResponseDto> ledger(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String branchIds,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String courseIds,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) String batchCodes,
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DUE") String dateType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dueStatus,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String verification,
            @RequestParam(required = false) String proofAttached,
            @RequestParam(required = false) String txnPresent,
            @RequestParam(required = false) String paidAmountOp,
            @RequestParam(required = false) BigDecimal paidAmount,
            @RequestParam(required = false) BigDecimal pendingMin,
            @RequestParam(required = false) BigDecimal pendingMax,
            @RequestParam(required = false) Boolean branchApprovedOnly,
            @RequestHeader(value = "X-Telecaller-User-Id", required = false) String telecallerUserId
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "dueDate"));

        List<String> statusList = splitCsv(status);
        List<String> paymentModes = splitCsv(paymentMode);
        List<String> paymentTypes = splitCsv(paymentType);
        List<Long> branchIdList = splitLongCsv(branchIds);
        if (branchId != null) {
            branchIdList = List.of(branchId);
        }
        List<Long> courseIdList = splitLongCsv(courseIds);
        if (courseId != null) {
            courseIdList = List.of(courseId);
        }
        List<String> batchCodeList = splitCsv(batchCodes);
        if (batch != null && !batch.isBlank()) {
            batchCodeList = List.of();
        }

        final List<Long> finalBranchIdList = branchIdList;
        final List<Long> finalCourseIdList = courseIdList;
        final List<String> finalBatchCodeList = batchCodeList;
        FeeLedgerResponseDto response = feeLedgerService.runInTelecallerScope(
                telecallerRules(telecallerUserId),
                () -> feeLedgerService.search(
                        q, finalBranchIdList, finalCourseIdList, batch, finalBatchCodeList, academicYearId,
                        startDate, endDate, dateType,
                        statusList, dueStatus, paymentModes, paymentTypes,
                        verification, proofAttached, txnPresent,
                        paidAmountOp, paidAmount,
                        pendingMin, pendingMax, branchApprovedOnly, pageable
                )
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ledger/other-payments")
    public ResponseEntity<java.util.List<com.bothash.admissionservice.dto.LedgerOtherPaymentRowDto>> otherPaymentLedger(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String branchIds,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String courseIds,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) String batchCodes,
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) Boolean branchApprovedOnly,
            @RequestHeader(value = "X-Telecaller-User-Id", required = false) String telecallerUserId
    ) {
        List<String> paymentModes = splitCsv(paymentMode);
        List<String> paymentTypes = splitCsv(paymentType);
        List<Long> branchIdList = splitLongCsv(branchIds);
        if (branchId != null) {
            branchIdList = List.of(branchId);
        }
        List<Long> courseIdList = splitLongCsv(courseIds);
        if (courseId != null) {
            courseIdList = List.of(courseId);
        }
        List<String> batchCodeList = splitCsv(batchCodes);
        if (batch != null && !batch.isBlank()) {
            batchCodeList = List.of();
        }
        final List<Long> finalBranchIdList = branchIdList;
        final List<Long> finalCourseIdList = courseIdList;
        final List<String> finalBatchCodeList = batchCodeList;
        return ResponseEntity.ok(feeLedgerService.runInTelecallerScope(
                telecallerRules(telecallerUserId),
                () -> feeLedgerService.searchOtherPayments(
                        q, finalBranchIdList, finalCourseIdList, batch, finalBatchCodeList, academicYearId,
                        startDate, endDate, paymentModes, paymentTypes, branchApprovedOnly
                )
        ));
    }

    @GetMapping("/ledger/payments")
    public ResponseEntity<FeeLedgerPaymentResponseDto> paymentLedger(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String branchIds,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String courseIds,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) String batchCodes,
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DUE") String dateType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dueStatus,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String verification,
            @RequestParam(required = false) String proofAttached,
            @RequestParam(required = false) String txnPresent,
            @RequestParam(required = false) String paidAmountOp,
            @RequestParam(required = false) BigDecimal paidAmount,
            @RequestParam(required = false) BigDecimal pendingMin,
            @RequestParam(required = false) BigDecimal pendingMax,
            @RequestParam(required = false) Boolean branchApprovedOnly,
            @RequestHeader(value = "X-Telecaller-User-Id", required = false) String telecallerUserId
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "paidOn"));

        List<String> statusList = splitCsv(status);
        List<String> paymentModes = splitCsv(paymentMode);
        List<String> paymentTypes = splitCsv(paymentType);
        List<Long> branchIdList = splitLongCsv(branchIds);
        if (branchId != null) {
            branchIdList = List.of(branchId);
        }
        List<Long> courseIdList = splitLongCsv(courseIds);
        if (courseId != null) {
            courseIdList = List.of(courseId);
        }
        List<String> batchCodeList = splitCsv(batchCodes);
        if (batch != null && !batch.isBlank()) {
            batchCodeList = List.of();
        }

        final List<Long> finalBranchIdList = branchIdList;
        final List<Long> finalCourseIdList = courseIdList;
        final List<String> finalBatchCodeList = batchCodeList;
        FeeLedgerPaymentResponseDto response = feeLedgerService.runInTelecallerScope(
                telecallerRules(telecallerUserId),
                () -> feeLedgerService.searchPayments(
                        q, finalBranchIdList, finalCourseIdList, batch, finalBatchCodeList, academicYearId,
                        startDate, endDate, dateType,
                        statusList, dueStatus, paymentModes, paymentTypes,
                        verification, proofAttached, txnPresent,
                        paidAmountOp, paidAmount,
                        pendingMin, pendingMax, branchApprovedOnly, pageable
                )
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
