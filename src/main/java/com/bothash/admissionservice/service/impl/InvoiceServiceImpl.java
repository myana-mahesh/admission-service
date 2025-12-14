package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl {

    private final FeeInvoiceRepository invoiceRepo;
   // private final EmailService emailService; // you already have some mail sender

    @Value("${invoice.storage.base}")
    private String invoiceBasePath;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    /**
     * Idempotent: if invoice already exists for this installment,
     * just return the existing one.
     */
    public FeeInvoice generateInvoiceForInstallment(Admission2 admission,
                                                    FeeInstallment inst) {
        // If invoice already exists, just return it
        if (invoiceRepo.existsByInstallment_InstallmentId(inst.getInstallmentId())) {
            return invoiceRepo.findByInstallment_InstallmentId(inst.getInstallmentId())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        try {
            String invoiceNumber = "INV-" + admission.getAdmissionId()
                    + "-" + inst.getStudyYear()
                    + "-" + inst.getInstallmentNo();

            byte[] pdfBytes = buildInvoicePdfBytes(admission, inst, invoiceNumber);

            Path dir = Paths.get(invoiceBasePath,
                    String.valueOf(admission.getAdmissionId()));
            Files.createDirectories(dir);

            String fileName = invoiceNumber + ".pdf";
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, pdfBytes);

            String downloadUrl = appBaseUrl +
                    "/api/invoices/download/" + admission.getAdmissionId() + "/" + fileName;

            FeeInvoice inv = new FeeInvoice();
            inv.setInstallment(inst);
            inv.setInvoiceNumber(invoiceNumber);
            inv.setFilePath(filePath.toString());
            inv.setDownloadUrl(downloadUrl);
            inv.setAmount(inst.getAmountPaid() != null
                    ? inst.getAmountPaid()
                    : inst.getAmountDue());

            FeeInvoice saved = invoiceRepo.save(inv);

            // Send mail (ignore errors)
//            try {
//                emailService.sendInvoiceEmail(
//                        admission.getStudent().getEmail(),
//                        admission.getStudent().getFullName(),
//                        downloadUrl,
//                        pdfBytes,
//                        fileName
//                );
//            } catch (Exception e) {
//                log.error("Failed to send invoice email for installment {}: {}",
//                        inst.getInstallmentId(), e.getMessage(), e);
//            }

            return saved;
        } catch (Exception ex) {
            log.error("Error generating invoice for installment {}: {}",
                    inst.getInstallmentId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to generate invoice", ex);
        }
    }

    private byte[] buildInvoicePdfBytes(Admission2 admission,
                                        FeeInstallment inst,
                                        String invoiceNumber) {
        // TODO: replace with proper PDF library; this is a stub
        String content = """
                Invoice: %s
                Student: %s
                Course: %s
                Study Year: %d
                Installment No: %d
                Amount Due: %s
                Amount Paid: %s
                Status: %s
                Due Date: %s
                Paid On: %s
                """.formatted(
                invoiceNumber,
                admission.getStudent().getFullName(),
                admission.getCourse().getName(),
                inst.getStudyYear(),
                inst.getInstallmentNo(),
                safe(inst.getAmountDue()),
                safe(inst.getAmountPaid()),
                inst.getStatus(),
                inst.getDueDate() != null ? inst.getDueDate().format(DateTimeFormatter.ISO_DATE) : "N/A",
                inst.getPaidOn() != null ? inst.getPaidOn().format(DateTimeFormatter.ISO_DATE) : "N/A"
        );
        return content.getBytes();
    }

    private String safe(BigDecimal v) {
        return v != null ? v.toPlainString() : "0.00";
    }
}
