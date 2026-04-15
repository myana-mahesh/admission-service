package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.dto.FeePaymentGroupDto;
import com.bothash.admissionservice.dto.PartialPaymentRequest;
import com.bothash.admissionservice.dto.UploadRequest;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.FileUpload;
import com.bothash.admissionservice.entity.PaymentModeMaster;
import com.bothash.admissionservice.repository.FeeInstallmentRepository;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import com.bothash.admissionservice.repository.FileUploadRepository;
import com.bothash.admissionservice.repository.FeeInstallmentPaymentRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.bothash.admissionservice.service.AdmissionAuditService;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeInstallmentServiceImpl {

    private final FeeInstallmentRepository installmentRepo;
    private final InvoiceServiceImpl invoiceService;
    
    private final FeeInvoiceRepository invoiceRepo;
    private final FileUploadRepository uploadRepo;
    private final FeeInstallmentPaymentRepository paymentRepo;
    private final AdmissionAuditService admissionAuditService;
    private final PaymentModeService paymentModeService;
    private final R2InvoiceStorageService r2InvoiceStorageService;

    @Transactional
    public FeeInstallment updateStatus(Long installmentId, String newStatus) {
        FeeInstallment inst = installmentRepo.findById(installmentId)
                .orElseThrow(() -> new IllegalArgumentException("Installment not found: " + installmentId));

        if ("Paid".equalsIgnoreCase(newStatus)) {
            var amountDue = inst.getAmountDue() == null ? java.math.BigDecimal.ZERO : inst.getAmountDue();
            var amountPaid = inst.getAmountPaid() == null ? java.math.BigDecimal.ZERO : inst.getAmountPaid();
            var hasPayment = amountPaid.compareTo(java.math.BigDecimal.ZERO) > 0;
            var fullyPaid = amountPaid.compareTo(amountDue) >= 0 && amountDue.compareTo(java.math.BigDecimal.ZERO) > 0;
            String resolvedStatus = hasPayment ? (fullyPaid ? "Paid" : "Partial Received") : "Under Verification";
            inst.setStatus(resolvedStatus);
            inst.setIsVerified(hasPayment);
            if (fullyPaid && inst.getPaidOn() == null) {
                inst.setPaidOn(java.time.LocalDate.now());
            }
        } else {
            inst.setStatus(newStatus);
        }

        FeeInstallment saved = installmentRepo.save(inst);

        // Only when changing from non-paid → Paid
        if ("Paid".equalsIgnoreCase(saved.getStatus())) {
            Admission2 admission = saved.getAdmission();
            FeeInvoice invoice = invoiceService.generateInvoiceForInstallment(admission, saved);
            log.info("Generated invoice {} for installment {}", invoice.getInvoiceNumber(), installmentId);
        }

        return saved;
    }

    @Transactional
    public FeeInstallment verifyPayment(Long paymentId, String actor) {
        var payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (Boolean.TRUE.equals(payment.getIsVerified())) {
            return payment.getInstallment();
        }

        payment.setIsVerified(true);
        payment.setVerifiedBy(actor);
        payment.setVerifiedAt(java.time.LocalDateTime.now());

        FeeInstallment installment = payment.getInstallment();
        var verifiedPayments = paymentRepo.findByInstallment_InstallmentIdOrderByCreatedAtAsc(
                installment.getInstallmentId());
        java.math.BigDecimal verifiedSum = java.math.BigDecimal.ZERO;
        for (var p : verifiedPayments) {
            if (Boolean.TRUE.equals(p.getIsVerified()) && p.getAmount() != null) {
                verifiedSum = verifiedSum.add(p.getAmount());
            }
        }
        var amountDue = installment.getAmountDue() == null ? java.math.BigDecimal.ZERO : installment.getAmountDue();
        boolean fullyPaid = verifiedSum.compareTo(amountDue) >= 0 && amountDue.compareTo(java.math.BigDecimal.ZERO) > 0;
        String newStatus = fullyPaid ? "Paid" : "Partial Received";
        installment.setStatus(newStatus);
        installment.setIsVerified(verifiedSum.compareTo(java.math.BigDecimal.ZERO) > 0);
        if (fullyPaid && installment.getPaidOn() == null) {
            installment.setPaidOn(java.time.LocalDate.now());
        }

        payment.setStatus("Paid");
        paymentRepo.save(payment);
        FeeInstallment saved = installmentRepo.save(installment);
        if (!hasAllocationInvoiceForPayment(paymentId)) {
            Admission2 admission = saved.getAdmission();
            FeeInvoice invoice = invoiceService.generateInvoiceForPayment(admission, saved, payment);
            log.info("Generated invoice {} for payment {}", invoice.getInvoiceNumber(), paymentId);
        }
        if (fullyPaid) {
            boolean hasInvoice = !invoiceRepo.findByInstallment_InstallmentId(saved.getInstallmentId()).isEmpty();
            if (!hasInvoice) {
                Admission2 admission = saved.getAdmission();
                FeeInvoice invoice = invoiceService.generateInvoiceForInstallment(admission, saved);
                log.info("Generated invoice {} for installment {}", invoice.getInvoiceNumber(), saved.getInstallmentId());
            }
        }
        return saved;
    }

    @Transactional
    public FeeInstallmentPayment verifyPaymentByAccountHead(Long paymentId, String actor) {
        var payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (Boolean.TRUE.equals(payment.getIsAccountHeadVerified())) {
            return payment;
        }
        payment.setIsAccountHeadVerified(true);
        payment.setAccountHeadVerifiedAt(java.time.LocalDateTime.now());
        return paymentRepo.save(payment);
    }

    @Transactional
    public FeePaymentGroupDto verifyPaymentGroup(String paymentGroupId, String actor) {
        if (!StringUtils.hasText(paymentGroupId)) {
            throw new IllegalArgumentException("Payment group is required.");
        }
        List<FeeInstallmentPayment> groupPayments = paymentRepo.findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(paymentGroupId);
        if (groupPayments.isEmpty()) {
            throw new IllegalArgumentException("Payment group not found: " + paymentGroupId);
        }

        Admission2 admission = groupPayments.get(0).getInstallment().getAdmission();
        List<FeeInstallment> admissionInstallments = installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(
                admission.getAdmissionId());
        Map<Long, Map<String, Object>> beforeInstallments = snapshotInstallments(admissionInstallments);
        String effectiveActor = StringUtils.hasText(actor) ? actor : resolveAuditActor();

        for (FeeInstallmentPayment payment : groupPayments) {
            if (!Boolean.TRUE.equals(payment.getIsVerified())) {
                verifyPayment(payment.getPaymentId(), effectiveActor);
            }
        }

        List<FeeInstallmentPayment> refreshedPayments = paymentRepo.findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(paymentGroupId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentGroupId", paymentGroupId);
        details.put("allocationCount", refreshedPayments.size());
        details.put("verifiedBy", effectiveActor);
        details.put("amount", sumPaymentAmounts(refreshedPayments));

        Map<String, Object> changedFields = buildInstallmentDiffPayload(
                beforeInstallments,
                installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admission.getAdmissionId())
        );
        addChange(changedFields, "paymentGroup.verified", false, true);
        admissionAuditService.record(admission, "PAYMENT_GROUP_VERIFIED", effectiveActor, details, changedFields);

        return buildPaymentGroups(refreshedPayments).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to load verified payment group."));
    }

    @Transactional
    public FeePaymentGroupDto verifyPaymentGroupByAccountHead(String paymentGroupId, String actor) {
        if (!StringUtils.hasText(paymentGroupId)) {
            throw new IllegalArgumentException("Payment group is required.");
        }
        List<FeeInstallmentPayment> groupPayments = paymentRepo.findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(paymentGroupId);
        if (groupPayments.isEmpty()) {
            throw new IllegalArgumentException("Payment group not found: " + paymentGroupId);
        }

        String effectiveActor = StringUtils.hasText(actor) ? actor : resolveAuditActor();
        boolean changed = false;
        for (FeeInstallmentPayment payment : groupPayments) {
            if (!Boolean.TRUE.equals(payment.getIsAccountHeadVerified())) {
                payment.setIsAccountHeadVerified(true);
                payment.setAccountHeadVerifiedAt(java.time.LocalDateTime.now());
                paymentRepo.save(payment);
                changed = true;
            }
        }

        List<FeeInstallmentPayment> refreshedPayments = paymentRepo.findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(paymentGroupId);
        if (changed) {
            Admission2 admission = refreshedPayments.get(0).getInstallment().getAdmission();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("paymentGroupId", paymentGroupId);
            details.put("allocationCount", refreshedPayments.size());
            details.put("verifiedBy", effectiveActor);
            details.put("amount", sumPaymentAmounts(refreshedPayments));
            Map<String, Object> changedFields = new LinkedHashMap<>();
            addChange(changedFields, "paymentGroup.accountHeadVerified", false, true);
            admissionAuditService.record(admission, "PAYMENT_GROUP_ACCOUNT_HEAD_VERIFIED", effectiveActor, details, changedFields);
        }

        return buildPaymentGroups(refreshedPayments).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to load account-head verified payment group."));
    }

    @Transactional
    public FeeInstallmentPayment updatePayment(Long paymentId,
                                              java.math.BigDecimal amount,
                                              String txnRef,
                                              String receivedBy,
                                              LocalDate paidOn) {
        FeeInstallmentPayment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        FeeInstallment installment = payment.getInstallment();
        Admission2 admission = installment.getAdmission();

        java.math.BigDecimal oldAmount = payment.getAmount();
        String oldTxnRef = payment.getTxnRef();
        String oldReceivedBy = payment.getReceivedBy();
        LocalDate oldPaidOn = payment.getPaidOn();

        if (amount != null) {
            payment.setAmount(amount);
        }
        payment.setTxnRef(txnRef);
        payment.setReceivedBy(receivedBy);
        payment.setPaidOn(paidOn);
        // Editing payment should require reverification
        payment.setIsVerified(false);
        payment.setVerifiedBy(null);
        payment.setVerifiedAt(null);
        payment.setStatus("Under Verification");
        payment.setIsAccountHeadVerified(false);
        payment.setAccountHeadVerifiedAt(null);
        FeeInstallmentPayment saved = paymentRepo.save(payment);

        recalculateInstallmentFromPayments(installment);

        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", paymentId);
        details.put("installmentId", installment.getInstallmentId());

        Map<String, Object> changedFields = new HashMap<>();
        addChange(changedFields, "amount", oldAmount, saved.getAmount());
        addChange(changedFields, "txnRef", oldTxnRef, saved.getTxnRef());
        addChange(changedFields, "receivedBy", oldReceivedBy, saved.getReceivedBy());
        addChange(changedFields, "paidOn", oldPaidOn, saved.getPaidOn());
        addChange(changedFields, "verified", true, false);
        admissionAuditService.record(admission, "INSTALLMENT_PAYMENT_UPDATED", resolveAuditActor(), details, changedFields);

        return saved;
    }

    @Transactional
    public void deletePayment(Long paymentId, boolean deleteFilesAlso) {
        FeeInstallmentPayment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        FeeInstallment installment = payment.getInstallment();
        Admission2 admission = installment.getAdmission();

        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", payment.getPaymentId());
        details.put("installmentId", installment.getInstallmentId());

        Map<String, Object> changedFields = new HashMap<>();
        addChange(changedFields, "payment.amount", payment.getAmount(), null);
        addChange(changedFields, "payment.txnRef", payment.getTxnRef(), null);
        addChange(changedFields, "payment.paidOn", payment.getPaidOn(), null);

        if (deleteFilesAlso) {
            for (FileUpload fu : uploadRepo.findByInstallmentPayment_PaymentId(paymentId)) {
                safeDeleteLocalFile(fu.getStorageUrl());
            }
        }
        uploadRepo.deleteByInstallmentPayment_PaymentId(paymentId);

        List<FeeInvoice> invoices = invoiceRepo.findByPayment_PaymentId(paymentId);
        if (deleteFilesAlso) {
            for (FeeInvoice inv : invoices) {
                safeDeleteLocalFile(inv.getFilePath());
            }
        }
        invoiceRepo.deleteAll(invoices);

        paymentRepo.delete(payment);
        recalculateInstallmentFromPayments(installment);

        admissionAuditService.record(admission, "INSTALLMENT_PAYMENT_DELETED", resolveAuditActor(), details, changedFields);
    }

    @Transactional
    public List<FeePaymentGroupDto> listPaymentGroups(Long admissionId) {
        List<FeeInstallmentPayment> payments = ensurePaymentGroupsForAdmission(admissionId);
        if (payments.isEmpty()) {
            return List.of();
        }
        return buildPaymentGroups(payments);
    }

    @Transactional
    public FeeInvoice ensurePaymentGroupInvoice(String paymentGroupId) {
        if (!StringUtils.hasText(paymentGroupId)) {
            throw new IllegalArgumentException("Payment group is required.");
        }
        List<FeeInstallmentPayment> groupPayments = paymentRepo.findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(paymentGroupId);
        if (groupPayments.isEmpty()) {
            throw new IllegalArgumentException("Payment group not found: " + paymentGroupId);
        }

        FeeInvoice existing = resolveGroupedInvoice(groupPayments);
        Admission2 admission = groupPayments.get(0).getInstallment().getAdmission();
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getReceiptDateSynced())) {
                return existing;
            }
            safeDeleteLocalFile(existing.getFilePath());
        }
        if (groupPayments.size() == 1) {
            FeeInstallmentPayment payment = groupPayments.get(0);
            return invoiceService.generateInvoiceForPayment(admission, payment.getInstallment(), payment);
        }
        return invoiceService.generateInvoiceForPaymentGroup(admission, groupPayments, paymentGroupId);
    }

    @Transactional
    public FeePaymentGroupDto updatePaymentGroup(String paymentGroupId, PartialPaymentRequest request, String role) {
        if (!StringUtils.hasText(paymentGroupId)) {
            throw new IllegalArgumentException("Payment group is required.");
        }
        if (request == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Payment amount must be non-zero.");
        }

        List<FeeInstallmentPayment> groupPayments = paymentRepo.findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(paymentGroupId);
        if (groupPayments.isEmpty()) {
            throw new IllegalArgumentException("Payment group not found: " + paymentGroupId);
        }

        Admission2 admission = groupPayments.get(0).getInstallment().getAdmission();
        List<FeeInstallment> admissionInstallments = installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(
                admission.getAdmissionId());
        Map<Long, Map<String, Object>> beforeInstallments = snapshotInstallments(admissionInstallments);

        BigDecimal oldTotalAmount = sumPaymentAmounts(groupPayments);
        String oldTxnRef = firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getTxnRef).toList());
        String oldRemarks = firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getRemarks).toList());
        String oldReceivedBy = firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getReceivedBy).toList());
        String oldMode = resolvePaymentModeCode(groupPayments);
        LocalDate oldPaidOn = resolvePaidOn(groupPayments);
        if (!StringUtils.hasText(request.getRemarks())) {
            request.setRemarks(oldRemarks);
        }

        UploadRequest effectiveReceipt = request.getReceipt();
        if (effectiveReceipt == null) {
            effectiveReceipt = captureExistingReceipt(groupPayments, admission);
        }

        deletePaymentGroupInternal(groupPayments, request.getReceipt() != null, request.getReceipt() == null);

        List<FeeInstallmentPayment> newPayments = applyPaymentGroupAllocations(
                admission,
                request,
                role,
                paymentGroupId,
                effectiveReceipt
        );

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentGroupId", paymentGroupId);
        details.put("oldAmount", oldTotalAmount);
        details.put("newAmount", request.getAmount());
        details.put("oldTxnRef", oldTxnRef);
        details.put("newTxnRef", request.getTxnRef());
        details.put("oldRemarks", oldRemarks);
        details.put("newRemarks", request.getRemarks());
        details.put("oldReceivedBy", oldReceivedBy);
        details.put("newReceivedBy", request.getReceivedBy());
        details.put("oldMode", oldMode);
        details.put("newMode", request.getMode());
        details.put("oldPaidOn", oldPaidOn);
        details.put("newPaidOn", request.getPaidOn());

        Map<String, Object> changedFields = buildInstallmentDiffPayload(
                beforeInstallments,
                installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admission.getAdmissionId())
        );
        addChange(changedFields, "paymentGroup.amount", oldTotalAmount, request.getAmount());
        addChange(changedFields, "paymentGroup.txnRef", oldTxnRef, request.getTxnRef());
        addChange(changedFields, "paymentGroup.remarks", oldRemarks, request.getRemarks());
        addChange(changedFields, "paymentGroup.receivedBy", oldReceivedBy, request.getReceivedBy());
        addChange(changedFields, "paymentGroup.mode", oldMode, request.getMode());
        addChange(changedFields, "paymentGroup.paidOn", oldPaidOn, request.getPaidOn());

        admissionAuditService.record(admission, "PAYMENT_GROUP_UPDATED", resolveAuditActor(), details, changedFields);
        return buildPaymentGroups(newPayments).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to rebuild payment group."));
    }

    @Transactional
    public void deletePaymentGroup(String paymentGroupId, boolean deleteFilesAlso) {
        if (!StringUtils.hasText(paymentGroupId)) {
            throw new IllegalArgumentException("Payment group is required.");
        }
        List<FeeInstallmentPayment> groupPayments = paymentRepo.findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(paymentGroupId);
        if (groupPayments.isEmpty()) {
            throw new IllegalArgumentException("Payment group not found: " + paymentGroupId);
        }

        Admission2 admission = groupPayments.get(0).getInstallment().getAdmission();
        List<FeeInstallment> admissionInstallments = installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(
                admission.getAdmissionId());
        Map<Long, Map<String, Object>> beforeInstallments = snapshotInstallments(admissionInstallments);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentGroupId", paymentGroupId);
        details.put("amount", sumPaymentAmounts(groupPayments));
        details.put("txnRef", firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getTxnRef).toList()));
        details.put("remarks", firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getRemarks).toList()));
        details.put("receivedBy", firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getReceivedBy).toList()));
        details.put("mode", resolvePaymentModeCode(groupPayments));
        details.put("paidOn", resolvePaidOn(groupPayments));

        deletePaymentGroupInternal(groupPayments, deleteFilesAlso, false);

        Map<String, Object> changedFields = buildInstallmentDiffPayload(
                beforeInstallments,
                installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admission.getAdmissionId())
        );
        admissionAuditService.record(admission, "PAYMENT_GROUP_DELETED", resolveAuditActor(), details, changedFields);
    }
    
    @Transactional
    public void deleteInstallment(Long installmentId, boolean deleteFilesAlso) {

        FeeInstallment inst = installmentRepo.findById(installmentId)
                .orElseThrow(() -> new EntityNotFoundException("FeeInstallment not found: " + installmentId));

        Admission2 admission = inst.getAdmission();
        Long admissionId = inst.getAdmission().getAdmissionId();
        Integer studyYear = inst.getStudyYear();
        Integer installmentNo = inst.getInstallmentNo();
        java.math.BigDecimal amountDue = inst.getAmountDue();
        java.math.BigDecimal amountPaid = inst.getAmountPaid();
        java.time.LocalDate dueDate = inst.getDueDate();
        String status = inst.getStatus();
        
        // Optional safety: don't allow delete if paid
        if (inst.getAmountPaid() != null && inst.getAmountPaid().signum() > 0) {
            throw new IllegalStateException("Cannot delete: installment has payment amountPaid > 0");
        }
        if ("Paid".equalsIgnoreCase(inst.getStatus())) {
            throw new IllegalStateException("Cannot delete: installment status is Paid");
        }

        // 1) Invoices (delete files first if needed)
        List<FeeInvoice> invoices = invoiceRepo.findByInstallment_InstallmentId(installmentId);
        if (deleteFilesAlso) {
            for (FeeInvoice inv : invoices) {
                safeDeleteLocalFile(inv.getFilePath());  // your invoice pdf path
            }
        }
        invoiceRepo.deleteAll(invoices); // or invoiceRepo.deleteByInstallment_InstallmentId(installmentId)

        // 2) Uploads linked to installment
        List<FileUpload> uploads = uploadRepo.findByInstallment_InstallmentId(installmentId);
        if (deleteFilesAlso) {
            for (FileUpload fu : uploads) {
                // If storageUrl is local file path or file://... you can delete locally.
                // If it's S3/cloud url, you must call that provider API instead.
            	safeDeleteLocalFile(fu.getStorageUrl());
            }
        }
        uploadRepo.deleteAll(uploads); // or uploadRepo.deleteByInstallment_InstallmentId(installmentId)

        // 3) Delete installment
        installmentRepo.delete(inst);
        
        resequenceInstallments(admissionId, studyYear);

        Map<String, Object> details = new HashMap<>();
        details.put("installmentId", installmentId);
        details.put("admissionId", admissionId);
        details.put("studyYear", studyYear);
        details.put("installmentNo", installmentNo);
        details.put("deletedAt", OffsetDateTime.now());

        Map<String, Object> beforePayload = new HashMap<>();
        beforePayload.put("studyYear", studyYear);
        beforePayload.put("installmentNo", installmentNo);
        beforePayload.put("amountDue", amountDue);
        beforePayload.put("amountPaid", amountPaid);
        beforePayload.put("dueDate", dueDate);
        beforePayload.put("status", status);

        Map<String, Object> deleteChange = new HashMap<>();
        deleteChange.put("label", "Installment Deleted");
        deleteChange.put("before", beforePayload);
        deleteChange.put("after", null);

        Map<String, Object> changedFields = new HashMap<>();
        changedFields.put("installmentDeleted", deleteChange);

        admissionAuditService.record(admission, "INSTALLMENT_DELETED", resolveAuditActor(), details, changedFields);
    }

    private void safeDeleteLocalFile(String filePath) {
        try {
            if (filePath == null || filePath.isBlank()) return;
            if (r2InvoiceStorageService.isEnabled()) {
                String r2Key = r2InvoiceStorageService.extractKey(filePath);
                if (StringUtils.hasText(r2Key)) {
                    r2InvoiceStorageService.delete(r2Key);
                    return;
                }
            }
            Path p = Paths.get(filePath);
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
            // log if you want
        }
    }

    private void safeDeleteFromStorageUrl(String storageUrl) {
        try {
            if (storageUrl == null || storageUrl.isBlank()) return;

            if (r2InvoiceStorageService.isEnabled()) {
                String r2Key = r2InvoiceStorageService.extractKey(storageUrl);
                if (StringUtils.hasText(r2Key)) {
                    r2InvoiceStorageService.delete(r2Key);
                    return;
                }
            }

            // If you stored as file path directly:
            if (!storageUrl.startsWith("http")) {
                safeDeleteLocalFile(storageUrl);
                return;
            }

            // If you stored file://... convert to Path
            if (storageUrl.startsWith("file:")) {
                Path p = Paths.get(URI.create(storageUrl));
                Files.deleteIfExists(p);
            }

            // If it's https (S3/Cloudinary/etc), DO NOT delete by filesystem.
            // You need provider API call here.
        } catch (Exception ignored) {
            // log if you want
        }
    }
    
    private void resequenceInstallments(Long admissionId, Integer studyYear) {
        List<FeeInstallment> remaining =
                installmentRepo.findByAdmission_AdmissionIdAndStudyYearOrderByInstallmentNoAsc(admissionId, studyYear);

        int i = 1;
        for (FeeInstallment fi : remaining) {
            if (fi.getInstallmentNo() == null || fi.getInstallmentNo() != i) {
                fi.setInstallmentNo(i);
            }
            i++;
        }
        installmentRepo.saveAll(remaining);
    }

    private List<FeeInstallmentPayment> ensurePaymentGroupsForAdmission(Long admissionId) {
        List<FeeInstallmentPayment> payments = paymentRepo.findByInstallment_Admission_AdmissionIdOrderByCreatedAtAscPaymentIdAsc(admissionId);
        if (payments.isEmpty()) {
            return payments;
        }
        Map<String, List<FeeInstallmentPayment>> legacyClusters = new LinkedHashMap<>();
        for (FeeInstallmentPayment payment : payments) {
            if (StringUtils.hasText(payment.getPaymentGroupId())) {
                continue;
            }
            legacyClusters.computeIfAbsent(buildLegacyClusterKey(payment), key -> new ArrayList<>()).add(payment);
        }

        List<FeeInstallmentPayment> changed = new ArrayList<>();
        for (List<FeeInstallmentPayment> cluster : legacyClusters.values()) {
            String groupId = UUID.randomUUID().toString();
            for (FeeInstallmentPayment payment : cluster) {
                payment.setPaymentGroupId(groupId);
                changed.add(payment);
            }
        }
        if (!changed.isEmpty()) {
            paymentRepo.saveAll(changed);
            payments = paymentRepo.findByInstallment_Admission_AdmissionIdOrderByCreatedAtAscPaymentIdAsc(admissionId);
        }
        payments = repairNegativePaymentGroupsIfNeeded(admissionId, payments);
        return payments;
    }

    private String buildLegacyClusterKey(FeeInstallmentPayment payment) {
        String sign = payment.getAmount() != null && payment.getAmount().compareTo(BigDecimal.ZERO) < 0 ? "NEG" : "POS";
        String paidOn = payment.getPaidOn() != null ? payment.getPaidOn().toString() : "";
        String mode = payment.getPaymentMode() != null && StringUtils.hasText(payment.getPaymentMode().getCode())
                ? payment.getPaymentMode().getCode().trim().toUpperCase()
                : "";
        String txnRef = StringUtils.hasText(payment.getTxnRef()) ? payment.getTxnRef().trim().toUpperCase() : "";
        String receivedBy = StringUtils.hasText(payment.getReceivedBy()) ? payment.getReceivedBy().trim().toUpperCase() : "";
        String createdSecond = payment.getCreatedAt() != null
                ? payment.getCreatedAt().truncatedTo(ChronoUnit.SECONDS).toString()
                : "NO_CREATED_AT";
        return String.join("|", sign, paidOn, mode, txnRef, receivedBy, createdSecond);
    }

    private List<FeePaymentGroupDto> buildPaymentGroups(List<FeeInstallmentPayment> payments) {
        List<Long> paymentIds = payments.stream().map(FeeInstallmentPayment::getPaymentId).toList();
        Map<Long, List<FileUpload>> uploadsByPayment = uploadRepo.findByInstallmentPayment_PaymentIdIn(paymentIds).stream()
                .filter(upload -> upload.getInstallmentPayment() != null && upload.getInstallmentPayment().getPaymentId() != null)
                .collect(Collectors.groupingBy(upload -> upload.getInstallmentPayment().getPaymentId(), LinkedHashMap::new, Collectors.toList()));

        Map<String, List<FeeInstallmentPayment>> grouped = payments.stream()
                .filter(payment -> StringUtils.hasText(payment.getPaymentGroupId()))
                .collect(Collectors.groupingBy(FeeInstallmentPayment::getPaymentGroupId, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> toPaymentGroupDto(entry.getKey(), entry.getValue(), uploadsByPayment))
                .sorted(Comparator.comparing(FeePaymentGroupDto::getPaidOn, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(FeePaymentGroupDto::getPaymentGroupId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private FeePaymentGroupDto toPaymentGroupDto(String paymentGroupId,
                                                 List<FeeInstallmentPayment> groupPayments,
                                                 Map<Long, List<FileUpload>> uploadsByPayment) {
        groupPayments.sort(Comparator.comparing(FeeInstallmentPayment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FeeInstallmentPayment::getPaymentId, Comparator.nullsLast(Comparator.naturalOrder())));
        FeePaymentGroupDto dto = new FeePaymentGroupDto();
        dto.setPaymentGroupId(paymentGroupId);
        dto.setPaidOn(resolvePaidOn(groupPayments));
        dto.setTotalAmount(sumPaymentAmounts(groupPayments));
        dto.setPaymentMode(resolvePaymentModeCode(groupPayments));
        dto.setTxnRef(firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getTxnRef).toList()));
        dto.setRemarks(firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getRemarks).toList()));
        dto.setReceivedBy(firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getReceivedBy).toList()));
        boolean verified = groupPayments.stream().allMatch(payment -> Boolean.TRUE.equals(payment.getIsVerified()));
        boolean accountHeadVerified = groupPayments.stream().allMatch(payment -> Boolean.TRUE.equals(payment.getIsAccountHeadVerified()));
        dto.setVerified(verified);
        dto.setAccountHeadVerified(accountHeadVerified);
        dto.setStatus(verified ? "Paid" : "Under Verification");
        dto.setAllocationCount(groupPayments.size());
        FileUpload receipt = resolveFirstReceipt(groupPayments, uploadsByPayment);
        if (receipt != null) {
            dto.setReceiptUrl(receipt.getStorageUrl());
            dto.setReceiptName(receipt.getFilename());
        }
        FeeInvoice groupedInvoice = resolveGroupedInvoice(groupPayments);
        if (groupedInvoice != null) {
            dto.setInvoiceNumber(groupedInvoice.getInvoiceNumber());
            dto.setInvoiceUrl(groupedInvoice.getDownloadUrl());
        }
        return dto;
    }

    private FileUpload resolveFirstReceipt(List<FeeInstallmentPayment> groupPayments, Map<Long, List<FileUpload>> uploadsByPayment) {
        for (FeeInstallmentPayment payment : groupPayments) {
            List<FileUpload> uploads = uploadsByPayment.get(payment.getPaymentId());
            if (uploads != null && !uploads.isEmpty()) {
                return uploads.get(0);
            }
        }
        return null;
    }

    private FeeInvoice resolveGroupedInvoice(List<FeeInstallmentPayment> groupPayments) {
        List<Long> paymentIds = groupPayments.stream()
                .map(FeeInstallmentPayment::getPaymentId)
                .filter(Objects::nonNull)
                .toList();
        if (paymentIds.isEmpty()) {
            return null;
        }
        List<FeeInvoice> invoices = invoiceRepo.findByPayment_PaymentIdIn(paymentIds);
        if (groupPayments.size() == 1) {
            return invoices.stream()
                    .sorted(Comparator.comparing(FeeInvoice::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(FeeInvoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .filter(invoice -> !InvoiceServiceImpl.isPaymentGroupInvoiceNumber(invoice.getInvoiceNumber()))
                    .findFirst()
                    .orElseGet(() -> invoices.stream()
                            .sorted(Comparator.comparing(FeeInvoice::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                    .thenComparing(FeeInvoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                            .findFirst()
                            .orElse(null));
        }
        return invoices.stream()
                .sorted(Comparator.comparing(FeeInvoice::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FeeInvoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .filter(invoice -> InvoiceServiceImpl.isPaymentGroupInvoiceNumber(invoice.getInvoiceNumber()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasAllocationInvoiceForPayment(Long paymentId) {
        return invoiceRepo.findByPayment_PaymentId(paymentId).stream()
                .anyMatch(invoice -> !InvoiceServiceImpl.isPaymentGroupInvoiceNumber(invoice.getInvoiceNumber()));
    }

    private UploadRequest captureExistingReceipt(List<FeeInstallmentPayment> groupPayments, Admission2 admission) {
        List<Long> paymentIds = groupPayments.stream().map(FeeInstallmentPayment::getPaymentId).toList();
        List<FileUpload> uploads = uploadRepo.findByInstallmentPayment_PaymentIdIn(paymentIds);
        if (uploads.isEmpty()) {
            return null;
        }
        FileUpload first = uploads.get(0);
        return UploadRequest.builder()
                .docTypeCode("RECEIPT")
                .filename(first.getFilename())
                .mimeType(first.getMimeType())
                .sizeBytes(first.getSizeBytes())
                .storageUrl(first.getStorageUrl())
                .sha256(first.getSha256())
                .label(first.getLabel())
                .build();
    }

    private List<FeeInstallmentPayment> repairNegativePaymentGroupsIfNeeded(Long admissionId,
                                                                            List<FeeInstallmentPayment> payments) {
        if (payments == null || payments.isEmpty()) {
            return payments;
        }
        Map<String, List<FeeInstallmentPayment>> grouped = payments.stream()
                .filter(payment -> StringUtils.hasText(payment.getPaymentGroupId()))
                .collect(Collectors.groupingBy(FeeInstallmentPayment::getPaymentGroupId, LinkedHashMap::new, Collectors.toList()));
        if (grouped.isEmpty()) {
            return payments;
        }

        List<String> negativeGroupIds = grouped.entrySet().stream()
                .filter(entry -> sumPaymentAmounts(entry.getValue()).compareTo(BigDecimal.ZERO) < 0)
                .sorted((left, right) -> compareGroupStart(right.getValue(), left.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        boolean repairedAny = false;
        for (String paymentGroupId : negativeGroupIds) {
            List<FeeInstallmentPayment> currentGroup = paymentRepo.findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(paymentGroupId);
            if (currentGroup.isEmpty() || sumPaymentAmounts(currentGroup).compareTo(BigDecimal.ZERO) >= 0) {
                continue;
            }
            List<ExpectedNegativeAllocation> expectedAllocations = buildExpectedNegativeAllocations(currentGroup);
            if (expectedAllocations.isEmpty() || !negativePaymentGroupNeedsRepair(currentGroup, expectedAllocations)) {
                continue;
            }
            repairNegativePaymentGroup(currentGroup, expectedAllocations);
            repairedAny = true;
        }

        if (repairedAny) {
            return paymentRepo.findByInstallment_Admission_AdmissionIdOrderByCreatedAtAscPaymentIdAsc(admissionId);
        }
        return payments;
    }

    private int compareGroupStart(List<FeeInstallmentPayment> left, List<FeeInstallmentPayment> right) {
        FeeInstallmentPayment leftStart = firstPaymentInGroup(left);
        FeeInstallmentPayment rightStart = firstPaymentInGroup(right);
        return comparePaymentSequence(leftStart, rightStart);
    }

    private FeeInstallmentPayment firstPaymentInGroup(List<FeeInstallmentPayment> groupPayments) {
        return groupPayments.stream()
                .filter(Objects::nonNull)
                .sorted(this::comparePaymentSequence)
                .findFirst()
                .orElse(null);
    }

    private int comparePaymentSequence(FeeInstallmentPayment left, FeeInstallmentPayment right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        OffsetDateTime leftCreatedAt = left.getCreatedAt();
        OffsetDateTime rightCreatedAt = right.getCreatedAt();
        int createdCompare;
        if (leftCreatedAt == null && rightCreatedAt == null) {
            createdCompare = 0;
        } else if (leftCreatedAt == null) {
            createdCompare = -1;
        } else if (rightCreatedAt == null) {
            createdCompare = 1;
        } else {
            createdCompare = leftCreatedAt.compareTo(rightCreatedAt);
        }
        if (createdCompare != 0) {
            return createdCompare;
        }
        Long leftId = left.getPaymentId();
        Long rightId = right.getPaymentId();
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return -1;
        }
        if (rightId == null) {
            return 1;
        }
        return leftId.compareTo(rightId);
    }

    private List<ExpectedNegativeAllocation> buildExpectedNegativeAllocations(List<FeeInstallmentPayment> groupPayments) {
        if (groupPayments == null || groupPayments.isEmpty()) {
            return List.of();
        }
        BigDecimal groupTotal = sumPaymentAmounts(groupPayments);
        if (groupTotal.compareTo(BigDecimal.ZERO) >= 0) {
            return List.of();
        }
        FeeInstallmentPayment firstGroupPayment = firstPaymentInGroup(groupPayments);
        if (firstGroupPayment == null || firstGroupPayment.getInstallment() == null
                || firstGroupPayment.getInstallment().getAdmission() == null) {
            return List.of();
        }
        Long admissionId = firstGroupPayment.getInstallment().getAdmission().getAdmissionId();
        List<FeeInstallment> installments = installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admissionId);
        if (installments.isEmpty()) {
            return List.of();
        }

        java.util.Set<Long> groupPaymentIds = groupPayments.stream()
                .map(FeeInstallmentPayment::getPaymentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<FeeInstallmentPayment> admissionPayments =
                paymentRepo.findByInstallment_Admission_AdmissionIdOrderByCreatedAtAscPaymentIdAsc(admissionId);

        Map<Long, BigDecimal> paidBeforeGroup = new LinkedHashMap<>();
        for (FeeInstallment installment : installments) {
            paidBeforeGroup.put(installment.getInstallmentId(), BigDecimal.ZERO);
        }
        for (FeeInstallmentPayment payment : admissionPayments) {
            if (payment == null || groupPaymentIds.contains(payment.getPaymentId())) {
                continue;
            }
            if (comparePaymentSequence(payment, firstGroupPayment) >= 0) {
                continue;
            }
            FeeInstallment installment = payment.getInstallment();
            if (installment == null || installment.getInstallmentId() == null) {
                continue;
            }
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            paidBeforeGroup.merge(installment.getInstallmentId(), amount, BigDecimal::add);
        }

        BigDecimal remaining = groupTotal.abs();
        List<ExpectedNegativeAllocation> expected = new ArrayList<>();
        List<FeeInstallment> reversalOrder = new ArrayList<>(installments);
        java.util.Collections.reverse(reversalOrder);
        for (FeeInstallment installment : reversalOrder) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal availablePaid = paidBeforeGroup.getOrDefault(installment.getInstallmentId(), BigDecimal.ZERO);
            if (availablePaid.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal applied = remaining.min(availablePaid);
            expected.add(new ExpectedNegativeAllocation(installment, applied.negate()));
            remaining = remaining.subtract(applied);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            return List.of();
        }
        return expected;
    }

    private boolean negativePaymentGroupNeedsRepair(List<FeeInstallmentPayment> groupPayments,
                                                    List<ExpectedNegativeAllocation> expectedAllocations) {
        Map<Long, BigDecimal> actualByInstallment = new LinkedHashMap<>();
        for (FeeInstallmentPayment payment : groupPayments) {
            if (payment == null || payment.getInstallment() == null || payment.getInstallment().getInstallmentId() == null) {
                continue;
            }
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            actualByInstallment.merge(payment.getInstallment().getInstallmentId(), amount, BigDecimal::add);
        }
        Map<Long, BigDecimal> expectedByInstallment = new LinkedHashMap<>();
        for (ExpectedNegativeAllocation allocation : expectedAllocations) {
            expectedByInstallment.merge(allocation.installment().getInstallmentId(), allocation.amount(), BigDecimal::add);
        }
        if (actualByInstallment.size() != expectedByInstallment.size()) {
            return true;
        }
        for (Map.Entry<Long, BigDecimal> entry : expectedByInstallment.entrySet()) {
            BigDecimal actual = actualByInstallment.get(entry.getKey());
            if (actual == null || actual.compareTo(entry.getValue()) != 0) {
                return true;
            }
        }
        return false;
    }

    private void repairNegativePaymentGroup(List<FeeInstallmentPayment> groupPayments,
                                            List<ExpectedNegativeAllocation> expectedAllocations) {
        if (groupPayments == null || groupPayments.isEmpty() || expectedAllocations == null || expectedAllocations.isEmpty()) {
            return;
        }
        List<FeeInstallmentPayment> sortedGroupPayments = groupPayments.stream()
                .filter(Objects::nonNull)
                .sorted(this::comparePaymentSequence)
                .collect(Collectors.toCollection(ArrayList::new));
        FeeInstallmentPayment anchorPayment = sortedGroupPayments.get(0);
        FeeInstallment anchorInstallment = anchorPayment.getInstallment();
        Admission2 admission = anchorInstallment != null ? anchorInstallment.getAdmission() : null;
        if (admission == null) {
            return;
        }

        List<FeeInstallment> admissionInstallments =
                installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admission.getAdmissionId());
        Map<Long, Map<String, Object>> beforeInstallments = snapshotInstallments(admissionInstallments);
        UploadRequest existingReceipt = captureExistingReceipt(sortedGroupPayments, admission);
        List<Long> oldPaymentIds = sortedGroupPayments.stream()
                .map(FeeInstallmentPayment::getPaymentId)
                .filter(Objects::nonNull)
                .toList();
        List<FileUpload> oldUploads = oldPaymentIds.isEmpty()
                ? List.of()
                : uploadRepo.findByInstallmentPayment_PaymentIdIn(oldPaymentIds);
        List<FeeInvoice> oldInvoices = oldPaymentIds.isEmpty()
                ? List.of()
                : invoiceRepo.findByPayment_PaymentIdIn(oldPaymentIds);
        boolean hadGroupInvoice = oldInvoices.stream()
                .anyMatch(invoice -> InvoiceServiceImpl.isPaymentGroupInvoiceNumber(invoice.getInvoiceNumber()));

        if (!oldUploads.isEmpty()) {
            uploadRepo.deleteAll(oldUploads);
        }
        if (!oldInvoices.isEmpty()) {
            for (FeeInvoice invoice : oldInvoices) {
                safeDeleteLocalFile(invoice.getFilePath());
            }
            invoiceRepo.deleteAll(oldInvoices);
        }

        List<FeeInstallmentPayment> retainedPayments = new ArrayList<>();
        for (int i = 0; i < expectedAllocations.size(); i++) {
            ExpectedNegativeAllocation allocation = expectedAllocations.get(i);
            FeeInstallmentPayment payment;
            if (i < sortedGroupPayments.size()) {
                payment = sortedGroupPayments.get(i);
            } else {
                payment = clonePaymentForRepair(anchorPayment);
            }
            payment.setInstallment(allocation.installment());
            payment.setAmount(allocation.amount());
            retainedPayments.add(payment);
        }

        if (sortedGroupPayments.size() > expectedAllocations.size()) {
            List<FeeInstallmentPayment> extraPayments = sortedGroupPayments.subList(expectedAllocations.size(), sortedGroupPayments.size());
            if (!extraPayments.isEmpty()) {
                paymentRepo.deleteAll(extraPayments);
            }
        }

        List<FeeInstallmentPayment> savedPayments = paymentRepo.saveAll(retainedPayments);
        if (existingReceipt != null && !savedPayments.isEmpty() && StringUtils.hasText(existingReceipt.getStorageUrl())) {
            uploadRepo.save(buildPaymentReceiptUpload(admission, savedPayments.get(0), existingReceipt));
        }

        java.util.Set<Long> impactedInstallmentIds = new java.util.LinkedHashSet<>();
        sortedGroupPayments.stream()
                .map(FeeInstallmentPayment::getInstallment)
                .filter(Objects::nonNull)
                .map(FeeInstallment::getInstallmentId)
                .filter(Objects::nonNull)
                .forEach(impactedInstallmentIds::add);
        expectedAllocations.stream()
                .map(ExpectedNegativeAllocation::installment)
                .filter(Objects::nonNull)
                .map(FeeInstallment::getInstallmentId)
                .filter(Objects::nonNull)
                .forEach(impactedInstallmentIds::add);

        List<FeeInstallment> impactedInstallments = admissionInstallments.stream()
                .filter(installment -> impactedInstallmentIds.contains(installment.getInstallmentId()))
                .toList();
        for (FeeInstallment installment : impactedInstallments) {
            recalculateInstallmentFromPayments(installment);
        }
        if (hadGroupInvoice) {
            ensurePaymentGroupInvoice(anchorPayment.getPaymentGroupId());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paymentGroupId", anchorPayment.getPaymentGroupId());
        details.put("amount", sumPaymentAmounts(savedPayments));
        details.put("allocationCountBefore", sortedGroupPayments.size());
        details.put("allocationCountAfter", savedPayments.size());
        details.put("systemAction", "NEGATIVE_PAYMENT_GROUP_REALLOCATED");

        Map<String, Object> changedFields = buildInstallmentDiffPayload(
                beforeInstallments,
                installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admission.getAdmissionId())
        );
        addChange(changedFields, "paymentGroup.repaired", false, true);
        admissionAuditService.record(admission, "NEGATIVE_PAYMENT_GROUP_REALLOCATED", "SYSTEM", details, changedFields);
    }

    private FeeInstallmentPayment clonePaymentForRepair(FeeInstallmentPayment anchorPayment) {
        return FeeInstallmentPayment.builder()
                .paymentGroupId(anchorPayment.getPaymentGroupId())
                .paymentMode(anchorPayment.getPaymentMode())
                .txnRef(anchorPayment.getTxnRef())
                .remarks(anchorPayment.getRemarks())
                .receivedBy(anchorPayment.getReceivedBy())
                .status(anchorPayment.getStatus())
                .isVerified(anchorPayment.getIsVerified())
                .verifiedBy(anchorPayment.getVerifiedBy())
                .verifiedAt(anchorPayment.getVerifiedAt())
                .isAccountHeadVerified(anchorPayment.getIsAccountHeadVerified())
                .accountHeadVerifiedAt(anchorPayment.getAccountHeadVerifiedAt())
                .paidOn(anchorPayment.getPaidOn())
                .build();
    }

    private record ExpectedNegativeAllocation(FeeInstallment installment, BigDecimal amount) {}

    private List<FeeInstallmentPayment> applyPaymentGroupAllocations(Admission2 admission,
                                                                     PartialPaymentRequest request,
                                                                     String role,
                                                                     String paymentGroupId,
                                                                     UploadRequest receipt) {
        List<FeeInstallment> installments = installmentRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(
                admission.getAdmissionId());
        if (installments.isEmpty()) {
            throw new IllegalArgumentException("No installments found for admission: " + admission.getAdmissionId());
        }
        PaymentModeMaster paymentMode = null;
        if (StringUtils.hasText(request.getMode())) {
            paymentMode = paymentModeService.getByMode(request.getMode());
        }

        BigDecimal remaining = request.getAmount();
        LocalDate paidOn = request.getPaidOn() != null ? request.getPaidOn() : LocalDate.now();
        List<FeeInstallmentPayment> payments = new ArrayList<>();
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            for (FeeInstallment installment : installments) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal amountDue = installment.getAmountDue() == null ? BigDecimal.ZERO : installment.getAmountDue();
                BigDecimal amountPaid = installment.getAmountPaid() == null ? BigDecimal.ZERO : installment.getAmountPaid();
                BigDecimal pending = amountDue.subtract(amountPaid);
                if (pending.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal applied = remaining.min(pending);
                payments.add(createGroupedAllocation(paymentGroupId, installment, applied, paymentMode, request, role, paidOn, receipt, admission));
                remaining = remaining.subtract(applied);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("Payment exceeds pending installment totals.");
            }
            return payments;
        }

        BigDecimal reversalRemaining = remaining.abs();
        List<FeeInstallment> reversalOrder = new ArrayList<>(installments);
        java.util.Collections.reverse(reversalOrder);
        for (FeeInstallment installment : reversalOrder) {
            if (reversalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal amountPaid = installment.getAmountPaid() == null ? BigDecimal.ZERO : installment.getAmountPaid();
            if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal applied = reversalRemaining.min(amountPaid);
            payments.add(createGroupedAllocation(paymentGroupId, installment, applied.negate(), paymentMode, request, role, paidOn, receipt, admission));
            reversalRemaining = reversalRemaining.subtract(applied);
        }
        if (reversalRemaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Reversal exceeds paid installment totals.");
        }
        return payments;
    }

    private FeeInstallmentPayment createGroupedAllocation(String paymentGroupId,
                                                          FeeInstallment installment,
                                                          BigDecimal appliedAmount,
                                                          PaymentModeMaster paymentMode,
                                                          PartialPaymentRequest request,
                                                          String role,
                                                          LocalDate paidOn,
                                                          UploadRequest receipt,
                                                          Admission2 admission) {
        BigDecimal currentPaid = installment.getAmountPaid() == null ? BigDecimal.ZERO : installment.getAmountPaid();
        BigDecimal newPaid = currentPaid.add(appliedAmount);
        installment.setAmountPaid(newPaid);
        BigDecimal amountDue = installment.getAmountDue() == null ? BigDecimal.ZERO : installment.getAmountDue();
        boolean verified = isRoleOneOf(role, "HO");
        String computedStatus;
        if (newPaid.compareTo(BigDecimal.ZERO) <= 0) {
            computedStatus = "Un Paid";
            installment.setPaidOn(null);
        } else if (newPaid.compareTo(amountDue) >= 0 && amountDue.compareTo(BigDecimal.ZERO) > 0) {
            computedStatus = "Paid";
            installment.setPaidOn(paidOn);
        } else {
            computedStatus = "Partial Received";
            installment.setPaidOn(paidOn);
        }
        installment.setStatus(verified ? computedStatus : "Under Verification");
        installment.setIsVerified(verified);
        installmentRepo.save(installment);

        String paymentStatus = verified ? "Paid" : "Under Verification";
        FeeInstallmentPayment payment = FeeInstallmentPayment.builder()
                .installment(installment)
                .amount(appliedAmount)
                .paymentGroupId(paymentGroupId)
                .paymentMode(paymentMode)
                .txnRef(request.getTxnRef())
                .remarks(request.getRemarks())
                .receivedBy(request.getReceivedBy())
                .status(paymentStatus)
                .isVerified(verified)
                .verifiedBy(verified ? request.getReceivedBy() : null)
                .verifiedAt(verified ? LocalDateTime.now() : null)
                .isAccountHeadVerified(Boolean.FALSE)
                .accountHeadVerifiedAt(null)
                .paidOn(paidOn)
                .build();
        payment = paymentRepo.save(payment);

        if (payment.getAmount() != null
                && payment.getAmount().compareTo(BigDecimal.ZERO) > 0
                && !hasAllocationInvoiceForPayment(payment.getPaymentId())) {
            invoiceService.generateInvoiceForPayment(admission, installment, payment);
        }
        if (receipt != null && StringUtils.hasText(receipt.getStorageUrl())) {
            uploadRepo.save(buildPaymentReceiptUpload(admission, payment, receipt));
        }
        return payment;
    }

    private FileUpload buildPaymentReceiptUpload(Admission2 admission, FeeInstallmentPayment payment, UploadRequest receipt) {
        return FileUpload.builder()
                .admission(admission)
                .installment(payment.getInstallment())
                .installmentPayment(payment)
                .filename(receipt.getFilename())
                .mimeType(receipt.getMimeType())
                .sizeBytes(receipt.getSizeBytes())
                .storageUrl(receipt.getStorageUrl())
                .sha256(receipt.getSha256())
                .label(StringUtils.hasText(receipt.getLabel()) ? receipt.getLabel() : "PARTIAL_RECEIPT")
                .build();
    }

    private void deletePaymentGroupInternal(List<FeeInstallmentPayment> groupPayments,
                                            boolean deletePhysicalReceiptFiles,
                                            boolean preserveReceiptFiles) {
        List<Long> paymentIds = groupPayments.stream().map(FeeInstallmentPayment::getPaymentId).toList();
        List<FileUpload> uploads = uploadRepo.findByInstallmentPayment_PaymentIdIn(paymentIds);
        if (deletePhysicalReceiptFiles && !preserveReceiptFiles) {
            for (FileUpload upload : uploads) {
                safeDeleteLocalFile(upload.getStorageUrl());
            }
        }
        uploadRepo.deleteAll(uploads);

        List<FeeInvoice> invoices = invoiceRepo.findByPayment_PaymentIdIn(paymentIds);
        for (FeeInvoice invoice : invoices) {
            safeDeleteLocalFile(invoice.getFilePath());
        }
        invoiceRepo.deleteAll(invoices);

        List<FeeInstallment> impactedInstallments = groupPayments.stream()
                .map(FeeInstallmentPayment::getInstallment)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        paymentRepo.deleteAll(groupPayments);
        for (FeeInstallment installment : impactedInstallments) {
            recalculateInstallmentFromPayments(installment);
        }
    }

    private Map<Long, Map<String, Object>> snapshotInstallments(List<FeeInstallment> installments) {
        Map<Long, Map<String, Object>> snapshots = new LinkedHashMap<>();
        for (FeeInstallment installment : installments) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("amountPaid", installment.getAmountPaid());
            snapshot.put("status", installment.getStatus());
            snapshot.put("isVerified", installment.getIsVerified());
            snapshot.put("paidOn", installment.getPaidOn());
            snapshots.put(installment.getInstallmentId(), snapshot);
        }
        return snapshots;
    }

    private Map<String, Object> buildInstallmentDiffPayload(Map<Long, Map<String, Object>> beforeSnapshots,
                                                            List<FeeInstallment> afterInstallments) {
        Map<String, Object> changedFields = new LinkedHashMap<>();
        List<Map<String, Object>> installmentChanges = new ArrayList<>();
        for (FeeInstallment installment : afterInstallments) {
            Map<String, Object> before = beforeSnapshots.get(installment.getInstallmentId());
            if (before == null) {
                continue;
            }
            Map<String, Object> fieldChanges = new LinkedHashMap<>();
            addChange(fieldChanges, "amountPaid", before.get("amountPaid"), installment.getAmountPaid());
            addChange(fieldChanges, "status", before.get("status"), installment.getStatus());
            addChange(fieldChanges, "isVerified", before.get("isVerified"), installment.getIsVerified());
            addChange(fieldChanges, "paidOn", before.get("paidOn"), installment.getPaidOn());
            if (!fieldChanges.isEmpty()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("installmentId", installment.getInstallmentId());
                payload.put("changes", fieldChanges);
                installmentChanges.add(payload);
            }
        }
        if (!installmentChanges.isEmpty()) {
            changedFields.put("installments", installmentChanges);
        }
        return changedFields;
    }

    private BigDecimal sumPaymentAmounts(List<FeeInstallmentPayment> payments) {
        return payments.stream()
                .map(FeeInstallmentPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolvePaymentModeCode(List<FeeInstallmentPayment> payments) {
        return payments.stream()
                .map(FeeInstallmentPayment::getPaymentMode)
                .filter(Objects::nonNull)
                .map(PaymentModeMaster::getCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private LocalDate resolvePaidOn(List<FeeInstallmentPayment> payments) {
        return payments.stream()
                .map(FeeInstallmentPayment::getPaidOn)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String firstNonBlank(List<String> values) {
        return values.stream().filter(StringUtils::hasText).findFirst().orElse(null);
    }

    private boolean isRoleOneOf(String role, String... roles) {
        if (!StringUtils.hasText(role) || roles == null || roles.length == 0) {
            return false;
        }
        for (String candidate : roles) {
            if (role.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void recalculateInstallmentFromPayments(FeeInstallment installment) {
        List<FeeInstallmentPayment> payments = paymentRepo.findByInstallment_InstallmentIdOrderByCreatedAtAsc(
                installment.getInstallmentId());
        java.math.BigDecimal totalPaid = java.math.BigDecimal.ZERO;
        java.math.BigDecimal verifiedPaid = java.math.BigDecimal.ZERO;
        boolean hasUnverified = false;
        LocalDate latestPaidOn = null;
        for (FeeInstallmentPayment p : payments) {
            java.math.BigDecimal amt = p.getAmount() != null ? p.getAmount() : java.math.BigDecimal.ZERO;
            totalPaid = totalPaid.add(amt);
            if (Boolean.TRUE.equals(p.getIsVerified())) {
                verifiedPaid = verifiedPaid.add(amt);
            } else {
                hasUnverified = true;
            }
            if (p.getPaidOn() != null && (latestPaidOn == null || p.getPaidOn().isAfter(latestPaidOn))) {
                latestPaidOn = p.getPaidOn();
            }
        }

        installment.setAmountPaid(totalPaid);
        java.math.BigDecimal amountDue = installment.getAmountDue() != null ? installment.getAmountDue() : java.math.BigDecimal.ZERO;
        if (totalPaid.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            installment.setStatus("Un Paid");
            installment.setIsVerified(false);
            installment.setPaidOn(null);
        } else if (hasUnverified) {
            installment.setStatus("Under Verification");
            installment.setIsVerified(false);
            installment.setPaidOn(latestPaidOn);
        } else if (verifiedPaid.compareTo(amountDue) >= 0 && amountDue.compareTo(java.math.BigDecimal.ZERO) > 0) {
            installment.setStatus("Paid");
            installment.setIsVerified(true);
            installment.setPaidOn(latestPaidOn);
        } else {
            installment.setStatus("Partial Received");
            installment.setIsVerified(verifiedPaid.compareTo(java.math.BigDecimal.ZERO) > 0);
            installment.setPaidOn(latestPaidOn);
        }
        installmentRepo.save(installment);
    }

    private void addChange(Map<String, Object> changes, String field, Object before, Object after) {
        if (java.util.Objects.equals(before, after)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("label", field);
        payload.put("before", before);
        payload.put("after", after);
        changes.put(field, payload);
    }

    private String resolveAuditActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String nameClaim = jwt.getClaimAsString("name");
                if (StringUtils.hasText(nameClaim)) {
                    return nameClaim;
                }
                String preferred = jwt.getClaimAsString("preferred_username");
                if (StringUtils.hasText(preferred)) {
                    return preferred;
                }
                String email = jwt.getClaimAsString("email");
                if (StringUtils.hasText(email)) {
                    return email;
                }
            }
            String name = auth.getName();
            if (StringUtils.hasText(name) && !"anonymousUser".equalsIgnoreCase(name)) {
                return name;
            }
        }
        return null;
    }
}
