package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.dto.FeePaymentGroupDto;
import com.bothash.admissionservice.dto.FeeInvoiceDto;
import com.bothash.admissionservice.dto.PartialPaymentRequest;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.entity.FileUpload;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import com.bothash.admissionservice.repository.FeeInstallmentPaymentRepository;
import com.bothash.admissionservice.repository.FileUploadRepository;
import com.bothash.admissionservice.service.impl.FeeInstallmentServiceImpl;
import com.bothash.admissionservice.service.impl.InvoiceServiceImpl;

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
        private String paymentType;
        private String txnRef;
        private String remarks;
        private String receivedBy;
        private String status;
        private String rejectionReason;
        private Boolean verified;
        private String verifiedBy;
        private java.time.LocalDateTime verifiedAt;
        private Boolean accountHeadVerified;
        private Boolean accountHeadRejected;
        private String accountHeadRejectionReason;
        private java.time.LocalDateTime accountHeadVerifiedAt;
        private java.time.LocalDate paidOn;
        private java.math.BigDecimal amount;
        private String receiptUrl;
        private String receiptName;
        private java.util.List<ReceiptLink> receipts;
        private String invoiceNumber;
        private String invoiceUrl;
    }

    @Data
    public static class ReceiptLink {
        private String name;
        private String url;
    }

    @Data
    public static class PaymentUpdateRequest {
        private java.math.BigDecimal amount;
        private String txnRef;
        private String receivedBy;
        private java.time.LocalDate paidOn;
    }

    @Data
    public static class PaymentRejectRequest {
        private String reason;
    }

    @GetMapping("/admissions/{admissionId}/payment-groups")
    public ResponseEntity<List<FeePaymentGroupDto>> listPaymentGroups(@PathVariable Long admissionId) {
        return ResponseEntity.ok(feeInstallmentService.listPaymentGroups(admissionId));
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
        Map<Long, java.util.List<FileUpload>> uploadsByPayment = uploads.stream()
                .filter(u -> u.getInstallmentPayment() != null)
                .collect(Collectors.groupingBy(u -> u.getInstallmentPayment().getPaymentId()));
        Map<Long, FeeInvoice> invoiceMap = invoiceRepo.findByPayment_PaymentIdIn(paymentIds).stream()
                .filter(inv -> inv.getPayment() != null)
                .filter(inv -> !InvoiceServiceImpl.isPaymentGroupInvoiceNumber(inv.getInvoiceNumber()))
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
            resp.setPaymentType(payment.getPaymentType());
            resp.setTxnRef(payment.getTxnRef());
            resp.setRemarks(payment.getRemarks());
            resp.setReceivedBy(payment.getReceivedBy());
            resp.setStatus(payment.getStatus());
            resp.setRejectionReason(payment.getRejectionReason());
            resp.setVerified(payment.getIsVerified());
            resp.setVerifiedBy(payment.getVerifiedBy());
            resp.setVerifiedAt(payment.getVerifiedAt());
            resp.setAccountHeadVerified(payment.getIsAccountHeadVerified());
            resp.setAccountHeadRejected(feeInstallmentService.isAccountHeadRejected(payment));
            resp.setAccountHeadRejectionReason(payment.getAccountHeadRejectionReason());
            resp.setAccountHeadVerifiedAt(payment.getAccountHeadVerifiedAt());
            resp.setPaidOn(payment.getPaidOn());
            java.util.List<FileUpload> paymentUploads = uploadsByPayment.get(payment.getPaymentId());
            if (paymentUploads != null && !paymentUploads.isEmpty()) {
                FileUpload first = paymentUploads.get(0);
                resp.setReceiptUrl(first.getStorageUrl());
                resp.setReceiptName(first.getFilename());
                java.util.List<ReceiptLink> links = new ArrayList<>();
                for (FileUpload up : paymentUploads) {
                    ReceiptLink link = new ReceiptLink();
                    link.setName(up.getFilename());
                    link.setUrl(up.getStorageUrl());
                    links.add(link);
                }
                resp.setReceipts(links);
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

    @PostMapping("/payments/{paymentId}/reject")
    public ResponseEntity<InvoiceResponse> rejectPayment(
            @PathVariable Long paymentId,
            @RequestParam(required = false) String actor,
            @RequestBody(required = false) PaymentRejectRequest request
    ) {
        FeeInstallment inst = feeInstallmentService.rejectPayment(paymentId, actor, request != null ? request.getReason() : null);
        InvoiceResponse resp = new InvoiceResponse();
        resp.setInstallmentId(inst.getInstallmentId());
        resp.setStatus(inst.getStatus());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/payment-groups/{paymentGroupId}/invoice")
    public ResponseEntity<FeeInvoiceDto> getPaymentGroupInvoice(@PathVariable String paymentGroupId) {
        FeeInvoice invoice = feeInstallmentService.ensurePaymentGroupInvoice(paymentGroupId);
        return ResponseEntity.ok(toDto(invoice));
    }

    @PostMapping("/payments/{paymentId}/account-head-verify")
    public ResponseEntity<PaymentResponse> verifyPaymentByAccountHead(
            @PathVariable Long paymentId,
            @RequestParam(required = false) String actor
    ) {
        FeeInstallmentPayment payment = feeInstallmentService.verifyPaymentByAccountHead(paymentId, actor);
        PaymentResponse resp = new PaymentResponse();
        resp.setPaymentId(payment.getPaymentId());
        resp.setVerified(payment.getIsVerified());
        resp.setVerifiedBy(payment.getVerifiedBy());
        resp.setVerifiedAt(payment.getVerifiedAt());
        resp.setAccountHeadVerified(payment.getIsAccountHeadVerified());
        resp.setAccountHeadRejected(feeInstallmentService.isAccountHeadRejected(payment));
        resp.setAccountHeadRejectionReason(payment.getAccountHeadRejectionReason());
        resp.setAccountHeadVerifiedAt(payment.getAccountHeadVerifiedAt());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/payments/{paymentId}/account-head-reject")
    public ResponseEntity<PaymentResponse> rejectPaymentByAccountHead(
            @PathVariable Long paymentId,
            @RequestParam(required = false) String actor,
            @RequestBody(required = false) PaymentRejectRequest request
    ) {
        FeeInstallmentPayment payment = feeInstallmentService.rejectPaymentByAccountHead(paymentId, actor, request != null ? request.getReason() : null);
        PaymentResponse resp = new PaymentResponse();
        resp.setPaymentId(payment.getPaymentId());
        resp.setVerified(payment.getIsVerified());
        resp.setVerifiedBy(payment.getVerifiedBy());
        resp.setVerifiedAt(payment.getVerifiedAt());
        resp.setAccountHeadVerified(payment.getIsAccountHeadVerified());
        resp.setAccountHeadRejected(feeInstallmentService.isAccountHeadRejected(payment));
        resp.setAccountHeadRejectionReason(payment.getAccountHeadRejectionReason());
        resp.setAccountHeadVerifiedAt(payment.getAccountHeadVerifiedAt());
        resp.setStatus(payment.getStatus());
        resp.setRejectionReason(payment.getRejectionReason());
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/payments/{paymentId}")
    public ResponseEntity<PaymentResponse> updatePayment(
            @PathVariable Long paymentId,
            @RequestBody PaymentUpdateRequest req
    ) {
        FeeInstallmentPayment payment = feeInstallmentService.updatePayment(
                paymentId,
                req != null ? req.getAmount() : null,
                req != null ? req.getTxnRef() : null,
                req != null ? req.getReceivedBy() : null,
                req != null ? req.getPaidOn() : null
        );
        PaymentResponse resp = new PaymentResponse();
        resp.setPaymentId(payment.getPaymentId());
        resp.setAmount(payment.getAmount());
        resp.setTxnRef(payment.getTxnRef());
        resp.setReceivedBy(payment.getReceivedBy());
        resp.setPaymentType(payment.getPaymentType());
        resp.setStatus(payment.getStatus());
        resp.setRejectionReason(payment.getRejectionReason());
        resp.setPaidOn(payment.getPaidOn());
        resp.setVerified(payment.getIsVerified());
        resp.setVerifiedBy(payment.getVerifiedBy());
        resp.setVerifiedAt(payment.getVerifiedAt());
        resp.setAccountHeadVerified(payment.getIsAccountHeadVerified());
        resp.setAccountHeadRejected(feeInstallmentService.isAccountHeadRejected(payment));
        resp.setAccountHeadRejectionReason(payment.getAccountHeadRejectionReason());
        resp.setAccountHeadVerifiedAt(payment.getAccountHeadVerifiedAt());
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/payment-groups/{paymentGroupId}")
    public ResponseEntity<FeePaymentGroupDto> updatePaymentGroup(
            @PathVariable String paymentGroupId,
            @RequestParam(required = false) String role,
            @RequestBody PartialPaymentRequest req
    ) {
        return ResponseEntity.ok(feeInstallmentService.updatePaymentGroup(paymentGroupId, req, role));
    }

    @PostMapping("/payment-groups/{paymentGroupId}/verify")
    public ResponseEntity<FeePaymentGroupDto> verifyPaymentGroup(
            @PathVariable String paymentGroupId,
            @RequestParam(required = false) String actor
    ) {
        return ResponseEntity.ok(feeInstallmentService.verifyPaymentGroup(paymentGroupId, actor));
    }

    @PostMapping("/payment-groups/{paymentGroupId}/reject")
    public ResponseEntity<FeePaymentGroupDto> rejectPaymentGroup(
            @PathVariable String paymentGroupId,
            @RequestParam(required = false) String actor,
            @RequestBody(required = false) PaymentRejectRequest request
    ) {
        return ResponseEntity.ok(feeInstallmentService.rejectPaymentGroup(paymentGroupId, actor, request != null ? request.getReason() : null));
    }

    @PostMapping("/payment-groups/{paymentGroupId}/account-head-verify")
    public ResponseEntity<FeePaymentGroupDto> verifyPaymentGroupByAccountHead(
            @PathVariable String paymentGroupId,
            @RequestParam(required = false) String actor
    ) {
        return ResponseEntity.ok(feeInstallmentService.verifyPaymentGroupByAccountHead(paymentGroupId, actor));
    }

    @PostMapping("/payment-groups/{paymentGroupId}/account-head-reject")
    public ResponseEntity<FeePaymentGroupDto> rejectPaymentGroupByAccountHead(
            @PathVariable String paymentGroupId,
            @RequestParam(required = false) String actor,
            @RequestBody(required = false) PaymentRejectRequest request
    ) {
        return ResponseEntity.ok(feeInstallmentService.rejectPaymentGroupByAccountHead(paymentGroupId, actor, request != null ? request.getReason() : null));
    }

    @DeleteMapping("/payments/{paymentId}")
    public ResponseEntity<Void> deletePayment(
            @PathVariable Long paymentId,
            @RequestParam(defaultValue = "true") boolean deleteFilesAlso
    ) {
        feeInstallmentService.deletePayment(paymentId, deleteFilesAlso);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/payment-groups/{paymentGroupId}")
    public ResponseEntity<Void> deletePaymentGroup(
            @PathVariable String paymentGroupId,
            @RequestParam(defaultValue = "true") boolean deleteFilesAlso
    ) {
        feeInstallmentService.deletePaymentGroup(paymentGroupId, deleteFilesAlso);
        return ResponseEntity.ok().build();
    }

    private FeeInvoiceDto toDto(FeeInvoice inv) {
        FeeInvoiceDto dto = new FeeInvoiceDto();
        dto.setInvoiceId(inv.getId());
        dto.setInvoiceNumber(inv.getInvoiceNumber());
        dto.setAmount(inv.getAmount());
        dto.setCreatedAt(inv.getCreatedAt());
        if (inv.getInstallment() != null) {
            dto.setInstallmentId(inv.getInstallment().getInstallmentId());
        }
        dto.setDownloadUrl(inv.getDownloadUrl());
        return dto;
    }
}
