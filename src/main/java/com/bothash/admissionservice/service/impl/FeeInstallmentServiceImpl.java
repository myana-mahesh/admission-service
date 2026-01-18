package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.FileUpload;
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
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeInstallmentServiceImpl {

    private final FeeInstallmentRepository installmentRepo;
    private final InvoiceServiceImpl invoiceService;
    
    private final FeeInvoiceRepository invoiceRepo;
    private final FileUploadRepository uploadRepo;
    private final FeeInstallmentPaymentRepository paymentRepo;

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
        if (!invoiceRepo.existsByPayment_PaymentId(paymentId)) {
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
    public void deleteInstallment(Long installmentId, boolean deleteFilesAlso) {

        FeeInstallment inst = installmentRepo.findById(installmentId)
                .orElseThrow(() -> new EntityNotFoundException("FeeInstallment not found: " + installmentId));

        Long admissionId = inst.getAdmission().getAdmissionId();
        Integer studyYear = inst.getStudyYear();
        
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
    }

    private void safeDeleteLocalFile(String filePath) {
        try {
            if (filePath == null || filePath.isBlank()) return;
            Path p = Paths.get(filePath);
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
            // log if you want
        }
    }

    private void safeDeleteFromStorageUrl(String storageUrl) {
        try {
            if (storageUrl == null || storageUrl.isBlank()) return;

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
}
