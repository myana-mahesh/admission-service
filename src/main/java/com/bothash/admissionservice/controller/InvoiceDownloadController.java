package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.dto.FeeInvoiceDto;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceDownloadController {

    private final FeeInvoiceRepository invoiceRepo;

    @GetMapping("/download/{admissionId}/{fileName}")
    public ResponseEntity<FileSystemResource> downloadInvoice(
            @PathVariable Long admissionId,
            @PathVariable String fileName) {

        FeeInvoice invoice = invoiceRepo.findAll().stream()
                .filter(inv -> inv.getFilePath().endsWith(File.separator + fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

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
