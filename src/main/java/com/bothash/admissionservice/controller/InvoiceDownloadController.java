package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;

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
}
