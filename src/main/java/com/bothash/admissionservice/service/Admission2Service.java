package com.bothash.admissionservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.bothash.admissionservice.dto.CreateAdmissionRequest;
import com.bothash.admissionservice.dto.InstallmentUpsertRequest;
import com.bothash.admissionservice.dto.MultipleUploadRequest;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.AdmissionDocument;
import com.bothash.admissionservice.entity.AdmissionSignoff;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FileUpload;
import com.bothash.admissionservice.entity.PaymentModeMaster;
import com.bothash.admissionservice.enumpackage.AdmissionStatus;
import com.bothash.admissionservice.enumpackage.PaymentMode;

public interface Admission2Service {
//	Admission2 createAdmission(Long studentId, String academicYearLabel, String courseCode, String formNo,
//			LocalDate formDate);

	Admission2 updateOfficeDetails(Long admissionId, String lastCollege, String collegeAttended, String collegeLocation,
			String remarks, LocalDate examDueDate, LocalDate dateOfAdmission);

// Documents checklist
	AdmissionDocument setDocumentReceived(Long admissionId, String docTypeCode, boolean received);

// File uploads metadata (store files in object storage, save metadata here)
	List<FileUpload> addUpload(Long admissionId, MultipleUploadRequest req);

// Fees
//	FeeInstallment upsertInstallment(Long admissionId, int studyYear, int installmentNo, BigDecimal amountDue,
//			LocalDate dueDate, String mode);

//	FeeInstallment recordPayment(Long installmentId, BigDecimal amountPaid, LocalDate paidOn, PaymentMode mode,
//			String txnRef);

// Sign-offs
	AdmissionSignoff signByHead(Long admissionId);

	AdmissionSignoff signByClerk(Long admissionId);

	AdmissionSignoff signByCounsellor(Long admissionId);

	Optional<Admission2> getById(Long id);

	List<Admission2> listByCourseAndYear(String courseCode, String yearLabel);

	Admission2 createAdmission( CreateAdmissionRequest req);

	List<FeeInstallment> upsertInstallments(Long id, List<InstallmentUpsertRequest> items, String role);

	FeeInstallment upsertInstallment(Long admissionId, int studyYear, int installmentNo, BigDecimal amountDue,
			LocalDate dueDate, String mode, String receivedBy, String status);
	
	Admission2 acknowledgeAdmission(Long id);


	FeeInstallment recordPayment(Long installmentId, BigDecimal amountPaid, LocalDate paidOn, PaymentModeMaster mode,
			String txnRef);

	Admission2 getAdmission(Long admissionId);

	void updateStatus(Long admissionId, AdmissionStatus status);

	FeeInstallment upsertInstallment(Long admissionId, int studyYear, int installmentNo, BigDecimal amountDue,
			LocalDate dueDate, String mode, String receivedBy, String status, Double yearlyFeesAmount, String txnRef,
			String role);
}
