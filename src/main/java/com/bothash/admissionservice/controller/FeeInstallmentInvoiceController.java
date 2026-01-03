package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.entity.FileUpload;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import com.bothash.admissionservice.repository.FeeInstallmentPaymentRepository;
import com.bothash.admissionservice.repository.FileUploadRepository;
import com.bothash.admissionservice.service.impl.FeeInstallmentServiceImpl;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fee-installments")
@RequiredArgsConstructor
public class FeeInstallmentInvoiceController {

    private final FeeInstallmentServiceImpl feeInstallmentService;
    private final FeeInvoiceRepository invoiceRepo;
    private final FeeInstallmentPaymentRepository paymentRepo;
    private final FileUploadRepository uploadRepo;

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

    @Data
    public static class PaymentResponse {
        private Long paymentId;
        private String paymentMode;
        private String txnRef;
        private String receivedBy;
        private String status;
        private Boolean verified;
        private String verifiedBy;
        private java.time.LocalDateTime verifiedAt;
        private java.time.LocalDate paidOn;
        private java.math.BigDecimal amount;
        private String receiptUrl;
        private String receiptName;
        private String invoiceNumber;
        private String invoiceUrl;
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

    @GetMapping("/{installmentId}/payments")
    public ResponseEntity<List<PaymentResponse>> listPayments(@PathVariable Long installmentId) {
        List<FeeInstallmentPayment> payments =
                paymentRepo.findByInstallment_InstallmentIdOrderByCreatedAtAsc(installmentId);
        if (payments.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Long> paymentIds = payments.stream()
                .map(FeeInstallmentPayment::getPaymentId)
                .toList();
        List<FileUpload> uploads = uploadRepo.findByInstallmentPayment_PaymentIdIn(paymentIds);
        Map<Long, FileUpload> uploadMap = uploads.stream()
                .filter(u -> u.getInstallmentPayment() != null)
                .collect(Collectors.toMap(
                        u -> u.getInstallmentPayment().getPaymentId(),
                        Function.identity(),
                        (a, b) -> a
                ));
        Map<Long, FeeInvoice> invoiceMap = invoiceRepo.findByPayment_PaymentIdIn(paymentIds).stream()
                .filter(inv -> inv.getPayment() != null)
                .collect(Collectors.toMap(
                        inv -> inv.getPayment().getPaymentId(),
                        Function.identity(),
                        (a, b) -> a
                ));

        List<PaymentResponse> out = new ArrayList<>(payments.size());
        for (FeeInstallmentPayment payment : payments) {
            PaymentResponse resp = new PaymentResponse();
            resp.setPaymentId(payment.getPaymentId());
            resp.setAmount(payment.getAmount());
            resp.setPaymentMode(payment.getPaymentMode() != null ? payment.getPaymentMode().getLabel() : null);
            resp.setTxnRef(payment.getTxnRef());
            resp.setReceivedBy(payment.getReceivedBy());
            resp.setStatus(payment.getStatus());
            resp.setVerified(payment.getIsVerified());
            resp.setVerifiedBy(payment.getVerifiedBy());
            resp.setVerifiedAt(payment.getVerifiedAt());
            resp.setPaidOn(payment.getPaidOn());
            FileUpload upload = uploadMap.get(payment.getPaymentId());
            if (upload != null) {
                resp.setReceiptUrl(upload.getStorageUrl());
                resp.setReceiptName(upload.getFilename());
            }
            FeeInvoice invoice = invoiceMap.get(payment.getPaymentId());
            if (invoice != null) {
                resp.setInvoiceNumber(invoice.getInvoiceNumber());
                resp.setInvoiceUrl(invoice.getDownloadUrl());
            }
            out.add(resp);
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/payments/{paymentId}/verify")
    public ResponseEntity<InvoiceResponse> verifyPayment(
            @PathVariable Long paymentId,
            @RequestParam(required = false) String actor
    ) {
        FeeInstallment inst = feeInstallmentService.verifyPayment(paymentId, actor);
        InvoiceResponse resp = new InvoiceResponse();
        resp.setInstallmentId(inst.getInstallmentId());
        resp.setStatus(inst.getStatus());
        if ("Paid".equalsIgnoreCase(inst.getStatus())) {
            FeeInvoice inv = invoiceRepo.findByInstallment_InstallmentId(inst.getInstallmentId())
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
