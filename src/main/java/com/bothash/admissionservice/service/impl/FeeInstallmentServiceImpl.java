package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.entity.FileUpload;
import com.bothash.admissionservice.repository.FeeInstallmentRepository;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import com.bothash.admissionservice.repository.FileUploadRepository;

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

    @Transactional
    public FeeInstallment updateStatus(Long installmentId, String newStatus) {
        FeeInstallment inst = installmentRepo.findById(installmentId)
                .orElseThrow(() -> new IllegalArgumentException("Installment not found: " + installmentId));

        String oldStatus = "";
        inst.setStatus(newStatus);
        // set paidOn if becoming Paid (optional)
        if ("Paid".equalsIgnoreCase(newStatus) && inst.getPaidOn() == null) {
            inst.setPaidOn(java.time.LocalDate.now());
            inst.setIsVerified(true);
        }

        FeeInstallment saved = installmentRepo.save(inst);

        // Only when changing from non-paid → Paid
        if ("Paid".equalsIgnoreCase(newStatus)) {
            Admission2 admission = saved.getAdmission();
            FeeInvoice invoice = invoiceService.generateInvoiceForInstallment(admission, saved);
            log.info("Generated invoice {} for installment {}", invoice.getInvoiceNumber(), installmentId);
        }

        return saved;
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
