package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.entity.*;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.service.Admission2Service;
import com.bothash.admissionservice.dto.CreateAdmissionRequest;
import com.bothash.admissionservice.dto.InstallmentUpsertRequest;
import com.bothash.admissionservice.dto.MultipleUploadRequest;
import com.bothash.admissionservice.dto.PartialPaymentRequest;
import com.bothash.admissionservice.dto.UploadRequest;
import com.bothash.admissionservice.enumpackage.AdmissionStatus;
import com.bothash.admissionservice.enumpackage.CollegeVerificationStatus;
import com.bothash.admissionservice.repository.*;

@Service
@RequiredArgsConstructor
@Transactional
public class Admission2ServiceImpl implements Admission2Service {
	private final Admission2Repository admissionRepo;
	private final StudentRepository studentRepo;
	private final CourseRepository courseRepo;
	private final CollegeRepository collegeRepo;
	private final AcademicYearRepository yearRepo;
	private final DocumentTypeRepository docTypeRepo;
private final AdmissionDocumentRepository admDocRepo;
private final FileUploadRepository uploadRepo;
	private final FeeInstallmentRepository feeRepo;
	private final FeeInstallmentPaymentRepository paymentRepo;
	private final AdmissionSignoffRepository signoffRepo;
	private final PaymentModeService service;
	private final BranchRepository branchRepository;
	private final CollegeCourseRepository collegeCourseRepository;
	private final InvoiceServiceImpl invoiceService;
	private final FeeInvoiceRepository invoiceRepo;
	
	@Autowired
	private YearlyFeesRepository yearlyFeesRepository;


	@Override
	public Admission2 createAdmission(CreateAdmissionRequest req) {
		Student student = studentRepo.findById(req.getStudentId())
				.orElseThrow(() -> new IllegalArgumentException("Student not found: " + req.getStudentId()));
		AcademicYear year = yearRepo.findByLabel(req.getAcademicYearLabel())
				.orElseThrow(() -> new IllegalArgumentException("Year not found: " + req.getAcademicYearLabel()));
		Course course = courseRepo.findById(req.getCourseCode())
				.orElseThrow(() -> new IllegalArgumentException("Course not found: " + req.getAcademicYearLabel()));

		College college = null;
		if (req.getCollegeId() != null) {
			college = collegeRepo.findById(req.getCollegeId())
					.orElseThrow(() -> new IllegalArgumentException("College not found: " + req.getCollegeId()));
		}
		BranchMaster admissionBranch =
				branchRepository.findById(req.getAdmissionBranchId())
						.orElseThrow(() -> new RuntimeException("Admission branch not found"));

		BranchMaster lectureBranch =
				branchRepository.findById(req.getLectureBranchId())
						.orElseThrow(() -> new RuntimeException("Lecture branch not found"));

		Admission2 a = this.admissionRepo.findByStudentStudentIdAndYearYearIdAndCourseCourseId(req.getStudentId(), year.getYearId(), course.getCourseId());
		boolean isNew = (a == null);
		Long previousCollegeId = null;
		if (isNew) {
			a = new Admission2();
			a.setStatus(AdmissionStatus.PENDING);
			a.setFormDate(req.getFormDate());
			if (!StringUtils.hasText(student.getAbsId())) {
				student.setAbsId(buildAbsId(course, admissionBranch, student.getStudentId()));
				studentRepo.save(student);
			}
		} else if (a.getCollege() != null) {
			previousCollegeId = a.getCollege().getCollegeId();
		}
		a.setStudent(student);
		a.setYear(year);
		a.setCourse(course);
		a.setCollege(college);
		if (college != null) {
			Long newCollegeId = college.getCollegeId();
			if (isNew || !Objects.equals(previousCollegeId, newCollegeId)) {
				a.setCollegeVerificationStatus(CollegeVerificationStatus.UNDER_VERIFICATION);
				a.setCollegeVerifiedBy(null);
				a.setCollegeVerifiedAt(null);
				a.setCollegeRejectedBy(null);
				a.setCollegeRejectedAt(null);
			}
		} else {
			a.setCollegeVerificationStatus(null);
			a.setCollegeVerifiedBy(null);
			a.setCollegeVerifiedAt(null);
			a.setCollegeRejectedBy(null);
			a.setCollegeRejectedAt(null);
		}
		a.setFormNo(req.getFormNo());
		a.setFormDate(req.getFormDate());
	
		a.setTotalFees(req.getTotalFees());
		a.setDiscount(req.getDiscount());
		a.setDiscountRemark(req.getDiscountRemark());
		a.setDiscountRemarkOther(req.getDiscountRemarkOther());
		a.setNoOfInstallments(req.getNoOfInstallments());
		a.setBatch(req.getOfficeUpdateRequest().getBatch());
		String registrationNumber = StringUtils.hasText(req.getOfficeUpdateRequest().getRegistrationNumber())
				? req.getOfficeUpdateRequest().getRegistrationNumber().trim()
				: null;
		a.setRegistrationNumber(registrationNumber);
		a.setReferenceName(req.getOfficeUpdateRequest().getReferenceName());
		a.setAdmissionBranch(admissionBranch);
		a.setLectureBranch(lectureBranch);
		a = admissionRepo.save(a);

		if (isNew && college != null) {
			incrementOnHoldSeats(college.getCollegeId(), course.getCourseId());
		}
		
		return this.updateOfficeDetails(a.getAdmissionId(), req.getOfficeUpdateRequest().getLastCollege(), req.getOfficeUpdateRequest().getCollegeAttended(), req.getOfficeUpdateRequest().getCollegeLocation(), 
				req.getOfficeUpdateRequest().getRemarks(), req.getOfficeUpdateRequest().getExamDueDate(), req.getOfficeUpdateRequest().getDateOfAdmission());
	}

	private String buildAbsId(Course course, BranchMaster admissionBranch, Long studentId) {
		int yearTwoDigits = LocalDate.now().getYear() % 100;
		String courseCode = course != null && StringUtils.hasText(course.getCode()) ? course.getCode().trim() : "";
		String branchCode = admissionBranch != null && StringUtils.hasText(admissionBranch.getCode())
				? admissionBranch.getCode().trim()
				: "";
		return String.format("%02d%s%s%04d", yearTwoDigits, courseCode, branchCode, studentId);
	}

	@Override
	public Admission2 updateOfficeDetails(Long admissionId, String lastCollege, String collegeAttended,
			String collegeLocation, String remarks, LocalDate examDueDate, LocalDate dateOfAdmission) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		a.setLastCollege(lastCollege);
		a.setCollegeAttended(collegeAttended);
		a.setCollegeLocation(collegeLocation);
		a.setRemarks(remarks);
		a.setExamDueDate(examDueDate);
		a.setDateOfAdm(dateOfAdmission);

		return admissionRepo.save(a);
	}

	@Override
	public AdmissionDocument setDocumentReceived(Long admissionId, String docTypeCode, boolean received) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		DocumentType dt = docTypeRepo.findByCode(docTypeCode)
				.orElseThrow(() -> new IllegalArgumentException("DocumentType not found: " + docTypeCode));

		AdmissionDocument doc = admDocRepo.findByAdmissionAdmissionIdAndDocTypeDocTypeId(admissionId, dt.getDocTypeId())
				.orElseGet(() -> {
					AdmissionDocument d = new AdmissionDocument();
					d.setAdmission(a);
					d.setDocType(dt);
					return d;
				});
		doc.setReceived(received);
		return admDocRepo.save(doc);
	}

	@Override
	public List<FileUpload> addUpload(Long admissionId, MultipleUploadRequest req) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		
		List<FileUpload> fileTosave = new ArrayList<>();
		for(UploadRequest uploadReq:req.getFiles()) {
			DocumentType dt = null;
			String docTypeCode = uploadReq.getDocTypeCode();
			if (StringUtils.hasText(docTypeCode)) {
				dt = docTypeRepo.findByCode(docTypeCode).orElse(null); // uploads may be misc (null)
			}

			FileUpload f = null;
			if (dt != null) {
				if(uploadReq.getInstallmentId() == null) {
					f = this.uploadRepo.findByAdmissionAdmissionIdAndDocTypeDocTypeId(admissionId, dt.getDocTypeId());
				}else {
					f = this.uploadRepo.findByAdmissionAdmissionIdAndDocTypeDocTypeIdAndInstallmentInstallmentId(admissionId, dt.getDocTypeId(),uploadReq.getInstallmentId());
				}
			}
				
			
			FeeInstallment feeInstallment = null;
			if (uploadReq.getInstallmentId() != null) {
				feeInstallment = this.feeRepo.findByInstallmentId(uploadReq.getInstallmentId()).orElse(null);
			}
			
			if(f == null) {
				f = new FileUpload();
			}
			
			f.setAdmission(a);
			f.setDocType(dt);
			f.setFilename(uploadReq.getFilename());
			f.setMimeType(uploadReq.getMimeType());
			f.setSizeBytes(uploadReq.getSizeBytes());
			f.setStorageUrl(uploadReq.getStorageUrl());
			f.setSha256(uploadReq.getSha256());
			f.setLabel(uploadReq.getLabel());
			f.setInstallment(feeInstallment);
			fileTosave.add(f);
		}
		if(!fileTosave.isEmpty())
			fileTosave = this.uploadRepo.saveAll(fileTosave);
		
		return fileTosave;
		
	}

	@Override
	public FeeInstallment upsertInstallment(Long admissionId, int studyYear, int installmentNo, BigDecimal amountDue,
			LocalDate dueDate, String mode, String receivedBy, String status) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		
	
		FeeInstallment fee = feeRepo
				.findByAdmissionAdmissionIdAndStudyYearAndInstallmentNo(admissionId, studyYear, installmentNo)
				.orElseGet(() -> {
					FeeInstallment f = new FeeInstallment();
					f.setAdmission(a);
					f.setStudyYear(studyYear);
					f.setInstallmentNo(installmentNo);
					if(mode!=null) {
						PaymentModeMaster paymentModeMaster = this.service.getByMode(mode);
						f.setPaymentMode(paymentModeMaster);
					}
						
					f.setStatus(status);
					f.setReceivedBy(receivedBy);
					
					return f;
				});
		fee.setAdmission(a);
		fee.setStudyYear(studyYear);
		fee.setInstallmentNo(installmentNo);
		if(mode!=null) {
			PaymentModeMaster paymentModeMaster = this.service.getByMode(mode);
			fee.setPaymentMode(paymentModeMaster);
		}
		fee.setStatus(status);
		fee.setReceivedBy(receivedBy);
		fee.setAmountDue(amountDue);
		fee.setDueDate(dueDate);
		return feeRepo.save(fee);
	}
	@Override
	public FeeInstallment upsertInstallment(Long admissionId, int studyYear, int installmentNo, BigDecimal amountDue,
			LocalDate dueDate, String mode, String receivedBy, String status, Double yearlyFeesAmount,String txnRef, String role) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		
		
		YearlyFees yearlyFees = this.yearlyFeesRepository.findByAdmissionAdmissionIdAndYear(admissionId,studyYear);
		
		if(yearlyFees==null) {
			yearlyFees = new YearlyFees();
			yearlyFees.setYear(studyYear);
			yearlyFees.setAdmission(a);
		}
		
		yearlyFees.setFees(yearlyFeesAmount);
		this.yearlyFeesRepository.save(yearlyFees);
		

		FeeInstallment fee = feeRepo
				.findByAdmissionAdmissionIdAndStudyYearAndInstallmentNo(admissionId, studyYear, installmentNo)
				.orElseGet(() -> {
					FeeInstallment f = new FeeInstallment();
					f.setAdmission(a);
					f.setStudyYear(studyYear);
					f.setInstallmentNo(installmentNo);
					if(mode!=null) {
						PaymentModeMaster paymentModeMaster = this.service.getByMode(mode);
						f.setPaymentMode(paymentModeMaster);
					}
					f.setTxnRef(txnRef);
					String resolvedStatus = resolveInstallmentStatus(f.getStatus(), status, role);
					f.setStatus(resolvedStatus);
					if (shouldAutoSetPaidAmount(role, status, amountDue)) {
						f.setAmountPaid(amountDue);
					}
					if (shouldMarkVerified(role, status)) {
						f.setIsVerified(true);
					}
						
					f.setReceivedBy(receivedBy);
					
					return f;
				});
		fee.setAdmission(a);
		fee.setStudyYear(studyYear);
		fee.setInstallmentNo(installmentNo);
		if(mode!=null) {
			PaymentModeMaster paymentModeMaster = this.service.getByMode(mode);
			fee.setPaymentMode(paymentModeMaster);
		}
		String resolvedStatus = resolveInstallmentStatus(fee.getStatus(), status, role);
		fee.setStatus(resolvedStatus);
		if (shouldAutoSetPaidAmount(role, status, amountDue)) {
			fee.setAmountPaid(amountDue);
		}
		if (shouldMarkVerified(role, status)) {
			fee.setIsVerified(true);
		}
		
		fee.setTxnRef(txnRef);
		fee.setReceivedBy(receivedBy);
		fee.setAmountDue(amountDue);
		fee.setDueDate(dueDate);
		return feeRepo.save(fee);
	}

	private String resolveInstallmentStatus(String currentStatus, String requestedStatus, String role) {
		if (requestedStatus == null) {
			return currentStatus;
		}
		if (role == null || !(role.equalsIgnoreCase("BRANCH_USER") || role.equalsIgnoreCase("ADMIN"))) {
			return requestedStatus;
		}
		if (!requestedStatus.equalsIgnoreCase("Paid")) {
			return requestedStatus;
		}
		if (currentStatus != null && currentStatus.equalsIgnoreCase("Paid")) {
			return currentStatus;
		}
		return "Under Verification";
	}

	private boolean shouldAutoSetPaidAmount(String role, String requestedStatus, BigDecimal amountDue) {
		return amountDue != null
				&& isRoleOneOf(role, "BRANCH_USER", "HO", "ADMIN")
				&& requestedStatus != null
				&& requestedStatus.equalsIgnoreCase("Paid");
	}

	private boolean shouldMarkVerified(String role, String requestedStatus) {
		return isRoleOneOf(role, "HO")
				&& requestedStatus != null
				&& requestedStatus.equalsIgnoreCase("Paid");
	}

	private boolean isRoleOneOf(String role, String... roles) {
		if (role == null) {
			return false;
		}
		for (String candidate : roles) {
			if (role.equalsIgnoreCase(candidate)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public List<FeeInstallmentPayment> applyPartialPayment(Long admissionId, PartialPaymentRequest request, String role) {
		if (request == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Payment amount must be greater than zero.");
		}
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));

		List<FeeInstallment> installments = feeRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admissionId);
		if (installments.isEmpty()) {
			throw new IllegalArgumentException("No installments found for admission: " + admissionId);
		}

		PaymentModeMaster paymentMode = null;
		if (StringUtils.hasText(request.getMode())) {
			paymentMode = service.getByMode(request.getMode());
		}

		BigDecimal remaining = request.getAmount();
		List<FeeInstallmentPayment> payments = new ArrayList<>();
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
			BigDecimal newPaid = amountPaid.add(applied);
			installment.setAmountPaid(newPaid);

			boolean fullyPaid = newPaid.compareTo(amountDue) >= 0;
			String status = resolvePartialPaymentStatus(role, fullyPaid);
			installment.setStatus(status);
			boolean verified = isRoleOneOf(role, "HO");
			installment.setIsVerified(verified);
			if (fullyPaid && installment.getPaidOn() == null) {
				installment.setPaidOn(LocalDate.now());
			}
			feeRepo.save(installment);

			String paymentStatus = verified ? "Paid" : "Under Verification";
			FeeInstallmentPayment payment = FeeInstallmentPayment.builder()
					.installment(installment)
					.amount(applied)
					.paymentMode(paymentMode)
					.txnRef(request.getTxnRef())
					.receivedBy(request.getReceivedBy())
					.status(paymentStatus)
					.isVerified(verified)
					.verifiedBy(verified ? request.getReceivedBy() : null)
					.verifiedAt(verified ? LocalDateTime.now() : null)
					.paidOn(LocalDate.now())
					.build();
			payment = paymentRepo.save(payment);
			payments.add(payment);

			if (verified && !invoiceRepo.existsByPayment_PaymentId(payment.getPaymentId())) {
				invoiceService.generateInvoiceForPayment(admission, installment, payment);
			}

			UploadRequest receipt = request.getReceipt();
			if (receipt != null && StringUtils.hasText(receipt.getStorageUrl())) {
				FileUpload upload = buildPaymentReceiptUpload(admission, installment, payment, receipt);
				uploadRepo.save(upload);
			}

			remaining = remaining.subtract(applied);
		}

		if (remaining.compareTo(BigDecimal.ZERO) > 0) {
			throw new IllegalArgumentException("Payment exceeds pending installment totals.");
		}
		return payments;
	}

	private String resolvePartialPaymentStatus(String role, boolean fullyPaid) {
		if (isRoleOneOf(role, "HO")) {
			return fullyPaid ? "Paid" : "Partial Received";
		}
		return "Under Verification";
	}

	private FileUpload buildPaymentReceiptUpload(Admission2 admission, FeeInstallment installment,
			FeeInstallmentPayment payment, UploadRequest receipt) {
		DocumentType dt = null;
		if (StringUtils.hasText(receipt.getDocTypeCode())) {
			dt = docTypeRepo.findByCode(receipt.getDocTypeCode()).orElse(null);
		}

		FileUpload upload = new FileUpload();
		upload.setAdmission(admission);
		upload.setDocType(dt);
		upload.setFilename(receipt.getFilename());
		upload.setMimeType(receipt.getMimeType());
		upload.setSizeBytes(receipt.getSizeBytes());
		upload.setStorageUrl(receipt.getStorageUrl());
		upload.setSha256(receipt.getSha256());
		upload.setLabel(receipt.getLabel());
		upload.setInstallment(installment);
		upload.setInstallmentPayment(payment);
		return upload;
	}


	@Override
	  @Transactional
	  public List<FeeInstallment> upsertInstallments(Long admissionId, List<InstallmentUpsertRequest> items,String role) {
	    List<FeeInstallment> out = new ArrayList<>(items.size());
	    for (var it : items) {
	      out.add(upsertInstallment(admissionId, it.getStudyYear(), it.getInstallmentNo(), it.getAmountDue(), it.getDueDate(),it.getMode(),
	    		  it.getReceivedBy(),it.getStatus(),it.getYearlyFees(),it.getTxnRef(),role));
	    }
	    return out;
	  }

	@Override
	public FeeInstallment recordPayment(Long installmentId, BigDecimal amountPaid, LocalDate paidOn, PaymentModeMaster mode,
			String txnRef) {
		FeeInstallment fee = feeRepo.findById(installmentId)
				.orElseThrow(() -> new IllegalArgumentException("Installment not found: " + installmentId));
		fee.setAmountPaid(amountPaid);
		fee.setPaidOn(paidOn);
		fee.setPaymentMode(mode);
		fee.setTxnRef(txnRef);
		return feeRepo.save(fee);
	}

	@Override
	public AdmissionSignoff signByHead(Long admissionId) {
		return sign(admissionId, "head");
	}

	@Override
	public AdmissionSignoff signByClerk(Long admissionId) {
		return sign(admissionId, "clerk");
	}

	@Override
	public AdmissionSignoff signByCounsellor(Long admissionId) {
		return sign(admissionId, "counsellor");
	}

	private AdmissionSignoff sign(Long admissionId, String role) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		AdmissionSignoff s = signoffRepo.findByAdmissionAdmissionId(admissionId).orElseGet(() -> {
			AdmissionSignoff x = new AdmissionSignoff();
			x.setAdmission(a);
			return x;
		});
		OffsetDateTime now = OffsetDateTime.now();
		switch (role) {
		case "head" -> s.setHeadSignAt(now);
		case "clerk" -> s.setClerkSignAt(now);
		case "counsellor" -> s.setCounsellorSignAt(now);
		default -> throw new IllegalArgumentException("Unknown role: " + role);
		}
		return signoffRepo.save(s);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Admission2> getById(Long id) {
		return admissionRepo.findByAdmissionId(id);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Admission2> listByCourseAndYear(String courseCode, String yearLabel) {
		Long cid = courseRepo.findByCode(courseCode).map(Course::getCourseId)
				.orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseCode));
		Long yid = yearRepo.findByLabel(yearLabel).map(AcademicYear::getYearId)
				.orElseThrow(() -> new IllegalArgumentException("Year not found: " + yearLabel));
		return admissionRepo.findByCourseCourseIdAndYearYearId(cid, yid);
	}

	@Override
	public Admission2 acknowledgeAdmission(Long id) {
		Optional<Admission2> admissionOpt = this.admissionRepo.findByAdmissionId(id);
		
		if(admissionOpt.isPresent()) {
			Admission2 admission = admissionOpt.get();
			if (admission.getStatus() != AdmissionStatus.ADMITTED) {
				admission.setStatus(AdmissionStatus.ADMITTED);
				this.admissionRepo.save(admission);
				allocateSeatIfPossible(admission);
			}
			return admission;
		}
		return null;
	}

	@Override
	public Admission2 updateCollegeVerification(Long admissionId, String status, String actor) {
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));

		CollegeVerificationStatus newStatus;
		try {
			newStatus = CollegeVerificationStatus.valueOf(status);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid college verification status: " + status);
		}

		admission.setCollegeVerificationStatus(newStatus);
		if (newStatus == CollegeVerificationStatus.VERIFIED) {
			admission.setCollegeVerifiedBy(actor);
			admission.setCollegeVerifiedAt(LocalDateTime.now());
			admission.setCollegeRejectedBy(null);
			admission.setCollegeRejectedAt(null);
		} else if (newStatus == CollegeVerificationStatus.REJECTED) {
			admission.setCollegeRejectedBy(actor);
			admission.setCollegeRejectedAt(LocalDateTime.now());
			admission.setCollegeVerifiedBy(null);
			admission.setCollegeVerifiedAt(null);
		}

		return admissionRepo.save(admission);
	}


	@Transactional
	public Admission2 getAdmission(Long admissionId) {
		return admissionRepo.findById(admissionId)
				.orElseThrow(() -> new RuntimeException("Admission not found"));
	}

	@Transactional
	public void updateStatus(Long admissionId, AdmissionStatus status) {
		Admission2 admission = getAdmission(admissionId);
		AdmissionStatus previous = admission.getStatus();
		admission.setStatus(status);
		admissionRepo.save(admission);
		if (previous != AdmissionStatus.ADMITTED && status == AdmissionStatus.ADMITTED) {
			allocateSeatIfPossible(admission);
		}
	}

	private void incrementOnHoldSeats(Long collegeId, Long courseId) {
		CollegeCourse cc = collegeCourseRepository
				.findByCollegeCollegeIdAndCourseCourseId(collegeId, courseId)
				.orElseThrow(() -> new IllegalArgumentException("College course mapping not found."));
		int onHold = cc.getOnHoldSeats() == null ? 0 : cc.getOnHoldSeats();
		cc.setOnHoldSeats(onHold + 1);
		collegeCourseRepository.save(cc);
	}

	private void allocateSeatIfPossible(Admission2 admission) {
		if (admission.getCollege() == null || admission.getCourse() == null) {
			return;
		}
		Long collegeId = admission.getCollege().getCollegeId();
		Long courseId = admission.getCourse().getCourseId();
		CollegeCourse cc = collegeCourseRepository
				.findByCollegeCollegeIdAndCourseCourseId(collegeId, courseId)
				.orElse(null);
		if (cc == null) {
			return;
		}
		int onHold = cc.getOnHoldSeats() == null ? 0 : cc.getOnHoldSeats();
		int allocated = cc.getAllocatedSeats() == null ? 0 : cc.getAllocatedSeats();
		if (onHold > 0) {
			cc.setOnHoldSeats(onHold - 1);
		}
		cc.setAllocatedSeats(allocated + 1);
		cc.setLastAllocatedAt(OffsetDateTime.now());
		collegeCourseRepository.save(cc);
	}
}
