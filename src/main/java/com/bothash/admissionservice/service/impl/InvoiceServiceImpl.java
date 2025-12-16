package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl {

	private final FeeInvoiceRepository invoiceRepo;
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

			Path dir = Paths.get(invoiceBasePath, String.valueOf(admission.getAdmissionId()));
			Files.createDirectories(dir);

			String fileName = invoiceNumber + ".pdf";
			Path filePath = dir.resolve(fileName);
			Files.write(filePath, pdfBytes);

			String downloadUrl = appBaseUrl + "/api/invoices/download/" + admission.getAdmissionId() + "/" + fileName;

			FeeInvoice inv = new FeeInvoice();
			if(invs!=null && !invs.isEmpty()) {
				inv = invs.get(0);
			}
			inv.setInstallment(inst);
			inv.setInvoiceNumber(invoiceNumber);
			inv.setFilePath(filePath.toString());
			inv.setDownloadUrl(downloadUrl);
			inv.setAmount(inst.getAmountPaid() != null ? inst.getAmountPaid() : inst.getAmountDue());

			FeeInvoice saved = invoiceRepo.save(inv);

			// (Optional) send email with pdfBytes...

			return saved;
		} catch (Exception ex) {
			log.error("Error generating invoice for installment {}: {}", inst.getInstallmentId(), ex.getMessage(), ex);
			throw new RuntimeException("Failed to generate invoice", ex);
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
			PdfPTable feeTable = new PdfPTable(6);
			feeTable.setWidthPercentage(100);
			feeTable.setWidths(new float[] { 2.5f, 1.0f, 1.0f, 1.2f, 1.2f, 1.1f });

// Header row
			feeTable.addCell(headerCell("Description"));
			feeTable.addCell(headerCell("Year"));
			feeTable.addCell(headerCell("Inst. No."));
			feeTable.addCell(headerCell("Due Date"));
			feeTable.addCell(headerCell("Amount (₹)"));
			feeTable.addCell(headerCell("Status"));

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
			feeTable.addCell(valueCell(inst.getStatus() != null ? inst.getStatus() : "-", valueFont));

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
			PdfPTable footerTable = new PdfPTable(2);
			footerTable.setWidthPercentage(100);
			footerTable.setWidths(new float[] { 1.5f, 1.5f });

			PdfPCell leftFooter = new PdfPCell();
			leftFooter.setBorder(Rectangle.NO_BORDER);
			leftFooter.addElement(new Paragraph(
					"Payment Mode: " + (inst.getPaymentMode() != null ? inst.getPaymentMode().getLabel() : "-"),
					valueFont));
			leftFooter.addElement(new Paragraph(
					"Transaction Ref: " + (inst.getTxnRef() != null ? inst.getTxnRef() : "-"), valueFont));
			leftFooter.addElement(new Paragraph(
					"Received By: " + (inst.getReceivedBy() != null ? inst.getReceivedBy() : "-"), valueFont));
			footerTable.addCell(leftFooter);

			PdfPCell rightFooter = new PdfPCell();
			rightFooter.setBorder(Rectangle.NO_BORDER);
			rightFooter.setHorizontalAlignment(Element.ALIGN_RIGHT);
			rightFooter.addElement(new Paragraph("For ABS EDUCATIONAL SOLUTION", valueFont));
			rightFooter.addElement(new Paragraph(" "));
			rightFooter.addElement(new Paragraph("Authorised Signatory", valueFont));
			footerTable.addCell(rightFooter);

			document.add(Chunk.NEWLINE);
			document.add(footerTable);

			document.add(Chunk.NEWLINE);
			Paragraph note = new Paragraph(
					"Note: This is a computer generated receipt and does not require a physical signature.",
					smallNoteFont);
			document.add(note);

		} catch (Exception e) {
			throw new RuntimeException("Error while building invoice PDF", e);
		} finally {
			document.close(); // flush into baos
		}

		return baos.toByteArray();
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
