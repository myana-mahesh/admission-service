package com.bothash.admissionservice.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.bothash.admissionservice.dto.BranchCashbookDayDto;
import com.bothash.admissionservice.dto.BranchCashbookDayUpsertRequest;
import com.bothash.admissionservice.dto.BranchCashbookExpenseDto;
import com.bothash.admissionservice.dto.BranchCashbookExpenseRequest;
import com.bothash.admissionservice.dto.BranchCashbookExpenseUpdateRequest;
import com.bothash.admissionservice.dto.BranchPettyCashAddRequest;
import com.bothash.admissionservice.dto.BranchPettyCashInitialRequest;
import com.bothash.admissionservice.dto.BranchPettyCashReturnRequest;
import com.bothash.admissionservice.dto.BranchRemittanceDetailDto;
import com.bothash.admissionservice.dto.BranchRemittanceHistoryDto;
import com.bothash.admissionservice.dto.BranchRemittancesGroupDto;
import com.bothash.admissionservice.dto.BranchCollectionDashboardDto;
import com.bothash.admissionservice.dto.PagedResponse;
import com.bothash.admissionservice.service.BranchCollectionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/branch-collections")
@RequiredArgsConstructor
public class BranchCollectionController {

    private final BranchCollectionService branchCollectionService;

    @GetMapping("/daily")
    public ResponseEntity<BranchCollectionDashboardDto> getDaily(
            @RequestParam Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(branchCollectionService.getDaily(branchId, date));
    }

    @PostMapping("/daily")
    public ResponseEntity<BranchCashbookDayDto> upsertDaily(@RequestBody BranchCashbookDayUpsertRequest request) {
        return ResponseEntity.ok(branchCollectionService.upsertDaily(request));
    }

    @PostMapping("/expenses")
    public ResponseEntity<BranchCashbookExpenseDto> addExpense(@RequestBody BranchCashbookExpenseRequest request) {
        return ResponseEntity.ok(branchCollectionService.addExpense(request));
    }

    @PutMapping("/expenses/{expenseId}")
    public ResponseEntity<BranchCashbookExpenseDto> updateExpense(
            @PathVariable Long expenseId,
            @RequestBody BranchCashbookExpenseUpdateRequest request
    ) {
        return ResponseEntity.ok(branchCollectionService.updateExpense(expenseId, request));
    }

    @DeleteMapping("/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable Long expenseId,
            @RequestParam Long branchId
    ) {
        branchCollectionService.deleteExpense(expenseId, branchId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/petty-cash")
    public ResponseEntity<BranchCashbookExpenseDto> addPettyCash(@RequestBody BranchPettyCashAddRequest request) {
        return ResponseEntity.ok(branchCollectionService.addPettyCash(request));
    }

    @PostMapping("/petty-cash/return")
    public ResponseEntity<BranchCashbookExpenseDto> returnPettyCash(@RequestBody BranchPettyCashReturnRequest request) {
        return ResponseEntity.ok(branchCollectionService.returnPettyCashToCollection(request));
    }

    @PostMapping("/petty-cash/initial")
    public ResponseEntity<BranchCashbookExpenseDto> addInitialPettyCash(@RequestBody BranchPettyCashInitialRequest request) {
        return ResponseEntity.ok(branchCollectionService.addInitialPettyCash(request));
    }

    @GetMapping("/history")
    public ResponseEntity<PagedResponse<BranchRemittanceHistoryDto>> remittanceHistory(
            @RequestParam Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(branchCollectionService.getRemittanceHistory(branchId, page, size));
    }

    @GetMapping("/history/{remittanceId}/detail")
    public ResponseEntity<BranchRemittanceDetailDto> remittanceDetail(
            @PathVariable Long remittanceId,
            @RequestParam Long branchId
    ) {
        return ResponseEntity.ok(branchCollectionService.getRemittanceDetail(branchId, remittanceId));
    }

    @GetMapping("/ho-remittances")
    public ResponseEntity<java.util.List<BranchRemittancesGroupDto>> hoRemittances(
            @RequestParam java.util.List<Long> branchIds,
            @RequestParam(defaultValue = "200") int perBranchLimit
    ) {
        return ResponseEntity.ok(branchCollectionService.getHoRemittances(branchIds, perBranchLimit));
    }

    @PostMapping("/remittances/{remittanceId}/accept")
    public ResponseEntity<BranchRemittanceDetailDto> acceptRemittance(
            @PathVariable Long remittanceId,
            @RequestBody RemittanceHandleRequest body,
            @RequestParam(required = false) String actor
    ) {
        return ResponseEntity.ok(branchCollectionService.acceptRemittance(
                remittanceId,
                body != null ? body.handlerName() : null,
                body != null ? body.handlerRemark() : null,
                actor));
    }

    @PostMapping("/remittances/{remittanceId}/reject")
    public ResponseEntity<BranchRemittanceDetailDto> rejectRemittance(
            @PathVariable Long remittanceId,
            @RequestBody RemittanceHandleRequest body,
            @RequestParam(required = false) String actor
    ) {
        return ResponseEntity.ok(branchCollectionService.rejectRemittance(
                remittanceId,
                body != null ? body.handlerName() : null,
                body != null ? body.handlerRemark() : null,
                actor));
    }

    @GetMapping("/rejected-remittances")
    public ResponseEntity<java.util.List<BranchRemittanceHistoryDto>> rejectedRemittances(
            @RequestParam Long branchId,
            @RequestParam(required = false) String currentUser
    ) {
        return ResponseEntity.ok(
                branchCollectionService.listRejectedSinceLastResubmit(branchId, currentUser));
    }

    @PostMapping("/rejected-remittances/{remittanceId}/acknowledge")
    public ResponseEntity<?> acknowledgeRejected(
            @PathVariable Long remittanceId,
            @RequestParam String currentUser
    ) {
        try {
            branchCollectionService.acknowledgeRejectedRemittance(remittanceId, currentUser);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    public record RemittanceHandleRequest(String handlerName, String handlerRemark) {}
}
