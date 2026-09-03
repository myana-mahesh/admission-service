package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.entity.*;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.service.Admission2Service;
import com.bothash.admissionservice.service.AdmissionAuditService;
import com.bothash.admissionservice.service.LookupService;
import com.bothash.admissionservice.dto.CreateAdmissionRequest;
import com.bothash.admissionservice.dto.InstallmentUpsertRequest;
import com.bothash.admissionservice.dto.MultipleUploadRequest;
import com.bothash.admissionservice.dto.PartialPaymentRequest;
import com.bothash.admissionservice.dto.UploadRequest;
import com.bothash.admissionservice.dto.AdmissionOtherPaymentDto;
import com.bothash.admissionservice.dto.AdmissionOtherPaymentRequest;
import com.bothash.admissionservice.dto.AdmissionOtherPaymentReturnRequest;
import com.bothash.admissionservice.dto.AdmissionDocumentReturnRequest;
import com.bothash.admissionservice.dto.AdmissionDocumentResubmissionRequest;
import com.bothash.admissionservice.dto.StudentAdditionalQualificationDto;
import com.bothash.admissionservice.enumpackage.AdmissionStatus;
import com.bothash.admissionservice.enumpackage.CollegeVerificationStatus;
import com.bothash.admissionservice.repository.*;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
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
	private final AdmissionDocumentReturnRepository admissionDocumentReturnRepository;
	private final StudentAdditionalQualificationRepository studentAdditionalQualificationRepository;
	private final AdmissionOtherPaymentRepository admissionOtherPaymentRepository;
	private final FeeInstallmentRepository feeRepo;
	private final FeeInstallmentPaymentRepository paymentRepo;
	private final AdmissionSignoffRepository signoffRepo;
	private final PaymentModeService service;
	private final BranchRepository branchRepository;
	private final CollegeCourseRepository collegeCourseRepository;
	private final InvoiceServiceImpl invoiceService;
	private final FeeInvoiceRepository invoiceRepo;
	private final AdmissionAuditService admissionAuditService;
	private final R2InvoiceStorageService r2InvoiceStorageService;
	private final LookupService lookupService;
	
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

		// Admission2 a = this.admissionRepo.findByStudentStudentIdAndYearYearIdAndCourseCourseId(req.getStudentId(), year.getYearId(), course.getCourseId());
		Optional<Admission2> optAdmission = this.admissionRepo.findByStudentStudentIdAndYearYearId(req.getStudentId(), year.getYearId());
		Admission2 a = null;
		if(optAdmission !=null && optAdmission.isPresent()){
			a = optAdmission.get();
		}
		boolean isNew = (a == null);
		Long previousCollegeId = null;
		Long previousCourseId = null;
		AdmissionStatus previousStatus = null;
		String prevFormNo = null;
		LocalDate prevFormDate = null;
		Double prevTotalFees = null;
		Double prevDiscount = null;
		String prevDiscountRemark = null;
		String prevDiscountRemarkOther = null;
		Integer prevInstallments = null;
		String prevBatch = null;
		String prevRegistrationNumber = null;
		String prevReferenceName = null;
		String prevAdmissionBranchName = null;
		String prevLectureBranchName = null;
		String prevAbsId = null;
		CollegeVerificationStatus prevCollegeVerificationStatus = null;
		if (isNew) {
			a = new Admission2();
			a.setStatus(AdmissionStatus.PENDING);
			a.setFormDate(req.getFormDate());
			if (!StringUtils.hasText(student.getAbsId())) {
				student.setAbsId(buildAbsId(course, admissionBranch, student.getStudentId()));
				studentRepo.save(student);
			}
		} else {
			previousCollegeId = a.getCollege() != null ? a.getCollege().getCollegeId() : null;
			previousCourseId = a.getCourse() != null ? a.getCourse().getCourseId() : null;
			previousStatus = a.getStatus();
			prevFormNo = a.getFormNo();
			prevFormDate = a.getFormDate();
			prevTotalFees = a.getTotalFees();
			prevDiscount = a.getDiscount();
			prevDiscountRemark = a.getDiscountRemark();
			prevDiscountRemarkOther = a.getDiscountRemarkOther();
			prevInstallments = a.getNoOfInstallments();
			prevBatch = a.getBatch();
			prevRegistrationNumber = a.getRegistrationNumber();
			prevReferenceName = a.getReferenceName();
			prevAdmissionBranchName = branchName(a.getAdmissionBranch());
			prevLectureBranchName = branchName(a.getLectureBranch());
			prevAbsId = student.getAbsId();
			prevCollegeVerificationStatus = a.getCollegeVerificationStatus();
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
		// Temporary-admission flag: honour the request only on create. Once the
		// record exists, the flag can only travel from true→false (via the
		// dedicated clear endpoint or the ADMITTED promotion). Ignoring the
		// request on update prevents the form from re-enabling it after the
		// user or the system have already confirmed the admission.
		if (isNew) {
			a.setTemporaryAdmission(Boolean.TRUE.equals(req.getTemporaryAdmission()));
		}
		a = admissionRepo.save(a);

		if (!isNew) {
			Long newCollegeId = college != null ? college.getCollegeId() : null;
			Long newCourseId = course != null ? course.getCourseId() : null;
			if (!Objects.equals(previousCourseId, newCourseId)) {
				student.setAbsId(buildAbsId(course, admissionBranch, student.getStudentId()));
				studentRepo.save(student);
			}
			adjustSeatCountsForCourseChange(previousStatus, previousCollegeId, previousCourseId, newCollegeId, newCourseId);

			Map<String, Object> changes = new LinkedHashMap<>();
			addChange(changes, "courseId", previousCourseId, newCourseId);
			addChange(changes, "collegeId", previousCollegeId, newCollegeId);
			addChange(changes, "totalFees", prevTotalFees, a.getTotalFees());
			addChange(changes, "discount", prevDiscount, a.getDiscount());
			addChange(changes, "discountRemark", prevDiscountRemark, a.getDiscountRemark());
			addChange(changes, "discountRemarkOther", prevDiscountRemarkOther, a.getDiscountRemarkOther());
			addChange(changes, "noOfInstallments", prevInstallments, a.getNoOfInstallments());
			addChange(changes, "batch", prevBatch, a.getBatch());
			addChange(changes, "registrationNumber", prevRegistrationNumber, a.getRegistrationNumber());
			addChange(changes, "referenceName", prevReferenceName, a.getReferenceName());
			addChange(changes, "admissionBranch", prevAdmissionBranchName, branchName(a.getAdmissionBranch()));
			addChange(changes, "lectureBranch", prevLectureBranchName, branchName(a.getLectureBranch()));
			addChange(changes, "absId", prevAbsId, student.getAbsId());
			addChange(changes, "collegeVerificationStatus", prevCollegeVerificationStatus, a.getCollegeVerificationStatus());
			Map<String, Object> details = Map.of(
					"admissionId", a.getAdmissionId(),
					"studentId", student.getStudentId()
			);
			audit(a, "ADMISSION_UPDATED", null, details, changes);
		}

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
		String prefix = (courseCode + branchCode).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
		String idPart = String.valueOf(studentId != null ? studentId : 0L);
		if (idPart.length() < 4) {
			idPart = String.format("%04d", studentId);
		}
		String suffix = String.format("%02d", yearTwoDigits) + idPart;
		int maxLen = 32;
		if (suffix.length() >= maxLen) {
			return suffix.substring(suffix.length() - maxLen);
		}
		int prefixLen = maxLen - suffix.length();
		String trimmedPrefix = prefix.length() > prefixLen ? prefix.substring(0, prefixLen) : prefix;
		return trimmedPrefix + suffix;
	}

	@Override
	public Admission2 updateOfficeDetails(Long admissionId, String lastCollege, String collegeAttended,
			String collegeLocation, String remarks, LocalDate examDueDate, LocalDate dateOfAdmission) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		String prevLastCollege = a.getLastCollege();
		String prevCollegeAttended = a.getCollegeAttended();
		String prevCollegeLocation = a.getCollegeLocation();
		String prevRemarks = a.getRemarks();
		LocalDate prevExamDueDate = a.getExamDueDate();
		LocalDate prevDateOfAdm = a.getDateOfAdm();
		a.setLastCollege(lastCollege);
		a.setCollegeAttended(collegeAttended);
		a.setCollegeLocation(collegeLocation);
		a.setRemarks(remarks);
		a.setExamDueDate(examDueDate);
		a.setDateOfAdm(dateOfAdmission);
		a = admissionRepo.save(a);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "lastCollege", prevLastCollege, a.getLastCollege());
		addChange(changes, "collegeAttended", prevCollegeAttended, a.getCollegeAttended());
		addChange(changes, "collegeLocation", prevCollegeLocation, a.getCollegeLocation());
		addChange(changes, "remarks", prevRemarks, a.getRemarks());
		addChange(changes, "examDueDate", prevExamDueDate, a.getExamDueDate());
		addChange(changes, "dateOfAdmission", prevDateOfAdm, a.getDateOfAdm());
		audit(a, "OFFICE_DETAILS_UPDATED", null, Map.of("admissionId", admissionId), changes);
		return a;
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
		Boolean prevReceived = doc.isReceived();
		doc.setReceived(received);
		doc = admDocRepo.save(doc);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "documents." + dt.getCode() + ".received", prevReceived, doc.isReceived());
		Map<String, Object> details = Map.of(
				"docTypeCode", dt.getCode(),
				"docTypeId", dt.getDocTypeId()
		);
		audit(a, "DOCUMENT_CHECKLIST_UPDATED", null, details, changes);
		return doc;
	}

	@Override
	public List<AdmissionDocumentReturn> listDocumentReturns(Long admissionId) {
		return admissionDocumentReturnRepository.findByAdmissionAdmissionIdOrderByReturnedOnDescReturnIdDesc(admissionId);
	}

	@Override
	public AdmissionDocumentReturn addDocumentReturn(Long admissionId, AdmissionDocumentReturnRequest request) {
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		if (request == null || !StringUtils.hasText(request.getDocTypeCode())) {
			throw new IllegalArgumentException("Document type is required.");
		}
		DocumentType docType = docTypeRepo.findByCode(request.getDocTypeCode().trim())
				.orElseThrow(() -> new IllegalArgumentException("DocumentType not found: " + request.getDocTypeCode()));

		AdmissionDocumentReturn entry = AdmissionDocumentReturn.builder()
				.admission(admission)
				.docType(docType)
				.returnedOn(request.getReturnedOn() != null ? request.getReturnedOn() : LocalDate.now())
				.reason(StringUtils.hasText(request.getReason()) ? request.getReason().trim() : null)
				.returnedBy(StringUtils.hasText(request.getReturnedBy()) ? request.getReturnedBy().trim() : null)
				.documentHandler(StringUtils.hasText(request.getDocumentHandler()) ? request.getDocumentHandler().trim() : null)
				.actionType(resolveReturnAction(request.getActionType()))
				.build();
		entry = admissionDocumentReturnRepository.save(entry);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "documentReturn.docTypeCode", null, docType.getCode());
		addChange(changes, "documentReturn.returnedOn", null, entry.getReturnedOn());
		addChange(changes, "documentReturn.reason", null, entry.getReason());
		addChange(changes, "documentReturn.returnedBy", null, entry.getReturnedBy());
		addChange(changes, "documentReturn.documentHandler", null, entry.getDocumentHandler());
		addChange(changes, "documentReturn.actionType", null, entry.getActionType());
		Map<String, Object> details = Map.of(
				"returnId", entry.getReturnId(),
				"docTypeCode", docType.getCode()
		);
		audit(admission, "DOCUMENT_RETURNED", entry.getReturnedBy(), details, changes);
		return entry;
	}

	@Override
	public AdmissionDocumentReturn updateDocumentResubmission(Long returnId, AdmissionDocumentResubmissionRequest request) {
		AdmissionDocumentReturn entry = admissionDocumentReturnRepository.findById(returnId)
				.orElseThrow(() -> new IllegalArgumentException("Document return not found: " + returnId));
		LocalDate prevResubmittedOn = entry.getResubmittedOn();
		String prevResubmittedTo = entry.getResubmittedTo();
		String prevResubmissionReason = entry.getResubmissionReason();
		String prevResubmittedBy = entry.getResubmittedBy();
		if (request == null) {
			throw new IllegalArgumentException("Resubmission request is required.");
		}
		entry.setResubmittedOn(LocalDate.now());
		String resubmittedTo = normalizeReceiveBackParty(request.getResubmittedTo());
		if (!StringUtils.hasText(resubmittedTo)) {
			resubmittedTo = resolveReturnTargetLabel(entry.getActionType());
		}
		entry.setResubmittedTo(resubmittedTo);
		entry.setResubmissionReason(StringUtils.hasText(request.getResubmissionReason())
				? request.getResubmissionReason().trim()
				: null);
		entry.setResubmittedBy(StringUtils.hasText(request.getResubmittedBy())
				? request.getResubmittedBy().trim()
				: null);
		entry = admissionDocumentReturnRepository.save(entry);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "documentReturn.resubmittedOn", prevResubmittedOn, entry.getResubmittedOn());
		addChange(changes, "documentReturn.resubmittedTo", prevResubmittedTo, entry.getResubmittedTo());
		addChange(changes, "documentReturn.resubmissionReason", prevResubmissionReason, entry.getResubmissionReason());
		addChange(changes, "documentReturn.resubmittedBy", prevResubmittedBy, entry.getResubmittedBy());
		Admission2 admission = entry.getAdmission();
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("returnId", entry.getReturnId());
		details.put("docTypeCode", entry.getDocType() != null ? entry.getDocType().getCode() : null);
		details.put("receivedBackFrom", entry.getResubmittedTo());
		audit(admission, "DOCUMENT_RECEIVED_BACK", entry.getResubmittedBy(), details, changes);
		return entry;
	}

	@Override
	public List<StudentAdditionalQualification> listAdditionalQualifications(Long admissionId) {
		return studentAdditionalQualificationRepository.findByAdmissionAdmissionIdOrderByQualificationIdAsc(admissionId);
	}

	@Override
	public List<StudentAdditionalQualification> replaceAdditionalQualifications(
			Long admissionId,
			List<StudentAdditionalQualificationDto> items
	) {
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		List<StudentAdditionalQualification> beforeEntries =
				studentAdditionalQualificationRepository.findByAdmissionAdmissionIdOrderByQualificationIdAsc(admissionId);
		List<QualificationSnapshot> beforeSnapshots = snapshotsFromEntities(beforeEntries);
		List<QualificationSnapshot> incomingSnapshots = snapshotsFromDtos(items);
		if (Objects.equals(beforeSnapshots, incomingSnapshots)) {
			return beforeEntries;
		}
		studentAdditionalQualificationRepository.deleteByAdmissionAdmissionId(admissionId);
		if (incomingSnapshots.isEmpty()) {
			Map<String, Object> changes = new LinkedHashMap<>();
			changes.put("additionalQualifications", Map.of(
					"label", "Additional Qualifications",
					"before", snapshotMaps(beforeSnapshots),
					"after", List.of()
			));
			audit(admission, "ADDITIONAL_QUALIFICATIONS_UPDATED", null, Map.of("admissionId", admissionId), changes);
			return List.of();
		}
		List<StudentAdditionalQualification> entries = new ArrayList<>();
		for (StudentAdditionalQualificationDto item : items) {
			if (item == null) {
				continue;
			}
			boolean hasData = StringUtils.hasText(item.getQualificationType())
					|| StringUtils.hasText(item.getCourseName())
					|| StringUtils.hasText(item.getCollegeName())
					|| item.getPercentage() != null;
			if (!hasData) {
				continue;
			}
			StudentAdditionalQualification entry = StudentAdditionalQualification.builder()
					.admission(admission)
					.qualificationType(StringUtils.hasText(item.getQualificationType())
							? item.getQualificationType().trim()
							: null)
					.courseName(StringUtils.hasText(item.getCourseName()) ? item.getCourseName().trim() : null)
					.collegeName(StringUtils.hasText(item.getCollegeName()) ? item.getCollegeName().trim() : null)
					.percentage(item.getPercentage())
					.build();
			entries.add(entry);
		}
		if (entries.isEmpty()) {
			Map<String, Object> changes = new LinkedHashMap<>();
			changes.put("additionalQualifications", Map.of(
					"label", "Additional Qualifications",
					"before", snapshotMaps(beforeSnapshots),
					"after", List.of()
			));
			audit(admission, "ADDITIONAL_QUALIFICATIONS_UPDATED", null, Map.of("admissionId", admissionId), changes);
			return List.of();
		}
		List<StudentAdditionalQualification> saved = studentAdditionalQualificationRepository.saveAll(entries);
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("additionalQualifications", Map.of(
				"label", "Additional Qualifications",
				"before", snapshotMaps(beforeSnapshots),
				"after", snapshotMaps(snapshotsFromEntities(saved))
		));
		audit(admission, "ADDITIONAL_QUALIFICATIONS_UPDATED", null, Map.of("admissionId", admissionId), changes);
		return saved;
	}

	private List<QualificationSnapshot> snapshotsFromEntities(List<StudentAdditionalQualification> entries) {
		if (entries == null) {
			return List.of();
		}
		List<QualificationSnapshot> snapshots = new ArrayList<>();
		for (StudentAdditionalQualification entry : entries) {
			if (entry == null) {
				continue;
			}
			snapshots.add(new QualificationSnapshot(
					clean(entry.getQualificationType()),
					clean(entry.getCourseName()),
					clean(entry.getCollegeName()),
					normalizePercentage(entry.getPercentage())
			));
		}
		snapshots.sort(qualificationComparator());
		return snapshots;
	}

	private List<QualificationSnapshot> snapshotsFromDtos(List<StudentAdditionalQualificationDto> items) {
		if (items == null) {
			return List.of();
		}
		List<QualificationSnapshot> snapshots = new ArrayList<>();
		for (StudentAdditionalQualificationDto item : items) {
			if (item == null) {
				continue;
			}
			boolean hasData = StringUtils.hasText(item.getQualificationType())
					|| StringUtils.hasText(item.getCourseName())
					|| StringUtils.hasText(item.getCollegeName())
					|| item.getPercentage() != null;
			if (!hasData) {
				continue;
			}
			snapshots.add(new QualificationSnapshot(
					clean(item.getQualificationType()),
					clean(item.getCourseName()),
					clean(item.getCollegeName()),
					normalizePercentage(item.getPercentage())
			));
		}
		snapshots.sort(qualificationComparator());
		return snapshots;
	}

	private List<Map<String, Object>> snapshotMaps(List<QualificationSnapshot> snapshots) {
		if (snapshots == null) {
			return List.of();
		}
		List<Map<String, Object>> mapped = new ArrayList<>();
		for (QualificationSnapshot snapshot : snapshots) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("type", snapshot.type());
			row.put("courseName", snapshot.courseName());
			row.put("collegeName", snapshot.collegeName());
			row.put("percentage", snapshot.percentage());
			mapped.add(row);
		}
		return mapped;
	}

	private java.util.Comparator<QualificationSnapshot> qualificationComparator() {
		return java.util.Comparator
				.comparing(QualificationSnapshot::type, java.util.Comparator.nullsFirst(String::compareToIgnoreCase))
				.thenComparing(QualificationSnapshot::courseName, java.util.Comparator.nullsFirst(String::compareToIgnoreCase))
				.thenComparing(QualificationSnapshot::collegeName, java.util.Comparator.nullsFirst(String::compareToIgnoreCase))
				.thenComparing(QualificationSnapshot::percentage, java.util.Comparator.nullsFirst(Double::compareTo));
	}

	private String clean(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private Double normalizePercentage(Double value) {
		if (value == null) {
			return null;
		}
		BigDecimal normalized = BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
		return normalized.doubleValue();
	}

	private record QualificationSnapshot(String type, String courseName, String collegeName, Double percentage) {
	}

	private String resolveReturnAction(String raw) {
		if (!StringUtils.hasText(raw)) {
			return "RETURNED_TO_STUDENT";
		}
		String normalized = raw.trim().toUpperCase(Locale.ENGLISH);
		return switch (normalized) {
			case "RETURNED_TO_BRANCH" -> "RETURNED_TO_BRANCH";
			case "RETURNED_TO_STUDENT", "RETURNED", "RESUBMITTED" -> "RETURNED_TO_STUDENT";
			default -> "RETURNED_TO_STUDENT";
		};
	}

	private String resolveReturnTargetLabel(String actionType) {
		return "RETURNED_TO_BRANCH".equals(resolveReturnAction(actionType)) ? "Branch" : "Student";
	}

	private String normalizeReceiveBackParty(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		String normalized = raw.trim().toUpperCase(Locale.ENGLISH);
		if ("STUDENT".equals(normalized)) {
			return "Student";
		}
		if ("BRANCH".equals(normalized)) {
			return "Branch";
		}
		return raw.trim();
	}

	@Override
	public List<FileUpload> addUpload(Long admissionId, MultipleUploadRequest req) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		
		List<FileUpload> fileTosave = new ArrayList<>();
		List<Map<String, Object>> uploadChanges = new ArrayList<>();
		for(UploadRequest uploadReq:req.getFiles()) {
			DocumentType dt = null;
			String docTypeCode = uploadReq.getDocTypeCode();
			String labelText = uploadReq.getLabel();
			boolean isOthersPlaceholder = StringUtils.hasText(docTypeCode)
					&& docTypeCode.toUpperCase(Locale.ROOT).matches("OTHERS\\d+");

			// "Add Other Document" rows arrive with placeholder codes like OTHERS1 / OTHERS2.
			// Some installs have those placeholders seeded in document_type, so a plain
			// findByCode would resolve them and the upload would be saved under the
			// useless placeholder row. Promote first whenever the placeholder pattern is
			// present and the user typed a non-empty label.
			if (isOthersPlaceholder && StringUtils.hasText(labelText)) {
				String slug = slugifyDocTypeCode(labelText);
				if (StringUtils.hasText(slug)) {
					dt = lookupService.getOrCreateDocType(slug, labelText.trim(), Boolean.FALSE);
				}
			}
			if (dt == null && StringUtils.hasText(docTypeCode) && !isOthersPlaceholder) {
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
			Long prevFileId = f.getFileId();
			String prevFilename = f.getFilename();
			String prevLabel = f.getLabel();
			String prevMimeType = f.getMimeType();
			Integer prevSize = f.getSizeBytes();
			String prevStorageUrl = f.getStorageUrl();
			String prevSha = f.getSha256();
			
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

			Map<String, Object> fieldChanges = new LinkedHashMap<>();
			addChange(fieldChanges, "filename", prevFilename, f.getFilename());

			Map<String, Object> change = new LinkedHashMap<>();
			change.put("fileId", prevFileId);
			change.put("docTypeCode", dt != null ? dt.getCode() : null);
			change.put("installmentId", feeInstallment != null ? feeInstallment.getInstallmentId() : null);
			change.put("changes", fieldChanges);
			if (!fieldChanges.isEmpty()) {
				uploadChanges.add(change);
			}
		}
		if(!fileTosave.isEmpty())
			fileTosave = this.uploadRepo.saveAll(fileTosave);
		audit(a, "DOCUMENT_UPLOADS_UPDATED", null, Map.of("admissionId", admissionId), uploadChanges);
		return fileTosave;
		
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdmissionOtherPaymentDto> listOtherPayments(Long admissionId) {
		admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		return admissionOtherPaymentRepository.findByAdmissionAdmissionIdOrderByPaidOnDescPaymentIdDesc(admissionId)
				.stream()
				.map(this::toOtherPaymentDto)
				.toList();
	}

	@Override
	public AdmissionOtherPaymentDto addOtherPayment(Long admissionId, AdmissionOtherPaymentRequest request) {
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		if (request == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Amount must be greater than zero.");
		}

		AdmissionOtherPayment payment = new AdmissionOtherPayment();
		payment.setAdmission(admission);
		payment.setAmount(request.getAmount());
		payment.setReturnedAmount(BigDecimal.ZERO);
		payment.setPaidOn(request.getPaidOn() != null ? request.getPaidOn() : LocalDate.now());
		payment.setTxnRef(normalizeToNull(request.getTxnRef()));
		payment.setCategory(normalizeToNull(request.getCategory()));
		payment.setRemarks(normalizeToNull(request.getRemarks()));
		payment.setReceivedBy(normalizeToNull(request.getReceivedBy()));
		payment.setPaymentMode(resolvePaymentMode(request.getMode()));
		payment.setPaymentType(normalizePaymentType(request.getPaymentType()));

		UploadRequest receipt = request.getReceipt();
		if (receipt != null && StringUtils.hasText(receipt.getStorageUrl())) {
			payment.setReceiptName(normalizeToNull(receipt.getFilename()));
			payment.setReceiptMimeType(normalizeToNull(receipt.getMimeType()));
			payment.setReceiptSizeBytes(receipt.getSizeBytes());
			payment.setReceiptStorageUrl(normalizeToNull(receipt.getStorageUrl()));
			payment.setReceiptSha256(normalizeToNull(receipt.getSha256()));
		}

		payment = admissionOtherPaymentRepository.save(payment);
		payment = admissionOtherPaymentRepository.save(invoiceService.generateInvoiceForOtherPayment(admission, payment));
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "otherPayment.amount", null, payment.getAmount());
		addChange(changes, "otherPayment.paidOn", null, payment.getPaidOn());
		addChange(changes, "otherPayment.mode", null,
				payment.getPaymentMode() != null ? payment.getPaymentMode().getCode() : null);
		addChange(changes, "otherPayment.txnRef", null, payment.getTxnRef());
		addChange(changes, "otherPayment.category", null, payment.getCategory());
		addChange(changes, "otherPayment.remarks", null, payment.getRemarks());
		addChange(changes, "otherPayment.receivedBy", null, payment.getReceivedBy());
		addChange(changes, "otherPayment.receiptName", null, payment.getReceiptName());
		addChange(changes, "otherPayment.invoiceNumber", null, payment.getInvoiceNumber());
		audit(admission, "OTHER_PAYMENT_ADDED", request.getReceivedBy(),
				Map.of("paymentId", payment.getPaymentId()), changes);
		return toOtherPaymentDto(payment);
	}

	@Override
	public AdmissionOtherPaymentDto addOtherPaymentReturn(Long admissionId, AdmissionOtherPaymentReturnRequest request) {
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		if (request == null || request.getReferencePaymentId() == null) {
			throw new IllegalArgumentException("Reference payment is required.");
		}
		if (request.getReturnAmount() == null || request.getReturnAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Return amount must be greater than zero.");
		}

		AdmissionOtherPayment reference = admissionOtherPaymentRepository.findById(request.getReferencePaymentId())
				.orElseThrow(() -> new IllegalArgumentException("Reference payment not found: " + request.getReferencePaymentId()));
		if (!Objects.equals(reference.getAdmission().getAdmissionId(), admissionId)) {
			throw new IllegalArgumentException("Reference payment does not belong to this admission.");
		}
		if (reference.getReferencePayment() != null) {
			throw new IllegalArgumentException("Cannot return against a return payment.");
		}

		BigDecimal existingReturned = reference.getReturnedAmount() != null ? reference.getReturnedAmount() : BigDecimal.ZERO;
		BigDecimal updatedReturned = existingReturned.add(request.getReturnAmount());
		if (updatedReturned.compareTo(reference.getAmount()) > 0) {
			throw new IllegalArgumentException("Return amount exceeds original payment amount.");
		}

		AdmissionOtherPayment payment = new AdmissionOtherPayment();
		payment.setAdmission(admission);
		payment.setReferencePayment(reference);
		payment.setAmount(request.getReturnAmount().negate());
		payment.setReturnedAmount(BigDecimal.ZERO);
		payment.setPaidOn(request.getPaidOn() != null ? request.getPaidOn() : LocalDate.now());
		payment.setTxnRef(normalizeToNull(request.getTxnRef()));
		payment.setCategory(normalizeToNull(request.getCategory()));
		payment.setRemarks(normalizeToNull(request.getRemarks()));
		payment.setReceivedBy(normalizeToNull(request.getReceivedBy()));
		payment.setPaymentMode(resolvePaymentMode(request.getMode()));
		payment.setPaymentType(normalizePaymentType(request.getPaymentType()));

		UploadRequest receipt = request.getReceipt();
		if (receipt != null && StringUtils.hasText(receipt.getStorageUrl())) {
			payment.setReceiptName(normalizeToNull(receipt.getFilename()));
			payment.setReceiptMimeType(normalizeToNull(receipt.getMimeType()));
			payment.setReceiptSizeBytes(receipt.getSizeBytes());
			payment.setReceiptStorageUrl(normalizeToNull(receipt.getStorageUrl()));
			payment.setReceiptSha256(normalizeToNull(receipt.getSha256()));
		}

		reference.setReturnedAmount(updatedReturned);
		admissionOtherPaymentRepository.save(reference);
		payment = admissionOtherPaymentRepository.save(payment);

		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "otherPayment.return.referencePaymentId", null, reference.getPaymentId());
		addChange(changes, "otherPayment.return.amount", null, payment.getAmount());
		addChange(changes, "otherPayment.return.paidOn", null, payment.getPaidOn());
		addChange(changes, "otherPayment.returnedAmount", existingReturned, updatedReturned);
		addChange(changes, "otherPayment.return.receiptName", null, payment.getReceiptName());
		audit(admission, "OTHER_PAYMENT_RETURN_ADDED", request.getReceivedBy(),
				Map.of("paymentId", payment.getPaymentId()), changes);
		return toOtherPaymentDto(payment);
	}

	@Override
	public AdmissionOtherPaymentDto updateOtherPayment(Long admissionId, Long paymentId, AdmissionOtherPaymentRequest request) {
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		AdmissionOtherPayment payment = admissionOtherPaymentRepository.findById(paymentId)
				.orElseThrow(() -> new IllegalArgumentException("Other payment not found: " + paymentId));
		if (!Objects.equals(payment.getAdmission().getAdmissionId(), admissionId)) {
			throw new IllegalArgumentException("Payment does not belong to this admission.");
		}
		if (request == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Amount must be greater than zero.");
		}
		BigDecimal existingReturned = payment.getReturnedAmount() != null ? payment.getReturnedAmount() : BigDecimal.ZERO;
		if (request.getAmount().compareTo(existingReturned) < 0) {
			throw new IllegalArgumentException("Amount cannot be less than the total already returned (" + existingReturned + ").");
		}

		BigDecimal previousAmount = payment.getAmount();
		String previousMode = payment.getPaymentMode() != null ? payment.getPaymentMode().getCode() : null;
		String previousType = payment.getPaymentType();
		String previousTxnRef = payment.getTxnRef();
		String previousCategory = payment.getCategory();
		String previousRemarks = payment.getRemarks();
		LocalDate previousPaidOn = payment.getPaidOn();

		payment.setAmount(request.getAmount());
		payment.setPaidOn(request.getPaidOn() != null ? request.getPaidOn() : payment.getPaidOn());
		payment.setTxnRef(normalizeToNull(request.getTxnRef()));
		payment.setCategory(normalizeToNull(request.getCategory()));
		payment.setRemarks(normalizeToNull(request.getRemarks()));
		payment.setPaymentMode(resolvePaymentMode(request.getMode()));
		payment.setPaymentType(normalizePaymentType(request.getPaymentType()));

		UploadRequest receipt = request.getReceipt();
		if (receipt != null && StringUtils.hasText(receipt.getStorageUrl())) {
			payment.setReceiptName(normalizeToNull(receipt.getFilename()));
			payment.setReceiptMimeType(normalizeToNull(receipt.getMimeType()));
			payment.setReceiptSizeBytes(receipt.getSizeBytes());
			payment.setReceiptStorageUrl(normalizeToNull(receipt.getStorageUrl()));
			payment.setReceiptSha256(normalizeToNull(receipt.getSha256()));
		}

		payment = admissionOtherPaymentRepository.save(payment);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "otherPayment.amount", previousAmount, payment.getAmount());
		addChange(changes, "otherPayment.paidOn", previousPaidOn, payment.getPaidOn());
		addChange(changes, "otherPayment.mode", previousMode,
				payment.getPaymentMode() != null ? payment.getPaymentMode().getCode() : null);
		addChange(changes, "otherPayment.paymentType", previousType, payment.getPaymentType());
		addChange(changes, "otherPayment.txnRef", previousTxnRef, payment.getTxnRef());
		addChange(changes, "otherPayment.category", previousCategory, payment.getCategory());
		addChange(changes, "otherPayment.remarks", previousRemarks, payment.getRemarks());
		audit(admission, "OTHER_PAYMENT_UPDATED", request.getReceivedBy(),
				Map.of("paymentId", paymentId), changes);
		return toOtherPaymentDto(payment);
	}

	@Override
	public void deleteOtherPayment(Long admissionId, Long paymentId) {
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		AdmissionOtherPayment payment = admissionOtherPaymentRepository.findById(paymentId)
				.orElseThrow(() -> new IllegalArgumentException("Other payment not found: " + paymentId));
		if (!Objects.equals(payment.getAdmission().getAdmissionId(), admissionId)) {
			throw new IllegalArgumentException("Payment does not belong to this admission.");
		}
		if (payment.getReferencePayment() == null) {
			List<AdmissionOtherPayment> returns = admissionOtherPaymentRepository.findByReferencePaymentPaymentId(paymentId);
			if (!returns.isEmpty()) {
				throw new IllegalArgumentException("Cannot delete payment with linked return records.");
			}
		} else {
			AdmissionOtherPayment reference = payment.getReferencePayment();
			BigDecimal existingReturned = reference.getReturnedAmount() != null ? reference.getReturnedAmount() : BigDecimal.ZERO;
			BigDecimal adjusted = existingReturned.subtract(payment.getAmount().abs());
			if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
				adjusted = BigDecimal.ZERO;
			}
			reference.setReturnedAmount(adjusted);
			admissionOtherPaymentRepository.save(reference);
		}
		safeDeleteLocalFile(payment.getInvoiceFilePath());
		admissionOtherPaymentRepository.delete(payment);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "otherPayment.deletedPaymentId", null, paymentId);
		audit(admission, "OTHER_PAYMENT_DELETED", null, Map.of("paymentId", paymentId), changes);
	}

	@Override
	public AdmissionOtherPaymentDto verifyOtherPaymentByAccountHead(Long paymentId, String verifiedBy) {
		AdmissionOtherPayment payment = admissionOtherPaymentRepository.findById(paymentId)
				.orElseThrow(() -> new IllegalArgumentException("Other payment not found: " + paymentId));
		if (Boolean.TRUE.equals(payment.getIsAccountHeadVerified())) {
			return toOtherPaymentDto(payment);
		}
		payment.setIsAccountHeadVerified(Boolean.TRUE);
		payment.setAccountHeadVerifiedBy(verifiedBy);
		payment.setAccountHeadVerifiedAt(LocalDateTime.now());
		AdmissionOtherPayment saved = admissionOtherPaymentRepository.save(payment);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "otherPayment.accountHeadVerified", false, true);
		addChange(changes, "otherPayment.accountHeadVerifiedBy", null, verifiedBy);
		Admission2 admission = saved.getAdmission();
		audit(admission, "OTHER_PAYMENT_ACCOUNT_HEAD_VERIFIED", verifiedBy,
				Map.of("paymentId", paymentId), changes);
		return toOtherPaymentDto(saved);
	}

	@Override
	public FeeInstallment upsertInstallment(Long admissionId, int studyYear, int installmentNo, BigDecimal amountDue,
			LocalDate dueDate, String mode, String receivedBy, String status) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		
	
		Optional<FeeInstallment> existingOpt = feeRepo
				.findByAdmissionAdmissionIdAndStudyYearAndInstallmentNo(admissionId, studyYear, installmentNo);
		FeeInstallment fee = existingOpt
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
		String prevMode = fee.getPaymentMode() != null ? fee.getPaymentMode().getCode() : null;
		String prevStatus = fee.getStatus();
		String prevReceivedBy = fee.getReceivedBy();
		BigDecimal prevAmountDue = fee.getAmountDue();
		LocalDate prevDueDate = fee.getDueDate();
		Long prevInstallmentId = fee.getInstallmentId();
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
		fee = feeRepo.save(fee);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "installmentId", prevInstallmentId, fee.getInstallmentId());
		addChange(changes, "studyYear", fee.getStudyYear(), fee.getStudyYear());
		addChange(changes, "installmentNo", fee.getInstallmentNo(), fee.getInstallmentNo());
		addChange(changes, "amountDue", prevAmountDue, fee.getAmountDue());
		addChange(changes, "dueDate", prevDueDate, fee.getDueDate());
		addChange(changes, "status", prevStatus, fee.getStatus());
		addChange(changes, "paymentMode", prevMode, fee.getPaymentMode() != null ? fee.getPaymentMode().getCode() : null);
		addChange(changes, "receivedBy", prevReceivedBy, fee.getReceivedBy());
		audit(a, "INSTALLMENT_UPDATED", receivedBy, Map.of("installmentId", fee.getInstallmentId()), changes);
		return fee;
	}
	@Override
	public FeeInstallment upsertInstallment(Long admissionId, int studyYear, int installmentNo, BigDecimal amountDue,
			LocalDate dueDate, String mode, String receivedBy, String status, Double yearlyFeesAmount,String txnRef, String role) {
		Admission2 a = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		
		
		List<YearlyFees> yearlyFeeRows = this.yearlyFeesRepository.findByAdmissionAdmissionIdAndYear(admissionId, studyYear);
		if (yearlyFeeRows.size() > 1) {
			log.warn("Duplicate YearlyFees rows found for admissionId={} year={} count={}. Using latest row and continuing.", admissionId, studyYear, yearlyFeeRows.size());
		}
		YearlyFees yearlyFees = yearlyFeeRows.stream()
				.max(java.util.Comparator.comparing(YearlyFees::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
				.orElse(null);
		
		if(yearlyFees==null) {
			yearlyFees = new YearlyFees();
			yearlyFees.setYear(studyYear);
			yearlyFees.setAdmission(a);
		}
		
		Double prevYearlyFees = yearlyFees.getFees();
		yearlyFees.setFees(yearlyFeesAmount);
		this.yearlyFeesRepository.save(yearlyFees);
		

		Optional<FeeInstallment> existingOpt = feeRepo
				.findByAdmissionAdmissionIdAndStudyYearAndInstallmentNo(admissionId, studyYear, installmentNo);
		FeeInstallment fee = existingOpt
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
		String prevMode = fee.getPaymentMode() != null ? fee.getPaymentMode().getCode() : null;
		String prevStatus = fee.getStatus();
		String prevReceivedBy = fee.getReceivedBy();
		String prevTxnRef = fee.getTxnRef();
		BigDecimal prevAmountDue = fee.getAmountDue();
		BigDecimal prevAmountPaid = fee.getAmountPaid();
		LocalDate prevDueDate = fee.getDueDate();
		Long prevInstallmentId = fee.getInstallmentId();
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
		fee = feeRepo.save(fee);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "installmentId", prevInstallmentId, fee.getInstallmentId());
		addChange(changes, "amountDue", prevAmountDue, fee.getAmountDue());
		addChange(changes, "amountPaid", prevAmountPaid, fee.getAmountPaid());
		addChange(changes, "dueDate", prevDueDate, fee.getDueDate());
		addChange(changes, "status", prevStatus, fee.getStatus());
		addChange(changes, "paymentMode", prevMode, fee.getPaymentMode() != null ? fee.getPaymentMode().getCode() : null);
		addChange(changes, "receivedBy", prevReceivedBy, fee.getReceivedBy());
		addChange(changes, "txnRef", prevTxnRef, fee.getTxnRef());
		addChange(changes, "yearlyFees", prevYearlyFees, yearlyFees.getFees());
		audit(a, "INSTALLMENT_UPDATED", receivedBy, Map.of("installmentId", fee.getInstallmentId()), changes);
		return fee;
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
		if (request == null || request.getAmount() == null
				|| request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
			throw new IllegalArgumentException("Payment amount must be non-zero.");
		}
		LocalDate paidOn = request.getPaidOn() != null ? request.getPaidOn() : LocalDate.now();
		LocalDate today = LocalDate.now();
		if (paidOn.isAfter(today) || paidOn.isBefore(today.minusDays(1))) {
			throw new IllegalArgumentException("Backdating allowed only up to 1 day.");
		}
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));

		List<FeeInstallment> installments = feeRepo.findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admissionId);
		if (installments.isEmpty()) {
			throw new IllegalArgumentException("No installments found for admission: " + admissionId);
		}
		Map<Long, Map<String, Object>> beforeInstallments = new LinkedHashMap<>();
		for (FeeInstallment installment : installments) {
			Map<String, Object> snapshot = new LinkedHashMap<>();
			snapshot.put("amountPaid", installment.getAmountPaid());
			snapshot.put("status", installment.getStatus());
			snapshot.put("isVerified", installment.getIsVerified());
			snapshot.put("paidOn", installment.getPaidOn());
			beforeInstallments.put(installment.getInstallmentId(), snapshot);
		}

		PaymentModeMaster paymentMode = resolvePaymentMode(request.getMode());
		String paymentType = normalizePaymentType(request.getPaymentType());
		if (log.isInfoEnabled()) {
			log.info("applyPartialPayment start admissionId={} admissionBranchId={} lectureBranchId={} amount={} paidOn={} role={} paymentType={} mode={} modeCode={} txnRef={}",
					admissionId,
					admission.getAdmissionBranch() != null ? admission.getAdmissionBranch().getId() : null,
					admission.getLectureBranch() != null ? admission.getLectureBranch().getId() : null,
					request.getAmount(),
					paidOn,
					role,
					paymentType,
					request.getMode(),
					paymentMode != null ? paymentMode.getCode() : null,
					request.getTxnRef());
		}

		BigDecimal remaining = request.getAmount();
		List<FeeInstallmentPayment> payments = new ArrayList<>();
		String paymentGroupId = java.util.UUID.randomUUID().toString();
		List<UploadRequest> partialReceipts = new ArrayList<>();
		if (request.getReceipts() != null) {
			for (UploadRequest r : request.getReceipts()) {
				if (r != null && StringUtils.hasText(r.getStorageUrl())) {
					partialReceipts.add(r);
				}
			}
		}
		if (partialReceipts.isEmpty() && request.getReceipt() != null
				&& StringUtils.hasText(request.getReceipt().getStorageUrl())) {
			partialReceipts.add(request.getReceipt());
		}
		boolean partialReceiptsAttached = false;
		if (remaining.compareTo(BigDecimal.ZERO) > 0) {
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
					installment.setPaidOn(paidOn);
				}
				feeRepo.save(installment);

				String paymentStatus = verified ? "Paid" : "Under Verification";
				FeeInstallmentPayment payment = FeeInstallmentPayment.builder()
						.installment(installment)
						.amount(applied)
						.paymentGroupId(paymentGroupId)
						.paymentMode(paymentMode)
						.paymentType(paymentType)
						.txnRef(request.getTxnRef())
						.remarks(request.getRemarks())
						.receivedBy(request.getReceivedBy())
						.status(paymentStatus)
						.isVerified(verified)
						.verifiedBy(verified ? request.getReceivedBy() : null)
						.verifiedAt(verified ? LocalDateTime.now() : null)
						.paidOn(paidOn)
						.build();
				payment = paymentRepo.save(payment);
				log.info("applyPartialPayment saved paymentId={} admissionId={} installmentId={} amount={} paymentType={} modeCode={} status={} paidOn={} createdAt={} groupId={}",
						payment.getPaymentId(),
						admissionId,
						installment.getInstallmentId(),
						payment.getAmount(),
						payment.getPaymentType(),
						payment.getPaymentMode() != null ? payment.getPaymentMode().getCode() : null,
						payment.getStatus(),
						payment.getPaidOn(),
						payment.getCreatedAt(),
						payment.getPaymentGroupId());
				payments.add(payment);

				if (payment.getAmount() != null
						&& payment.getAmount().compareTo(BigDecimal.ZERO) > 0
						&& !hasAllocationInvoiceForPayment(payment.getPaymentId())) {
					invoiceService.generateInvoiceForPayment(admission, installment, payment);
				}

				if (!partialReceiptsAttached) {
					for (UploadRequest receipt : partialReceipts) {
						FileUpload upload = buildPaymentReceiptUpload(admission, installment, payment, receipt);
						uploadRepo.save(upload);
					}
					partialReceiptsAttached = true;
				}

				remaining = remaining.subtract(applied);
			}

			if (remaining.compareTo(BigDecimal.ZERO) > 0) {
				throw new IllegalArgumentException("Payment exceeds pending installment totals.");
			}
			List<Map<String, Object>> installmentChanges = new ArrayList<>();
			for (FeeInstallment installment : installments) {
				Map<String, Object> before = beforeInstallments.get(installment.getInstallmentId());
				if (before == null) {
					continue;
				}
				Map<String, Object> fieldChanges = new LinkedHashMap<>();
				addChange(fieldChanges, "amountPaid", before.get("amountPaid"), installment.getAmountPaid());
				addChange(fieldChanges, "status", before.get("status"), installment.getStatus());
				addChange(fieldChanges, "isVerified", before.get("isVerified"), installment.getIsVerified());
				addChange(fieldChanges, "paidOn", before.get("paidOn"), installment.getPaidOn());
				if (!fieldChanges.isEmpty()) {
					Map<String, Object> change = new LinkedHashMap<>();
					change.put("installmentId", installment.getInstallmentId());
					change.put("changes", fieldChanges);
					installmentChanges.add(change);
				}
			}
			List<Map<String, Object>> paymentDetails = new ArrayList<>();
			for (FeeInstallmentPayment payment : payments) {
				Map<String, Object> paymentMap = new LinkedHashMap<>();
				paymentMap.put("paymentId", payment.getPaymentId());
				paymentMap.put("installmentId", payment.getInstallment() != null
						? payment.getInstallment().getInstallmentId()
						: null);
				paymentMap.put("amount", payment.getAmount());
				paymentMap.put("status", payment.getStatus());
				paymentMap.put("paidOn", payment.getPaidOn());
				paymentDetails.add(paymentMap);
			}
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("amount", request.getAmount());
			details.put("mode", request.getMode());
			details.put("txnRef", request.getTxnRef());
			details.put("remarks", request.getRemarks());
			details.put("payments", paymentDetails);
			audit(admission, "PAYMENT_APPLIED", request.getReceivedBy(), details, installmentChanges);
			return payments;
		}

		BigDecimal reversalRemaining = remaining.abs();
		List<FeeInstallment> reversalOrder = new ArrayList<>(installments);
		java.util.Collections.reverse(reversalOrder);
		for (FeeInstallment installment : reversalOrder) {
			if (reversalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
				break;
			}
			BigDecimal amountPaid = installment.getAmountPaid() == null ? BigDecimal.ZERO : installment.getAmountPaid();
			if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
				continue;
			}
			BigDecimal applied = reversalRemaining.min(amountPaid);
			BigDecimal newPaid = amountPaid.subtract(applied);
			installment.setAmountPaid(newPaid);
			BigDecimal amountDue = installment.getAmountDue() == null ? BigDecimal.ZERO : installment.getAmountDue();
			String computedStatus;
			if (newPaid.compareTo(BigDecimal.ZERO) == 0) {
				computedStatus = "Un Paid";
				installment.setPaidOn(null);
			} else if (newPaid.compareTo(amountDue) >= 0) {
				computedStatus = "Paid";
			} else {
				computedStatus = "Partial Received";
			}
			boolean verified = isRoleOneOf(role, "HO");
			installment.setStatus(verified ? computedStatus : "Under Verification");
			installment.setIsVerified(verified);
			feeRepo.save(installment);

			String paymentStatus = verified ? "Paid" : "Under Verification";
			FeeInstallmentPayment payment = FeeInstallmentPayment.builder()
					.installment(installment)
					.amount(applied.negate())
					.paymentGroupId(paymentGroupId)
					.paymentMode(paymentMode)
					.paymentType(paymentType)
					.txnRef(request.getTxnRef())
					.remarks(request.getRemarks())
					.receivedBy(request.getReceivedBy())
					.status(paymentStatus)
					.isVerified(verified)
					.verifiedBy(verified ? request.getReceivedBy() : null)
					.verifiedAt(verified ? LocalDateTime.now() : null)
					.paidOn(paidOn)
					.build();
			payment = paymentRepo.save(payment);
			log.info("applyPartialPayment saved reversal paymentId={} admissionId={} installmentId={} amount={} paymentType={} modeCode={} status={} paidOn={} createdAt={} groupId={}",
					payment.getPaymentId(),
					admissionId,
					installment.getInstallmentId(),
					payment.getAmount(),
					payment.getPaymentType(),
					payment.getPaymentMode() != null ? payment.getPaymentMode().getCode() : null,
					payment.getStatus(),
					payment.getPaidOn(),
					payment.getCreatedAt(),
					payment.getPaymentGroupId());
			payments.add(payment);

			if (!partialReceiptsAttached) {
				for (UploadRequest receipt : partialReceipts) {
					FileUpload upload = buildPaymentReceiptUpload(admission, installment, payment, receipt);
					uploadRepo.save(upload);
				}
				partialReceiptsAttached = true;
			}

			reversalRemaining = reversalRemaining.subtract(applied);
		}

		if (reversalRemaining.compareTo(BigDecimal.ZERO) > 0) {
			throw new IllegalArgumentException("Reversal exceeds paid installment totals.");
		}
		List<Map<String, Object>> installmentChanges = new ArrayList<>();
		for (FeeInstallment installment : installments) {
			Map<String, Object> before = beforeInstallments.get(installment.getInstallmentId());
			if (before == null) {
				continue;
			}
			Map<String, Object> fieldChanges = new LinkedHashMap<>();
			addChange(fieldChanges, "amountPaid", before.get("amountPaid"), installment.getAmountPaid());
			addChange(fieldChanges, "status", before.get("status"), installment.getStatus());
			addChange(fieldChanges, "isVerified", before.get("isVerified"), installment.getIsVerified());
			addChange(fieldChanges, "paidOn", before.get("paidOn"), installment.getPaidOn());
			if (!fieldChanges.isEmpty()) {
				Map<String, Object> change = new LinkedHashMap<>();
				change.put("installmentId", installment.getInstallmentId());
				change.put("changes", fieldChanges);
				installmentChanges.add(change);
			}
		}
		List<Map<String, Object>> paymentDetails = new ArrayList<>();
		for (FeeInstallmentPayment payment : payments) {
			Map<String, Object> paymentMap = new LinkedHashMap<>();
			paymentMap.put("paymentId", payment.getPaymentId());
			paymentMap.put("installmentId", payment.getInstallment() != null
					? payment.getInstallment().getInstallmentId()
					: null);
			paymentMap.put("amount", payment.getAmount());
			paymentMap.put("status", payment.getStatus());
			paymentMap.put("paidOn", payment.getPaidOn());
			paymentDetails.add(paymentMap);
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("amount", request.getAmount());
		details.put("mode", request.getMode());
		details.put("txnRef", request.getTxnRef());
		details.put("remarks", request.getRemarks());
		details.put("payments", paymentDetails);
		audit(admission, "PAYMENT_APPLIED", request.getReceivedBy(), details, installmentChanges);
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

	private AdmissionOtherPaymentDto toOtherPaymentDto(AdmissionOtherPayment payment) {
		BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
		BigDecimal returnedAmount = payment.getReturnedAmount() != null ? payment.getReturnedAmount() : BigDecimal.ZERO;
		return AdmissionOtherPaymentDto.builder()
				.paymentId(payment.getPaymentId())
				.admissionId(payment.getAdmission() != null ? payment.getAdmission().getAdmissionId() : null)
				.amount(amount)
				.returnedAmount(returnedAmount)
				.netAmount(amount.subtract(returnedAmount))
				.paidOn(payment.getPaidOn())
				.paymentMode(payment.getPaymentMode() != null ? payment.getPaymentMode().getCode() : null)
				.paymentType(payment.getPaymentType())
				.txnRef(payment.getTxnRef())
				.category(payment.getCategory())
				.remarks(payment.getRemarks())
				.receivedBy(payment.getReceivedBy())
				.referencePaymentId(payment.getReferencePayment() != null ? payment.getReferencePayment().getPaymentId() : null)
				.receiptName(payment.getReceiptName())
				.receiptUrl(payment.getReceiptStorageUrl())
				.invoiceNumber(payment.getInvoiceNumber())
				.invoiceUrl(payment.getInvoiceDownloadUrl())
				.build();
	}

	private PaymentModeMaster resolvePaymentMode(String mode) {
		if (!StringUtils.hasText(mode)) {
			return null;
		}
		String normalized = mode.trim();
		PaymentModeMaster byMode = service.getByMode(normalized);
		if (byMode != null) {
			return byMode;
		}
		Optional<PaymentModeMaster> byCode = service.findByCode(normalized);
		if (byCode.isPresent()) {
			PaymentModeMaster existing = byCode.get();
			if (!existing.isActive()) {
				existing.setActive(true);
				existing = service.save(existing);
			}
			return existing;
		}
		PaymentModeMaster created = PaymentModeMaster.builder()
				.code(normalized)
				.label(normalized)
				.displayOrder(0)
				.active(true)
				.build();
		return service.save(created);
	}

	private String normalizePaymentType(String paymentType) {
		if (!StringUtils.hasText(paymentType)) {
			throw new IllegalArgumentException("Payment type is required.");
		}
		String trimmed = paymentType.trim();
		String normalized = trimmed.toLowerCase(Locale.ENGLISH);
		return switch (normalized) {
			case "cash" -> "Cash";
			case "cheque", "check" -> "Cheque";
			case "online" -> "Online";
			// The UI lets the user pick "Others" and type a free-text payment
			// type (Bank Draft, NEFT-QR, …). Preserve whatever they typed
			// verbatim so history views show the original wording.
			default -> trimmed;
		};
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
		BigDecimal prevAmountPaid = fee.getAmountPaid();
		LocalDate prevPaidOn = fee.getPaidOn();
		String prevMode = fee.getPaymentMode() != null ? fee.getPaymentMode().getCode() : null;
		String prevTxnRef = fee.getTxnRef();
		fee.setAmountPaid(amountPaid);
		fee.setPaidOn(paidOn);
		fee.setPaymentMode(mode);
		fee.setTxnRef(txnRef);
		fee = feeRepo.save(fee);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "amountPaid", prevAmountPaid, fee.getAmountPaid());
		addChange(changes, "paidOn", prevPaidOn, fee.getPaidOn());
		addChange(changes, "paymentMode", prevMode, fee.getPaymentMode() != null ? fee.getPaymentMode().getCode() : null);
		addChange(changes, "txnRef", prevTxnRef, fee.getTxnRef());
		Admission2 admission = fee.getAdmission();
		audit(admission, "PAYMENT_UPDATED", null, Map.of("installmentId", installmentId), changes);
		return fee;
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
				AdmissionStatus previousStatus = admission.getStatus();
				Boolean previousTemporary = admission.getTemporaryAdmission();
				admission.setStatus(AdmissionStatus.ADMITTED);
				// Confirming an admission also promotes any temporary flag off.
				if (Boolean.TRUE.equals(previousTemporary)) {
					admission.setTemporaryAdmission(false);
				}
				this.admissionRepo.save(admission);
				allocateSeatIfPossible(admission);

				// Persist the transition in admission_audit so the record
				// carries "was ever Temporary" and "who confirmed it" forever.
				Map<String, Object> changes = new LinkedHashMap<>();
				addChange(changes, "status", previousStatus, admission.getStatus());
				if (Boolean.TRUE.equals(previousTemporary)
						&& !Boolean.TRUE.equals(admission.getTemporaryAdmission())) {
					addChange(changes, "temporaryAdmission", true, false);
				}
				audit(admission, "ADMISSION_ACKNOWLEDGED", null,
						Map.of("admissionId", id), changes);
			}
			return admission;
		}
		return null;
	}

	@Override
	@Transactional
	public Admission2 confirmTemporaryAdmission(Long id, String actor) {
		Admission2 admission = admissionRepo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + id));
		if (Boolean.TRUE.equals(admission.getTemporaryAdmission())) {
			admission.setTemporaryAdmission(false);
			admission = admissionRepo.save(admission);
			Map<String, Object> changes = new LinkedHashMap<>();
			addChange(changes, "temporaryAdmission", true, false);
			audit(admission, "TEMPORARY_ADMISSION_CLEARED", actor,
					Map.of("admissionId", id), changes);
		}
		return admission;
	}

	@Override
	public Admission2 updateCollegeVerification(Long admissionId, String status, String actor) {
		Admission2 admission = admissionRepo.findById(admissionId)
				.orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
		CollegeVerificationStatus prevStatus = admission.getCollegeVerificationStatus();
		String prevVerifiedBy = admission.getCollegeVerifiedBy();
		LocalDateTime prevVerifiedAt = admission.getCollegeVerifiedAt();
		String prevRejectedBy = admission.getCollegeRejectedBy();
		LocalDateTime prevRejectedAt = admission.getCollegeRejectedAt();

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

		admission = admissionRepo.save(admission);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "collegeVerificationStatus", prevStatus, admission.getCollegeVerificationStatus());
		addChange(changes, "collegeVerifiedBy", prevVerifiedBy, admission.getCollegeVerifiedBy());
		addChange(changes, "collegeVerifiedAt", prevVerifiedAt, admission.getCollegeVerifiedAt());
		addChange(changes, "collegeRejectedBy", prevRejectedBy, admission.getCollegeRejectedBy());
		addChange(changes, "collegeRejectedAt", prevRejectedAt, admission.getCollegeRejectedAt());
		audit(admission, "COLLEGE_VERIFICATION_UPDATED", actor, Map.of("status", newStatus.name()), changes);
		return admission;
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
		Boolean previousTemporary = admission.getTemporaryAdmission();
		if (status == AdmissionStatus.ADMITTED && Boolean.TRUE.equals(previousTemporary)) {
			admission.setTemporaryAdmission(false);
		}
		admission = admissionRepo.save(admission);
		Map<String, Object> changes = new LinkedHashMap<>();
		addChange(changes, "status", previous, admission.getStatus());
		if (Boolean.TRUE.equals(previousTemporary) && !Boolean.TRUE.equals(admission.getTemporaryAdmission())) {
			addChange(changes, "temporaryAdmission", true, false);
		}
		audit(admission, "ADMISSION_STATUS_UPDATED", null, Map.of("admissionId", admissionId), changes);
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

	private void decrementOnHoldSeats(Long collegeId, Long courseId) {
		CollegeCourse cc = collegeCourseRepository
				.findByCollegeCollegeIdAndCourseCourseId(collegeId, courseId)
				.orElseThrow(() -> new IllegalArgumentException("College course mapping not found."));
		int onHold = cc.getOnHoldSeats() == null ? 0 : cc.getOnHoldSeats();
		if (onHold > 0) {
			cc.setOnHoldSeats(onHold - 1);
			collegeCourseRepository.save(cc);
		}
	}

	private void incrementAllocatedSeats(Long collegeId, Long courseId) {
		CollegeCourse cc = collegeCourseRepository
				.findByCollegeCollegeIdAndCourseCourseId(collegeId, courseId)
				.orElseThrow(() -> new IllegalArgumentException("College course mapping not found."));
		int allocated = cc.getAllocatedSeats() == null ? 0 : cc.getAllocatedSeats();
		cc.setAllocatedSeats(allocated + 1);
		cc.setLastAllocatedAt(OffsetDateTime.now());
		collegeCourseRepository.save(cc);
	}

	private void decrementAllocatedSeats(Long collegeId, Long courseId) {
		CollegeCourse cc = collegeCourseRepository
				.findByCollegeCollegeIdAndCourseCourseId(collegeId, courseId)
				.orElseThrow(() -> new IllegalArgumentException("College course mapping not found."));
		int allocated = cc.getAllocatedSeats() == null ? 0 : cc.getAllocatedSeats();
		if (allocated > 0) {
			cc.setAllocatedSeats(allocated - 1);
			collegeCourseRepository.save(cc);
		}
	}

	private void adjustSeatCountsForCourseChange(AdmissionStatus status,
			Long oldCollegeId,
			Long oldCourseId,
			Long newCollegeId,
			Long newCourseId) {
		boolean changed = !Objects.equals(oldCollegeId, newCollegeId)
				|| !Objects.equals(oldCourseId, newCourseId);
		if (!changed) {
			return;
		}
		boolean admitted = status == AdmissionStatus.ADMITTED;
		if (oldCollegeId != null && oldCourseId != null) {
			if (admitted) {
				decrementAllocatedSeats(oldCollegeId, oldCourseId);
			} else {
				decrementOnHoldSeats(oldCollegeId, oldCourseId);
			}
		}
		if (newCollegeId != null && newCourseId != null) {
			if (admitted) {
				incrementAllocatedSeats(newCollegeId, newCourseId);
			} else {
				incrementOnHoldSeats(newCollegeId, newCourseId);
			}
		}
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

	private void addChange(Map<String, Object> changes, String field, Object before, Object after) {
		if (!valuesEqual(before, after)) {
			Map<String, Object> delta = new LinkedHashMap<>();
			delta.put("label", buildLabel(field));
			delta.put("before", before);
			delta.put("after", after);
			changes.put(field, delta);
		}
	}

	private String normalizeToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	/**
	 * Convert a human-typed document name into a stable upper-snake slug usable
	 * as a {@code document_type.code} (max 32 chars). Non-alphanumerics collapse
	 * to {@code _}; leading/trailing underscores are trimmed. Returns {@code null}
	 * if nothing useful remains (e.g. label was just punctuation).
	 */
	private String slugifyDocTypeCode(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String upper = value.trim().toUpperCase(Locale.ROOT);
		String slug = upper.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (slug.isEmpty()) {
			return null;
		}
		if (slug.length() > 32) {
			slug = slug.substring(0, 32).replaceAll("_+$", "");
		}
		return slug.isEmpty() ? null : slug;
	}

	private String branchName(BranchMaster branch) {
		if (branch == null) {
			return null;
		}
		if (StringUtils.hasText(branch.getName())) {
			return branch.getName().trim();
		}
		if (StringUtils.hasText(branch.getCode())) {
			return branch.getCode().trim();
		}
		return branch.getId() != null ? String.valueOf(branch.getId()) : null;
	}

	private void safeDeleteLocalFile(String filePath) {
		if (!StringUtils.hasText(filePath)) {
			return;
		}
		try {
			if (r2InvoiceStorageService.isEnabled()) {
				String r2Key = r2InvoiceStorageService.extractKey(filePath);
				if (StringUtils.hasText(r2Key)) {
					r2InvoiceStorageService.delete(r2Key);
					return;
				}
			}
			Files.deleteIfExists(Path.of(filePath));
		} catch (Exception ignored) {
		}
	}

	private boolean hasAllocationInvoiceForPayment(Long paymentId) {
		return invoiceRepo.findByPayment_PaymentId(paymentId).stream()
				.anyMatch(invoice -> !InvoiceServiceImpl.isPaymentGroupInvoiceNumber(invoice.getInvoiceNumber()));
	}

	private String buildLabel(String field) {
		if (!StringUtils.hasText(field)) {
			return field;
		}
		String normalized = field.replace('.', ' ').replace('_', ' ').trim();
		StringBuilder out = new StringBuilder();
		char prev = 0;
		for (int i = 0; i < normalized.length(); i++) {
			char ch = normalized.charAt(i);
			if (Character.isUpperCase(ch) && i > 0 && Character.isLetterOrDigit(prev) && prev != ' ') {
				out.append(' ');
			}
			out.append(ch);
			prev = ch;
		}
		String spaced = out.toString().trim();
		if (spaced.isEmpty()) {
			return field;
		}
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private boolean valuesEqual(Object before, Object after) {
		if (before instanceof BigDecimal b1 && after instanceof BigDecimal b2) {
			return b1.compareTo(b2) == 0;
		}
		return Objects.equals(before, after);
	}

	private boolean isEmptyAuditPayload(Object value) {
		if (value == null) {
			return true;
		}
		if (value instanceof Map<?, ?> map) {
			return map.isEmpty();
		}
		if (value instanceof List<?> list) {
			return list.isEmpty();
		}
		return false;
	}

	private String resolveAuditActor(String fallback) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated()) {
			Object principal = auth.getPrincipal();
			if (principal instanceof Jwt jwt) {
				String nameClaim = jwt.getClaimAsString("name");
				if (StringUtils.hasText(nameClaim)) {
					return nameClaim;
				}
				String preferred = jwt.getClaimAsString("preferred_username");
				if (StringUtils.hasText(preferred)) {
					return preferred;
				}
				String email = jwt.getClaimAsString("email");
				if (StringUtils.hasText(email)) {
					return email;
				}
			}
			String name = auth.getName();
			if (StringUtils.hasText(name) && !"anonymousUser".equalsIgnoreCase(name)) {
				return name;
			}
		}
		return StringUtils.hasText(fallback) ? fallback : null;
	}

	private void audit(Admission2 admission, String action, String actor, Object details, Object changedFields) {
		if (admission == null || action == null || isEmptyAuditPayload(changedFields)) {
			return;
		}
		String resolvedActor = resolveAuditActor(actor);
		admissionAuditService.record(admission, action, resolvedActor, details, changedFields);
	}
}
