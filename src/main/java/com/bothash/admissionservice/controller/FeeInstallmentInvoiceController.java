package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import com.bothash.admissionservice.service.impl.FeeInstallmentServiceImpl;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fee-installments")
@RequiredArgsConstructor
public class FeeInstallmentInvoiceController {

    private final FeeInstallmentServiceImpl feeInstallmentService;
    private final FeeInvoiceRepository invoiceRepo;

    @Data
    public static class StatusUpdateRequest {
        private String status; // "Paid" / "Un Paid"
    }

    @Data
    public static class InvoiceResponse {
        private Long installmentId;
        private String invoiceNumber;
        private String downloadUrl;
        private String status;
    }

    @PostMapping("/{installmentId}/status")
    public ResponseEntity<InvoiceResponse> updateStatus(
            @PathVariable Long installmentId,
            @RequestBody StatusUpdateRequest req
    ) {
        FeeInstallment inst =
                feeInstallmentService.updateStatus(installmentId, req.getStatus());

        InvoiceResponse resp = new InvoiceResponse();
        resp.setInstallmentId(inst.getInstallmentId());
        resp.setStatus(inst.getStatus());

        // If Paid, return invoice info (there should now be one)
        if ("Paid".equalsIgnoreCase(inst.getStatus())) {
            FeeInvoice inv = invoiceRepo.findByInstallment_InstallmentId(installmentId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (inv != null) {
                resp.setInvoiceNumber(inv.getInvoiceNumber());
                resp.setDownloadUrl(inv.getDownloadUrl());
            }
        }

        return ResponseEntity.ok(resp);
    }
}
