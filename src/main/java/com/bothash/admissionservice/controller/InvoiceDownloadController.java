package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.dto.FeeInvoiceDto;
import com.bothash.admissionservice.entity.AdmissionOtherPayment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.MiscPayment;
import com.bothash.admissionservice.repository.AdmissionOtherPaymentRepository;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import com.bothash.admissionservice.repository.FeeInstallmentPaymentRepository;
import com.bothash.admissionservice.repository.MiscPaymentRepository;
import com.bothash.admissionservice.service.impl.InvoiceServiceImpl;
import com.bothash.admissionservice.service.impl.R2InvoiceStorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceDownloadController {

    private final FeeInvoiceRepository invoiceRepo;
    private final FeeInstallmentPaymentRepository paymentRepo;
    private final AdmissionOtherPaymentRepository admissionOtherPaymentRepository;
    private final MiscPaymentRepository miscPaymentRepository;
    private final InvoiceServiceImpl invoiceService;
    private final R2InvoiceStorageService r2InvoiceStorageService;

    @GetMapping("/download/{admissionId}/{fileName}")
    @Transactional
    public ResponseEntity<FileSystemResource> downloadInvoice(
            @PathVariable Long admissionId,
            @PathVariable String fileName) {

        FeeInvoice invoice = invoiceRepo.findAll().stream()
                .filter(inv -> inv.getFilePath().endsWith(File.separator + fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        invoice = refreshLegacyPaymentInvoiceIfRequired(invoice);

        File file = new File(invoice.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(fileName).build());
        headers.setContentType(MediaType.APPLICATION_PDF);

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    private FeeInvoice refreshLegacyPaymentInvoiceIfRequired(FeeInvoice invoice) {
        if (invoice == null || invoice.getPayment() == null) {
            return invoice;
        }
        if (Boolean.TRUE.equals(invoice.getReceiptDateSynced())) {
            return invoice;
        }

        FeeInstallmentPayment payment = invoice.getPayment();
        if (payment.getInstallment() == null || payment.getInstallment().getAdmission() == null) {
            return invoice;
        }

        if (StringUtils.hasText(payment.getPaymentGroupId())
                && InvoiceServiceImpl.isPaymentGroupInvoiceNumber(invoice.getInvoiceNumber())) {
            List<FeeInstallmentPayment> groupPayments = paymentRepo
                    .findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(payment.getPaymentGroupId());
            if (!groupPayments.isEmpty()) {
                return invoiceService.generateInvoiceForPaymentGroup(
                        payment.getInstallment().getAdmission(),
                        groupPayments,
                        payment.getPaymentGroupId()
                );
            }
        }

        return invoiceService.generateInvoiceForPayment(
                payment.getInstallment().getAdmission(),
                payment.getInstallment(),
                payment
        );
    }

    @GetMapping("/download-other-payment/{admissionId}/{fileName}")
    public ResponseEntity<FileSystemResource> downloadOtherPaymentInvoice(
            @PathVariable Long admissionId,
            @PathVariable String fileName) {

        AdmissionOtherPayment payment = admissionOtherPaymentRepository.findByAdmissionAdmissionId(admissionId).stream()
                .filter(p -> p.getInvoiceFilePath() != null && p.getInvoiceFilePath().endsWith(File.separator + fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        File invoiceFile = new File(payment.getInvoiceFilePath());

        if (!invoiceFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(invoiceFile);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(fileName).build());
        headers.setContentType(MediaType.APPLICATION_PDF);

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    @GetMapping("/download-misc-payment/{paymentId}/{fileName}")
    public ResponseEntity<FileSystemResource> downloadMiscPaymentInvoice(
            @PathVariable Long paymentId,
            @PathVariable String fileName) {

        MiscPayment payment = miscPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        if (!StringUtils.hasText(payment.getInvoiceFilePath())
                || !payment.getInvoiceFilePath().endsWith(File.separator + fileName)) {
            throw new IllegalArgumentException("Invoice not found");
        }

        File invoiceFile = new File(payment.getInvoiceFilePath());

        if (!invoiceFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(invoiceFile);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(fileName).build());
        headers.setContentType(MediaType.APPLICATION_PDF);

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    @GetMapping("/r2/{token}")
    public ResponseEntity<Void> downloadR2Invoice(@PathVariable String token) {
        if (!r2InvoiceStorageService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        String key;
        try {
            key = r2InvoiceStorageService.decodeKey(token);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
        if (!StringUtils.hasText(key) || !r2InvoiceStorageService.exists(key)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, r2InvoiceStorageService.presignGet(key).toString())
                .build();
    }
    
    @GetMapping("/by-admission/{admissionId}")
    public ResponseEntity<List<FeeInvoiceDto>> getInvoicesByAdmission(
            @PathVariable Long admissionId) {

        List<FeeInvoice> invoices =
                invoiceRepo.findByInstallment_Admission_AdmissionId(admissionId);

        List<FeeInvoiceDto> dtos = invoices.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
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
