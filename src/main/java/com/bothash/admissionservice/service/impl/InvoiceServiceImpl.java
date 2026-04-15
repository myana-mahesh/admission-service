package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.AdmissionOtherPayment;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.entity.MiscPayment;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 👉 Use OpenPDF (or iText) – adjust package if you use iText
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl {

	public static final String PAYMENT_GROUP_INVOICE_PREFIX = "INV-GP-";
	public static final String INVOICE_FORMAT_INSTALLMENT = "INSTALLMENT";
	public static final String INVOICE_FORMAT_PAYMENT_V1_DETAILED = "PAYMENT_V1_DETAILED";
	public static final String INVOICE_FORMAT_PAYMENT_V2_SIMPLIFIED = "PAYMENT_V2_SIMPLIFIED";
	public static final String INVOICE_FORMAT_OTHER_PAYMENT = "OTHER_PAYMENT";
	public static final String INVOICE_FORMAT_MISC_PAYMENT = "MISC_PAYMENT";

	private final FeeInvoiceRepository invoiceRepo;
	private final R2InvoiceStorageService r2InvoiceStorageService;
	// private final EmailService emailService;

	@Value("${invoice.storage.base}")
	private String invoiceBasePath;

	@Value("${app.base-url}")
	private String appBaseUrl;

	/**
	 * Idempotent: if invoice already exists for this installment, just return the
	 * existing one.
	 */
	public FeeInvoice generateInvoiceForInstallment(Admission2 admission, FeeInstallment inst) {
		// If invoice already exists, just return it
		List<FeeInvoice> invs = new ArrayList<>();
		if (invoiceRepo.existsByInstallment_InstallmentId(inst.getInstallmentId())) {
			invs =  invoiceRepo.findByInstallment_InstallmentId(inst.getInstallmentId());
					
		}

		try {
			String invoiceNumber = "INV-" + admission.getAdmissionId() + "-" + inst.getStudyYear() + "-"
					+ inst.getInstallmentNo();

			// ✅ REAL PDF BYTES NOW
			byte[] pdfBytes = buildInvoicePdfBytes(admission, inst, invoiceNumber);

			String fileName = invoiceNumber + ".pdf";
			String filePathValue;
			String downloadUrl;
			if (r2InvoiceStorageService.isEnabled()) {
				String key = "invoices/" + admission.getAdmissionId() + "/" + fileName;
				r2InvoiceStorageService.upload(key, pdfBytes);
				filePathValue = r2InvoiceStorageService.marker(key);
				downloadUrl = appBaseUrl + "/api/invoices/r2/" + r2InvoiceStorageService.encodeKey(key);
			} else {
				Path dir = Paths.get(invoiceBasePath, String.valueOf(admission.getAdmissionId()));
				Files.createDirectories(dir);
				Path filePath = dir.resolve(fileName);
				Files.write(filePath, pdfBytes);
				filePathValue = filePath.toString();
				downloadUrl = appBaseUrl + "/api/invoices/download/" + admission.getAdmissionId() + "/" + fileName;
			}

			FeeInvoice inv = new FeeInvoice();
			if(invs!=null && !invs.isEmpty()) {
				inv = invs.get(0);
			}
			inv.setInstallment(inst);
			inv.setInvoiceNumber(invoiceNumber);
			inv.setFilePath(filePathValue);
			inv.setDownloadUrl(downloadUrl);
			inv.setAmount(inst.getAmountPaid() != null ? inst.getAmountPaid() : inst.getAmountDue());
			inv.setInvoiceFormat(INVOICE_FORMAT_INSTALLMENT);
			inv.setReceiptDateSynced(Boolean.TRUE);

			FeeInvoice saved = invoiceRepo.save(inv);

			// (Optional) send email with pdfBytes...

			return saved;
		} catch (Exception ex) {
			log.error("Error generating invoice for installment {}: {}", inst.getInstallmentId(), ex.getMessage(), ex);
			throw new RuntimeException("Failed to generate invoice", ex);
		}
	}

	public FeeInvoice generateInvoiceForPayment(Admission2 admission, FeeInstallment inst, FeeInstallmentPayment payment) {
		if (payment == null) {
			throw new IllegalArgumentException("Payment is required for invoice generation.");
		}
		try {
			String invoiceNumber = "INV-P-" + admission.getAdmissionId() + "-" + payment.getPaymentId();
			byte[] pdfBytes = buildSimplePaymentInvoicePdfBytes(admission, payment, invoiceNumber);

			String fileName = invoiceNumber + ".pdf";
			String filePathValue;
			String downloadUrl;
			if (r2InvoiceStorageService.isEnabled()) {
				String key = "invoices/" + admission.getAdmissionId() + "/" + fileName;
				r2InvoiceStorageService.upload(key, pdfBytes);
				filePathValue = r2InvoiceStorageService.marker(key);
				downloadUrl = appBaseUrl + "/api/invoices/r2/" + r2InvoiceStorageService.encodeKey(key);
			} else {
				Path dir = Paths.get(invoiceBasePath, String.valueOf(admission.getAdmissionId()));
				Files.createDirectories(dir);
				Path filePath = dir.resolve(fileName);
				Files.write(filePath, pdfBytes);
				filePathValue = filePath.toString();
				downloadUrl = appBaseUrl + "/api/invoices/download/" + admission.getAdmissionId() + "/" + fileName;
			}

			FeeInvoice inv = invoiceRepo.findByInvoiceNumber(invoiceNumber).orElseGet(FeeInvoice::new);
			inv.setInstallment(inst);
			inv.setPayment(payment);
			inv.setInvoiceNumber(invoiceNumber);
			inv.setFilePath(filePathValue);
			inv.setDownloadUrl(downloadUrl);
			inv.setAmount(payment.getAmount());
			inv.setInvoiceFormat(INVOICE_FORMAT_PAYMENT_V2_SIMPLIFIED);
			inv.setReceiptDateSynced(Boolean.TRUE);

			return saveInvoiceByNumber(inv, invoiceNumber);
		} catch (Exception ex) {
			log.error("Error generating invoice for payment {}: {}", payment.getPaymentId(), ex.getMessage(), ex);
			throw new RuntimeException("Failed to generate payment invoice", ex);
		}
	}

	public FeeInvoice generateInvoiceForPaymentGroup(Admission2 admission,
			List<FeeInstallmentPayment> groupPayments,
			String paymentGroupId) {
		if (admission == null) {
			throw new IllegalArgumentException("Admission is required for payment group invoice generation.");
		}
		if (groupPayments == null || groupPayments.isEmpty()) {
			throw new IllegalArgumentException("Payment group is required for invoice generation.");
		}
		String normalizedPaymentGroupId = paymentGroupId != null ? paymentGroupId.trim() : null;
		if (normalizedPaymentGroupId == null || normalizedPaymentGroupId.isBlank()) {
			throw new IllegalArgumentException("Payment group id is required.");
		}
		try {
			List<FeeInstallmentPayment> sortedPayments = new ArrayList<>(groupPayments);
			sortedPayments.sort(Comparator
					.comparing(FeeInstallmentPayment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(FeeInstallmentPayment::getPaymentId, Comparator.nullsLast(Comparator.naturalOrder())));
			FeeInstallmentPayment anchorPayment = sortedPayments.get(0);
			FeeInstallment anchorInstallment = anchorPayment.getInstallment();
			String invoiceNumber = PAYMENT_GROUP_INVOICE_PREFIX + admission.getAdmissionId() + "-" + normalizedPaymentGroupId;
			byte[] pdfBytes = buildSimplePaymentGroupInvoicePdfBytes(admission, sortedPayments, invoiceNumber);

			String fileName = invoiceNumber + ".pdf";
			String filePathValue;
			String downloadUrl;
			if (r2InvoiceStorageService.isEnabled()) {
				String key = "invoices/" + admission.getAdmissionId() + "/" + fileName;
				r2InvoiceStorageService.upload(key, pdfBytes);
				filePathValue = r2InvoiceStorageService.marker(key);
				downloadUrl = appBaseUrl + "/api/invoices/r2/" + r2InvoiceStorageService.encodeKey(key);
			} else {
				Path dir = Paths.get(invoiceBasePath, String.valueOf(admission.getAdmissionId()));
				Files.createDirectories(dir);
				Path filePath = dir.resolve(fileName);
				Files.write(filePath, pdfBytes);
				filePathValue = filePath.toString();
				downloadUrl = appBaseUrl + "/api/invoices/download/" + admission.getAdmissionId() + "/" + fileName;
			}

			FeeInvoice inv = invoiceRepo.findByInvoiceNumber(invoiceNumber).orElseGet(FeeInvoice::new);
			inv.setInstallment(anchorInstallment);
			inv.setPayment(anchorPayment);
			inv.setInvoiceNumber(invoiceNumber);
			inv.setFilePath(filePathValue);
			inv.setDownloadUrl(downloadUrl);
			inv.setAmount(sortedPayments.stream()
					.map(FeeInstallmentPayment::getAmount)
					.filter(v -> v != null)
					.reduce(BigDecimal.ZERO, BigDecimal::add));
			inv.setInvoiceFormat(INVOICE_FORMAT_PAYMENT_V2_SIMPLIFIED);
			inv.setReceiptDateSynced(Boolean.TRUE);

			return saveInvoiceByNumber(inv, invoiceNumber);
		} catch (Exception ex) {
			log.error("Error generating invoice for payment group {}: {}", paymentGroupId, ex.getMessage(), ex);
			throw new RuntimeException("Failed to generate payment group invoice", ex);
		}
	}

	public AdmissionOtherPayment generateInvoiceForOtherPayment(Admission2 admission, AdmissionOtherPayment payment) {
		if (payment == null) {
			throw new IllegalArgumentException("Other payment is required for invoice generation.");
		}
		if (payment.getReferencePayment() != null) {
			return payment;
		}
		try {
			String invoiceNumber = "INV-OP-" + admission.getAdmissionId() + "-" + payment.getPaymentId();
			byte[] pdfBytes = buildOtherPaymentInvoicePdfBytes(admission, payment, invoiceNumber);

			String fileName = invoiceNumber + ".pdf";
			String filePathValue;
			String downloadUrl;
			if (r2InvoiceStorageService.isEnabled()) {
				String key = "invoices/" + admission.getAdmissionId() + "/" + fileName;
				r2InvoiceStorageService.upload(key, pdfBytes);
				filePathValue = r2InvoiceStorageService.marker(key);
				downloadUrl = appBaseUrl + "/api/invoices/r2/" + r2InvoiceStorageService.encodeKey(key);
			} else {
				Path dir = Paths.get(invoiceBasePath, String.valueOf(admission.getAdmissionId()));
				Files.createDirectories(dir);
				Path filePath = dir.resolve(fileName);
				Files.write(filePath, pdfBytes);
				filePathValue = filePath.toString();
				downloadUrl = appBaseUrl + "/api/invoices/download-other-payment/"
						+ admission.getAdmissionId() + "/" + fileName;
			}

			payment.setInvoiceNumber(invoiceNumber);
			payment.setInvoiceFilePath(filePathValue);
			payment.setInvoiceDownloadUrl(downloadUrl);
			return payment;
		} catch (Exception ex) {
			log.error("Error generating invoice for other payment {}: {}", payment.getPaymentId(), ex.getMessage(), ex);
			throw new RuntimeException("Failed to generate other payment invoice", ex);
		}
	}

	public MiscPayment generateInvoiceForMiscPayment(MiscPayment payment) {
		if (payment == null) {
			throw new IllegalArgumentException("Miscellaneous payment is required for invoice generation.");
		}
		if (payment.getPaymentId() == null) {
			throw new IllegalArgumentException("Miscellaneous payment id is required for invoice generation.");
		}
		try {
			String invoiceNumber = "INV-MP-" + payment.getPaymentId();
			byte[] pdfBytes = buildMiscPaymentInvoicePdfBytes(payment, invoiceNumber);

			String fileName = invoiceNumber + ".pdf";
			String filePathValue;
			String downloadUrl;
			if (r2InvoiceStorageService.isEnabled()) {
				String key = "misc-payments/invoices/" + payment.getPaymentId() + "/" + fileName;
				r2InvoiceStorageService.upload(key, pdfBytes);
				filePathValue = r2InvoiceStorageService.marker(key);
				downloadUrl = appBaseUrl + "/api/invoices/r2/" + r2InvoiceStorageService.encodeKey(key);
			} else {
				Path dir = Paths.get(invoiceBasePath, "misc-payments", "invoices", String.valueOf(payment.getPaymentId()));
				Files.createDirectories(dir);
				Path filePath = dir.resolve(fileName);
				Files.write(filePath, pdfBytes);
				filePathValue = filePath.toString();
				downloadUrl = appBaseUrl + "/api/invoices/download-misc-payment/"
						+ payment.getPaymentId() + "/" + fileName;
			}

			payment.setInvoiceNumber(invoiceNumber);
			payment.setInvoiceFilePath(filePathValue);
			payment.setInvoiceDownloadUrl(downloadUrl);
			return payment;
		} catch (Exception ex) {
			log.error("Error generating invoice for miscellaneous payment {}: {}", payment.getPaymentId(), ex.getMessage(), ex);
			throw new RuntimeException("Failed to generate miscellaneous payment invoice", ex);
		}
	}

	private byte[] buildInvoicePdfBytes(Admission2 admission, FeeInstallment inst, String invoiceNumber) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 36, 36, 60, 36); // margins: left, right, top, bottom

		try {
			PdfWriter.getInstance(document, baos);
			document.open();

// ---------- Fonts ----------
			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
			Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
			Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
			Font smallNoteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

// ========== 1. COLLEGE HEADER ==========
			Paragraph collegeName = new Paragraph("ABS EDUCATIONAL SOLUTION", titleFont);
			collegeName.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeName);

			Paragraph collegeLine2 = new Paragraph("Authorised Admission & Education Solution Centre", valueFont);
			collegeLine2.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeLine2);

			Paragraph collegeContact = new Paragraph("Mobile: +91 98332 11999  |  Email: info@absedu.example",
					valueFont);
			collegeContact.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeContact);

			document.add(Chunk.NEWLINE);

			Paragraph receiptTitle = new Paragraph("FEE RECEIPT / INVOICE", subTitleFont);
			receiptTitle.setAlignment(Element.ALIGN_CENTER);
			document.add(receiptTitle);

			document.add(Chunk.NEWLINE);

// ========== 2. INVOICE META TABLE ==========
			PdfPTable metaTable = new PdfPTable(2);
			metaTable.setWidthPercentage(100);
			metaTable.setWidths(new float[] { 1.2f, 1.8f });

			metaTable.addCell(labelCell("Invoice No."));
			metaTable.addCell(valueCell(invoiceNumber, valueFont));

			metaTable.addCell(labelCell("Invoice Date"));
			metaTable.addCell(valueCell(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_DATE),
					valueFont));

			metaTable.addCell(labelCell("Admission ID"));
			metaTable.addCell(valueCell(
					admission.getAdmissionId() != null ? admission.getAdmissionId().toString() : "-", valueFont));

			document.add(metaTable);
			document.add(Chunk.NEWLINE);

// ========== 3. STUDENT & COURSE DETAILS ==========
			PdfPTable studentTable = new PdfPTable(2);
			studentTable.setWidthPercentage(100);
			studentTable.setWidths(new float[] { 1.2f, 1.8f });

			studentTable.addCell(labelCell("Student Name"));
			studentTable.addCell(valueCell(admission.getStudent().getFullName(), valueFont));

			studentTable.addCell(labelCell("ABS ID"));
			studentTable.addCell(valueCell(
					admission.getStudent().getAbsId() != null ? admission.getStudent().getAbsId() : "-", valueFont));

			studentTable.addCell(labelCell("Course"));
			studentTable.addCell(valueCell(admission.getCourse().getName(), valueFont));

			studentTable.addCell(labelCell("Study Year"));
			studentTable.addCell(valueCell(String.valueOf(inst.getStudyYear()), valueFont));

			studentTable.addCell(labelCell("Installment No."));
			studentTable.addCell(valueCell(String.valueOf(inst.getInstallmentNo()), valueFont));

			document.add(studentTable);
			document.add(Chunk.NEWLINE);

// ========== 4. FEES TABLE (LIKE A RECEIPT LINE ITEM) ==========
			PdfPTable feeTable = new PdfPTable(5);
			feeTable.setWidthPercentage(100);
			feeTable.setWidths(new float[] { 2.5f, 1.0f, 1.0f, 1.2f, 1.3f });

// Header row
			feeTable.addCell(headerCell("Description"));
			feeTable.addCell(headerCell("Year"));
			feeTable.addCell(headerCell("Inst. No."));
			feeTable.addCell(headerCell("Due Date"));
			feeTable.addCell(headerCell("Amount (₹)"));

// Data row (single installment as one line item)
			String desc = "Tuition Fees Installment";

			java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ISO_DATE;

			feeTable.addCell(valueCell(desc, valueFont));
			feeTable.addCell(valueCell(String.valueOf(inst.getStudyYear()), valueFont));
			feeTable.addCell(valueCell(String.valueOf(inst.getInstallmentNo()), valueFont));
			feeTable.addCell(valueCell(inst.getDueDate() != null ? inst.getDueDate().format(df) : "-", valueFont));

// Here we show the *paid* amount prominently, fallback to due
			java.math.BigDecimal paid = inst.getAmountPaid() != null &&  inst.getAmountPaid().doubleValue()>0.0 ? inst.getAmountPaid() : inst.getAmountDue();

			feeTable.addCell(valueCell("₹ " + safe(paid), valueFont));

			document.add(feeTable);
			document.add(Chunk.NEWLINE);

// ========== 5. SUMMARY BOX (TOTAL / PAID / BALANCE) ==========
			java.math.BigDecimal totalDue = inst.getAmountDue() != null ? inst.getAmountDue()
					: java.math.BigDecimal.ZERO;
			java.math.BigDecimal totalPaid = paid;
			java.math.BigDecimal balance = totalDue.subtract(totalPaid);

			PdfPTable summaryTable = new PdfPTable(2);
			summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
			summaryTable.setWidthPercentage(40);
			summaryTable.setWidths(new float[] { 1.2f, 1.0f });

			summaryTable.addCell(labelCell("Total Due (₹)"));
			summaryTable.addCell(rightValueCell(safe(totalDue), valueFont));

			summaryTable.addCell(labelCell("Total Paid (₹)"));
			summaryTable.addCell(rightValueCell(safe(totalPaid), valueFont));

			summaryTable.addCell(labelCell("Balance (₹)"));
			summaryTable.addCell(rightValueCell(safe(balance), valueFont));

			document.add(summaryTable);
			document.add(Chunk.NEWLINE);

// ========== 6. PAYMENT / SIGNATURE SECTION ==========
			PdfPTable footerTable = new PdfPTable(1);
			footerTable.setWidthPercentage(100);

			PdfPCell footerCell = new PdfPCell();
			footerCell.setBorder(Rectangle.NO_BORDER);
			footerCell.addElement(new Paragraph(
					"Payment Mode: " + (inst.getPaymentMode() != null ? inst.getPaymentMode().getLabel() : "-"),
					valueFont));
			footerCell.addElement(new Paragraph(
					"Transaction Ref: " + (inst.getTxnRef() != null ? inst.getTxnRef() : "-"), valueFont));
			footerCell.addElement(new Paragraph(
					"Received By: " + (inst.getReceivedBy() != null ? inst.getReceivedBy() : "-"), valueFont));
			footerTable.addCell(footerCell);

			document.add(Chunk.NEWLINE);
			document.add(footerTable);

		} catch (Exception e) {
			throw new RuntimeException("Error while building invoice PDF", e);
		} finally {
			document.close(); // flush into baos
		}

		return baos.toByteArray();
	}

	private byte[] buildPaymentInvoicePdfBytes(Admission2 admission, FeeInstallment inst, FeeInstallmentPayment payment,
			String invoiceNumber) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 36, 36, 60, 36);

		try {
			PdfWriter.getInstance(document, baos);
			document.open();

			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
			Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
			Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

			Paragraph collegeName = new Paragraph("ABS EDUCATIONAL SOLUTION", titleFont);
			collegeName.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeName);

			Paragraph collegeLine2 = new Paragraph("Authorised Admission & Education Solution Centre", valueFont);
			collegeLine2.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeLine2);

			document.add(Chunk.NEWLINE);

			Paragraph receiptTitle = new Paragraph("PARTIAL PAYMENT RECEIPT", subTitleFont);
			receiptTitle.setAlignment(Element.ALIGN_CENTER);
			document.add(receiptTitle);

			document.add(Chunk.NEWLINE);

			PdfPTable metaTable = new PdfPTable(2);
			metaTable.setWidthPercentage(100);
			metaTable.setWidths(new float[] { 1.2f, 1.8f });

			metaTable.addCell(labelCell("Receipt No."));
			metaTable.addCell(valueCell(invoiceNumber, valueFont));

			metaTable.addCell(labelCell("Receipt Date"));
			metaTable.addCell(valueCell(formatReceiptDate(payment.getPaidOn()), valueFont));

			metaTable.addCell(labelCell("Admission ID"));
			metaTable.addCell(valueCell(
					admission.getAdmissionId() != null ? admission.getAdmissionId().toString() : "-", valueFont));

			document.add(metaTable);
			document.add(Chunk.NEWLINE);

			PdfPTable studentTable = new PdfPTable(2);
			studentTable.setWidthPercentage(100);
			studentTable.setWidths(new float[] { 1.2f, 1.8f });

			studentTable.addCell(labelCell("Student Name"));
			studentTable.addCell(valueCell(admission.getStudent().getFullName(), valueFont));

			studentTable.addCell(labelCell("ABS ID"));
			studentTable.addCell(valueCell(
					admission.getStudent().getAbsId() != null ? admission.getStudent().getAbsId() : "-", valueFont));

			studentTable.addCell(labelCell("Course"));
			studentTable.addCell(valueCell(admission.getCourse().getName(), valueFont));

			studentTable.addCell(labelCell("Study Year"));
			studentTable.addCell(valueCell(String.valueOf(inst.getStudyYear()), valueFont));

			studentTable.addCell(labelCell("Installment No."));
			studentTable.addCell(valueCell(String.valueOf(inst.getInstallmentNo()), valueFont));

			document.add(studentTable);
			document.add(Chunk.NEWLINE);

			PdfPTable feeTable = new PdfPTable(3);
			feeTable.setWidthPercentage(100);
			feeTable.setWidths(new float[] { 2.8f, 1.2f, 1.3f });

			feeTable.addCell(headerCell("Description"));
			feeTable.addCell(headerCell("Amount (₹)"));
			feeTable.addCell(headerCell("Payment Mode"));

			feeTable.addCell(valueCell("Partial Payment", valueFont));
			feeTable.addCell(valueCell("₹ " + safe(payment.getAmount()), valueFont));
			feeTable.addCell(valueCell(payment.getPaymentMode() != null ? payment.getPaymentMode().getLabel() : "-", valueFont));

			document.add(feeTable);
			document.add(Chunk.NEWLINE);

			PdfPTable footerTable = new PdfPTable(1);
			footerTable.setWidthPercentage(100);

			PdfPCell footerCell = new PdfPCell();
			footerCell.setBorder(Rectangle.NO_BORDER);
			footerCell.addElement(new Paragraph(
					"Transaction ID: " + (payment.getTxnRef() != null ? payment.getTxnRef() : "-"),
					valueFont));
			footerCell.addElement(new Paragraph(
					"Received By: " + (payment.getReceivedBy() != null ? payment.getReceivedBy() : "-"),
					valueFont));

			footerTable.addCell(footerCell);

			document.add(footerTable);
			document.add(Chunk.NEWLINE);

			document.close();
			return baos.toByteArray();
		} catch (DocumentException e) {
			throw new RuntimeException("Error building payment invoice PDF", e);
		}
	}

	private byte[] buildOtherPaymentInvoicePdfBytes(Admission2 admission, AdmissionOtherPayment payment,
			String invoiceNumber) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 36, 36, 60, 36);

		try {
			PdfWriter.getInstance(document, baos);
			document.open();

			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
			Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

			Paragraph collegeName = new Paragraph("ABS EDUCATIONAL SOLUTION", titleFont);
			collegeName.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeName);

			Paragraph collegeLine2 = new Paragraph("Authorised Admission & Education Solution Centre", valueFont);
			collegeLine2.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeLine2);

			document.add(Chunk.NEWLINE);

			Paragraph receiptTitle = new Paragraph("OTHER PAYMENT RECEIPT", subTitleFont);
			receiptTitle.setAlignment(Element.ALIGN_CENTER);
			document.add(receiptTitle);

			document.add(Chunk.NEWLINE);

			PdfPTable metaTable = new PdfPTable(2);
			metaTable.setWidthPercentage(100);
			metaTable.setWidths(new float[] { 1.2f, 1.8f });

			metaTable.addCell(labelCell("Receipt No."));
			metaTable.addCell(valueCell(invoiceNumber, valueFont));

			metaTable.addCell(labelCell("Receipt Date"));
			metaTable.addCell(valueCell(formatReceiptDate(payment.getPaidOn()), valueFont));

			metaTable.addCell(labelCell("Admission ID"));
			metaTable.addCell(valueCell(
					admission.getAdmissionId() != null ? admission.getAdmissionId().toString() : "-", valueFont));

			document.add(metaTable);
			document.add(Chunk.NEWLINE);

			PdfPTable studentTable = new PdfPTable(2);
			studentTable.setWidthPercentage(100);
			studentTable.setWidths(new float[] { 1.2f, 1.8f });

			studentTable.addCell(labelCell("Student Name"));
			studentTable.addCell(valueCell(
					admission.getStudent() != null ? admission.getStudent().getFullName() : "-", valueFont));

			studentTable.addCell(labelCell("ABS ID"));
			studentTable.addCell(valueCell(
					admission.getStudent() != null && admission.getStudent().getAbsId() != null
							? admission.getStudent().getAbsId()
							: "-",
					valueFont));

			studentTable.addCell(labelCell("Course"));
			studentTable.addCell(valueCell(
					admission.getCourse() != null ? admission.getCourse().getName() : "-", valueFont));

			studentTable.addCell(labelCell("Category"));
			studentTable.addCell(valueCell(
					payment.getCategory() != null ? payment.getCategory() : "Other Payment", valueFont));

			document.add(studentTable);
			document.add(Chunk.NEWLINE);

			PdfPTable feeTable = new PdfPTable(3);
			feeTable.setWidthPercentage(100);
			feeTable.setWidths(new float[] { 2.8f, 1.2f, 1.3f });

			feeTable.addCell(headerCell("Description"));
			feeTable.addCell(headerCell("Amount (INR)"));
			feeTable.addCell(headerCell("Payment Mode"));

			feeTable.addCell(valueCell(payment.getCategory() != null ? payment.getCategory() : "Other Payment", valueFont));
			feeTable.addCell(valueCell("INR " + safe(payment.getAmount()), valueFont));
			feeTable.addCell(valueCell(
					payment.getPaymentMode() != null ? payment.getPaymentMode().getLabel() : "-", valueFont));

			document.add(feeTable);
			document.add(Chunk.NEWLINE);

			PdfPTable footerTable = new PdfPTable(1);
			footerTable.setWidthPercentage(100);

			PdfPCell footerCell = new PdfPCell();
			footerCell.setBorder(Rectangle.NO_BORDER);
			footerCell.addElement(new Paragraph(
					"Transaction ID: " + (payment.getTxnRef() != null ? payment.getTxnRef() : "-"),
					valueFont));
			footerCell.addElement(new Paragraph(
					"Received By: " + (payment.getReceivedBy() != null ? payment.getReceivedBy() : "-"),
					valueFont));
			footerCell.addElement(new Paragraph(
					"Paid On: " + (payment.getPaidOn() != null ? payment.getPaidOn().toString() : "-"),
					valueFont));

			footerTable.addCell(footerCell);

			document.add(footerTable);
			document.add(Chunk.NEWLINE);

			document.close();
			return baos.toByteArray();
		} catch (DocumentException e) {
			throw new RuntimeException("Error building other payment invoice PDF", e);
		}
	}

	private byte[] buildMiscPaymentInvoicePdfBytes(MiscPayment payment, String invoiceNumber) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 36, 36, 60, 36);

		try {
			PdfWriter.getInstance(document, baos);
			document.open();

			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
			Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

			Paragraph collegeName = new Paragraph("ABS EDUCATIONAL SOLUTION", titleFont);
			collegeName.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeName);

			Paragraph collegeLine2 = new Paragraph("Authorised Admission & Education Solution Centre", valueFont);
			collegeLine2.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeLine2);

			document.add(Chunk.NEWLINE);

			Paragraph receiptTitle = new Paragraph("MISCELLANEOUS PAYMENT RECEIPT", subTitleFont);
			receiptTitle.setAlignment(Element.ALIGN_CENTER);
			document.add(receiptTitle);

			document.add(Chunk.NEWLINE);

			PdfPTable metaTable = new PdfPTable(2);
			metaTable.setWidthPercentage(100);
			metaTable.setWidths(new float[] { 1.2f, 1.8f });

			metaTable.addCell(labelCell("Receipt No."));
			metaTable.addCell(valueCell(invoiceNumber, valueFont));

			metaTable.addCell(labelCell("Receipt Date"));
			metaTable.addCell(valueCell(formatReceiptDate(payment.getPaymentDate()), valueFont));

			metaTable.addCell(labelCell("Payment ID"));
			metaTable.addCell(valueCell(String.valueOf(payment.getPaymentId()), valueFont));

			document.add(metaTable);
			document.add(Chunk.NEWLINE);

			PdfPTable studentTable = new PdfPTable(2);
			studentTable.setWidthPercentage(100);
			studentTable.setWidths(new float[] { 1.2f, 1.8f });

			studentTable.addCell(labelCell("Student Name"));
			studentTable.addCell(valueCell(payment.getStudentName(), valueFont));

			studentTable.addCell(labelCell("Contact Number"));
			studentTable.addCell(valueCell(payment.getContactNumber(), valueFont));

			studentTable.addCell(labelCell("Course"));
			studentTable.addCell(valueCell(payment.getCourseName(), valueFont));

			studentTable.addCell(labelCell("Batch"));
			studentTable.addCell(valueCell(payment.getBatch(), valueFont));

			studentTable.addCell(labelCell("College"));
			studentTable.addCell(valueCell(
					StringUtils.hasText(payment.getCollegeName()) ? payment.getCollegeName() : "-", valueFont));

			document.add(studentTable);
			document.add(Chunk.NEWLINE);

			PdfPTable feeTable = new PdfPTable(3);
			feeTable.setWidthPercentage(100);
			feeTable.setWidths(new float[] { 2.8f, 1.2f, 1.3f });

			feeTable.addCell(headerCell("Description"));
			feeTable.addCell(headerCell("Amount (INR)"));
			feeTable.addCell(headerCell("Payment Mode"));

			feeTable.addCell(valueCell(payment.getFeeType(), valueFont));
			feeTable.addCell(valueCell("INR " + safe(payment.getAmount()), valueFont));
			feeTable.addCell(valueCell(
					StringUtils.hasText(payment.getPaymentMode()) ? payment.getPaymentMode() : "-", valueFont));

			document.add(feeTable);
			document.add(Chunk.NEWLINE);

			PdfPTable footerTable = new PdfPTable(1);
			footerTable.setWidthPercentage(100);

			PdfPCell footerCell = new PdfPCell();
			footerCell.setBorder(Rectangle.NO_BORDER);
			footerCell.addElement(new Paragraph(
					"Remark: " + (StringUtils.hasText(payment.getRemark()) ? payment.getRemark() : "-"),
					valueFont));
			footerCell.addElement(new Paragraph(
					"Received By: " + (StringUtils.hasText(payment.getCreatedBy()) ? payment.getCreatedBy() : "-"),
					valueFont));
			footerCell.addElement(new Paragraph(
					"Paid On: " + (payment.getPaymentDate() != null ? payment.getPaymentDate().toString() : "-"),
					valueFont));

			footerTable.addCell(footerCell);

			document.add(footerTable);
			document.add(Chunk.NEWLINE);

			document.close();
			return baos.toByteArray();
		} catch (DocumentException e) {
			throw new RuntimeException("Error building miscellaneous payment invoice PDF", e);
		}
	}

	private byte[] buildPaymentGroupInvoicePdfBytes(Admission2 admission,
			List<FeeInstallmentPayment> groupPayments,
			String invoiceNumber) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 36, 36, 60, 36);

		try {
			PdfWriter.getInstance(document, baos);
			document.open();

			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
			Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
			Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

			Paragraph collegeName = new Paragraph("ABS EDUCATIONAL SOLUTION", titleFont);
			collegeName.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeName);

			Paragraph collegeLine2 = new Paragraph("Authorised Admission & Education Solution Centre", valueFont);
			collegeLine2.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeLine2);

			document.add(Chunk.NEWLINE);

			Paragraph receiptTitle = new Paragraph("PAYMENT RECEIPT", subTitleFont);
			receiptTitle.setAlignment(Element.ALIGN_CENTER);
			document.add(receiptTitle);

			document.add(Chunk.NEWLINE);

			PdfPTable metaTable = new PdfPTable(2);
			metaTable.setWidthPercentage(100);
			metaTable.setWidths(new float[] { 1.2f, 1.8f });
			metaTable.addCell(labelCell("Receipt No."));
			metaTable.addCell(valueCell(invoiceNumber, valueFont));
			metaTable.addCell(labelCell("Receipt Date"));
			metaTable.addCell(valueCell(formatReceiptDate(groupPayments.get(0).getPaidOn()), valueFont));
			metaTable.addCell(labelCell("Admission ID"));
			metaTable.addCell(valueCell(admission.getAdmissionId() != null ? admission.getAdmissionId().toString() : "-", valueFont));
			document.add(metaTable);
			document.add(Chunk.NEWLINE);

			PdfPTable studentTable = new PdfPTable(2);
			studentTable.setWidthPercentage(100);
			studentTable.setWidths(new float[] { 1.2f, 1.8f });
			studentTable.addCell(labelCell("Student Name"));
			studentTable.addCell(valueCell(admission.getStudent() != null ? admission.getStudent().getFullName() : "-", valueFont));
			studentTable.addCell(labelCell("ABS ID"));
			studentTable.addCell(valueCell(
					admission.getStudent() != null && admission.getStudent().getAbsId() != null
							? admission.getStudent().getAbsId()
							: "-",
					valueFont));
			studentTable.addCell(labelCell("Course"));
			studentTable.addCell(valueCell(admission.getCourse() != null ? admission.getCourse().getName() : "-", valueFont));
			document.add(studentTable);
			document.add(Chunk.NEWLINE);

			PdfPTable paymentTable = new PdfPTable(4);
			paymentTable.setWidthPercentage(100);
			paymentTable.setWidths(new float[] { 1.0f, 1.0f, 1.0f, 1.2f });
			paymentTable.addCell(headerCell("Year"));
			paymentTable.addCell(headerCell("Inst. No."));
			paymentTable.addCell(headerCell("Paid On"));
			paymentTable.addCell(headerCell("Amount (Rs)"));

			BigDecimal totalAmount = BigDecimal.ZERO;
			for (FeeInstallmentPayment payment : groupPayments) {
				FeeInstallment installment = payment.getInstallment();
				paymentTable.addCell(valueCell(installment != null && installment.getStudyYear() != null
						? String.valueOf(installment.getStudyYear()) : "-", valueFont));
				paymentTable.addCell(valueCell(installment != null && installment.getInstallmentNo() != null
						? String.valueOf(installment.getInstallmentNo()) : "-", valueFont));
				paymentTable.addCell(valueCell(payment.getPaidOn() != null ? payment.getPaidOn().toString() : "-", valueFont));
				paymentTable.addCell(valueCell(safe(payment.getAmount()), valueFont));
				if (payment.getAmount() != null) {
					totalAmount = totalAmount.add(payment.getAmount());
				}
			}
			document.add(paymentTable);
			document.add(Chunk.NEWLINE);

			PdfPTable summaryTable = new PdfPTable(2);
			summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
			summaryTable.setWidthPercentage(45);
			summaryTable.setWidths(new float[] { 1.4f, 1.0f });
			summaryTable.addCell(labelCell("Total Amount (Rs)"));
			summaryTable.addCell(rightValueCell(safe(totalAmount), valueFont));
			document.add(summaryTable);
			document.add(Chunk.NEWLINE);

			FeeInstallmentPayment firstPayment = groupPayments.get(0);
			PdfPTable footerTable = new PdfPTable(1);
			footerTable.setWidthPercentage(100);

			PdfPCell footerCell = new PdfPCell();
			footerCell.setBorder(Rectangle.NO_BORDER);
			footerCell.addElement(new Paragraph(
					"Payment Mode: " + (firstPayment.getPaymentMode() != null ? firstPayment.getPaymentMode().getLabel() : "-"),
					valueFont));
			footerCell.addElement(new Paragraph(
					"Transaction ID: " + (firstPayment.getTxnRef() != null ? firstPayment.getTxnRef() : "-"),
					valueFont));
			footerCell.addElement(new Paragraph(
					"Received By: " + (firstPayment.getReceivedBy() != null ? firstPayment.getReceivedBy() : "-"),
					valueFont));

			footerTable.addCell(footerCell);
			document.add(footerTable);

		} catch (Exception e) {
			throw new RuntimeException("Error while building payment group invoice PDF", e);
		} finally {
			document.close();
		}

		return baos.toByteArray();
	}

	private byte[] buildSimplePaymentInvoicePdfBytes(Admission2 admission,
			FeeInstallmentPayment payment,
			String invoiceNumber) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 36, 36, 60, 36);

		try {
			PdfWriter.getInstance(document, baos);
			document.open();

			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
			Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
			Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

			Paragraph collegeName = new Paragraph("ABS EDUCATIONAL SOLUTION", titleFont);
			collegeName.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeName);

			Paragraph collegeLine2 = new Paragraph("Authorised Admission & Education Solution Centre", valueFont);
			collegeLine2.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeLine2);
			document.add(Chunk.NEWLINE);

			Paragraph receiptTitle = new Paragraph("PAYMENT RECEIPT", subTitleFont);
			receiptTitle.setAlignment(Element.ALIGN_CENTER);
			document.add(receiptTitle);
			document.add(Chunk.NEWLINE);

			addPaymentMetaTable(document, admission, invoiceNumber, payment.getPaidOn(), valueFont);
			addPaymentStudentTable(document, admission, valueFont);
			addSimplePaymentDetailsTable(document, payment.getAmount(), payment.getRemarks(), payment.getTxnRef(), valueFont);
			addSimplePaymentFooter(document, payment.getReceivedBy(), valueFont);
			document.close();
			return baos.toByteArray();
		} catch (DocumentException e) {
			throw new RuntimeException("Error building simplified payment invoice PDF", e);
		}
	}

	private byte[] buildSimplePaymentGroupInvoicePdfBytes(Admission2 admission,
			List<FeeInstallmentPayment> groupPayments,
			String invoiceNumber) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 36, 36, 60, 36);

		try {
			PdfWriter.getInstance(document, baos);
			document.open();

			Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
			Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
			Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

			Paragraph collegeName = new Paragraph("ABS EDUCATIONAL SOLUTION", titleFont);
			collegeName.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeName);

			Paragraph collegeLine2 = new Paragraph("Authorised Admission & Education Solution Centre", valueFont);
			collegeLine2.setAlignment(Element.ALIGN_CENTER);
			document.add(collegeLine2);
			document.add(Chunk.NEWLINE);

			Paragraph receiptTitle = new Paragraph("PAYMENT RECEIPT", subTitleFont);
			receiptTitle.setAlignment(Element.ALIGN_CENTER);
			document.add(receiptTitle);
			document.add(Chunk.NEWLINE);

			addPaymentMetaTable(document, admission, invoiceNumber, groupPayments.get(0).getPaidOn(), valueFont);
			addPaymentStudentTable(document, admission, valueFont);

			FeeInstallmentPayment firstPayment = groupPayments.get(0);
			BigDecimal totalAmount = groupPayments.stream()
					.map(FeeInstallmentPayment::getAmount)
					.filter(v -> v != null)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			addSimplePaymentDetailsTable(document, totalAmount, firstPayment.getRemarks(), firstPayment.getTxnRef(), valueFont);
			addSimplePaymentFooter(document, firstPayment.getReceivedBy(), valueFont);
			document.close();
			return baos.toByteArray();
		} catch (DocumentException e) {
			throw new RuntimeException("Error building simplified payment group invoice PDF", e);
		}
	}

	private void addPaymentMetaTable(Document document,
			Admission2 admission,
			String invoiceNumber,
			LocalDate receiptDate,
			Font valueFont)
			throws DocumentException {
		PdfPTable metaTable = new PdfPTable(2);
		metaTable.setWidthPercentage(100);
		metaTable.setWidths(new float[] { 1.2f, 1.8f });
		metaTable.addCell(labelCell("Receipt No."));
		metaTable.addCell(valueCell(invoiceNumber, valueFont));
		metaTable.addCell(labelCell("Receipt Date"));
		metaTable.addCell(valueCell(formatReceiptDate(receiptDate), valueFont));
		metaTable.addCell(labelCell("Admission ID"));
		metaTable.addCell(valueCell(admission.getAdmissionId() != null ? admission.getAdmissionId().toString() : "-", valueFont));
		document.add(metaTable);
		document.add(Chunk.NEWLINE);
	}

	private String formatReceiptDate(LocalDate receiptDate) {
		LocalDate effectiveDate = receiptDate != null ? receiptDate : LocalDate.now();
		return effectiveDate.format(DateTimeFormatter.ISO_DATE);
	}

	private void addPaymentStudentTable(Document document, Admission2 admission, Font valueFont) throws DocumentException {
		PdfPTable studentTable = new PdfPTable(2);
		studentTable.setWidthPercentage(100);
		studentTable.setWidths(new float[] { 1.2f, 1.8f });
		studentTable.addCell(labelCell("Student Name"));
		studentTable.addCell(valueCell(
				admission.getStudent() != null ? admission.getStudent().getFullName() : "-", valueFont));
		studentTable.addCell(labelCell("ABS ID"));
		studentTable.addCell(valueCell(
				admission.getStudent() != null && admission.getStudent().getAbsId() != null
						? admission.getStudent().getAbsId()
						: "-",
				valueFont));
		studentTable.addCell(labelCell("Course"));
		studentTable.addCell(valueCell(
				admission.getCourse() != null ? admission.getCourse().getName() : "-", valueFont));
		document.add(studentTable);
		document.add(Chunk.NEWLINE);
	}

	private void addSimplePaymentDetailsTable(Document document,
			BigDecimal amount,
			String remarks,
			String txnRef,
			Font valueFont) throws DocumentException {
		PdfPTable detailsTable = new PdfPTable(3);
		detailsTable.setWidthPercentage(100);
		detailsTable.setWidths(new float[] { 1.1f, 2.1f, 1.5f });
		detailsTable.addCell(headerCell("Amount (Rs)"));
		detailsTable.addCell(headerCell("Remark"));
		detailsTable.addCell(headerCell("Transaction ID"));
		detailsTable.addCell(valueCell(safe(amount), valueFont));
		detailsTable.addCell(valueCell(remarks, valueFont));
		detailsTable.addCell(valueCell(txnRef, valueFont));
		document.add(detailsTable);
		document.add(Chunk.NEWLINE);
	}

	private void addSimplePaymentFooter(Document document,
			String receivedBy,
			Font valueFont) throws DocumentException {
		PdfPTable footerTable = new PdfPTable(1);
		footerTable.setWidthPercentage(100);

		PdfPCell footerCell = new PdfPCell();
		footerCell.setBorder(Rectangle.NO_BORDER);
		footerCell.setHorizontalAlignment(Element.ALIGN_LEFT);
		footerCell.addElement(new Paragraph(
				"Received By: " + (StringUtils.hasText(receivedBy) ? receivedBy : "-"),
				valueFont));
		footerTable.addCell(footerCell);

		document.add(footerTable);
		document.add(Chunk.NEWLINE);
	}

	public static boolean isPaymentGroupInvoiceNumber(String invoiceNumber) {
		return invoiceNumber != null && invoiceNumber.startsWith(PAYMENT_GROUP_INVOICE_PREFIX);
	}

	public String detectPaymentInvoiceFormat(FeeInvoice invoice) {
		if (invoice == null) {
			return null;
		}
		if (StringUtils.hasText(invoice.getInvoiceFormat())) {
			return invoice.getInvoiceFormat();
		}
		try {
			byte[] pdfBytes = loadInvoiceBytes(invoice);
			if (pdfBytes == null || pdfBytes.length == 0) {
				return INVOICE_FORMAT_PAYMENT_V1_DETAILED;
			}
			PdfReader reader = new PdfReader(pdfBytes);
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			StringBuilder text = new StringBuilder();
			int pages = reader.getNumberOfPages();
			for (int i = 1; i <= pages; i++) {
				text.append(extractor.getTextFromPage(i)).append('\n');
			}
			reader.close();
			String normalized = text.toString();
			boolean hasLegacyDetails = normalized.contains("Installment No.") || normalized.contains("Payment Mode:");
			boolean hasSimplifiedDetails = normalized.contains("Remark") || normalized.contains("Transaction ID");
			return !hasLegacyDetails && hasSimplifiedDetails
					? INVOICE_FORMAT_PAYMENT_V2_SIMPLIFIED
					: INVOICE_FORMAT_PAYMENT_V1_DETAILED;
		} catch (Exception ex) {
			log.warn("Failed to inspect invoice {} for payment format", invoice.getInvoiceNumber(), ex);
			return INVOICE_FORMAT_PAYMENT_V1_DETAILED;
		}
	}

	private byte[] loadInvoiceBytes(FeeInvoice invoice) throws IOException {
		String filePath = invoice.getFilePath();
		if (!StringUtils.hasText(filePath)) {
			return null;
		}
		String r2Key = r2InvoiceStorageService.extractKey(filePath);
		if (!StringUtils.hasText(r2Key)) {
			r2Key = r2InvoiceStorageService.extractKey(invoice.getDownloadUrl());
		}
		if (StringUtils.hasText(r2Key)) {
			return r2InvoiceStorageService.download(r2Key);
		}
		Path path = Paths.get(filePath);
		if (!Files.exists(path)) {
			return null;
		}
		return Files.readAllBytes(path);
	}

	private FeeInvoice saveInvoiceByNumber(FeeInvoice invoice, String invoiceNumber) {
		try {
			return invoiceRepo.save(invoice);
		} catch (DataIntegrityViolationException ex) {
			FeeInvoice existing = invoiceRepo.findByInvoiceNumber(invoiceNumber)
					.orElseThrow(() -> ex);
			existing.setInstallment(invoice.getInstallment());
			existing.setPayment(invoice.getPayment());
			existing.setInvoiceNumber(invoice.getInvoiceNumber());
			existing.setFilePath(invoice.getFilePath());
			existing.setDownloadUrl(invoice.getDownloadUrl());
			existing.setAmount(invoice.getAmount());
			existing.setInvoiceFormat(invoice.getInvoiceFormat());
			existing.setReceiptDateSynced(invoice.getReceiptDateSynced());
			return invoiceRepo.save(existing);
		}
	}

	/** Small helper methods for nice-looking cells */
	private PdfPCell labelCell(String text) {
		Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
		PdfPCell cell = new PdfPCell(new Phrase(text, labelFont));
		cell.setPadding(4f);
		cell.setBorder(Rectangle.BOX);
		return cell;
	}

	private PdfPCell headerCell(String text) {
		Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
		PdfPCell cell = new PdfPCell(new Phrase(text, headerFont));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setPadding(5f);
		cell.setBackgroundColor(new Color(230, 230, 230)); // light grey (OpenPDF `java.awt.Color`)
		return cell;
	}

	private PdfPCell valueCell(String text, Font font) {
		PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
		cell.setPadding(4f);
		cell.setBorder(Rectangle.BOX);
		return cell;
	}

	private PdfPCell rightValueCell(String text, Font font) {
		PdfPCell cell = valueCell(text, font);
		cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		return cell;
	}

	private String safe(BigDecimal v) {
		return v != null ? v.toPlainString() : "0.00";
	}
}
