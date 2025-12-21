package com.bothash.admissionservice.service.impl;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bothash.admissionservice.service.Admission2Service;
import com.bothash.admissionservice.dto.CreateAdmissionRequest;
import com.bothash.admissionservice.dto.InstallmentUpsertRequest;
import com.bothash.admissionservice.dto.MultipleUploadRequest;
import com.bothash.admissionservice.dto.UploadRequest;
import com.bothash.admissionservice.entity.AcademicYear;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.AdmissionDocument;
import com.bothash.admissionservice.entity.AdmissionSignoff;
import com.bothash.admissionservice.entity.College;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.DocumentType;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FileUpload;
import com.bothash.admissionservice.entity.PaymentModeMaster;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.entity.YearlyFees;
import com.bothash.admissionservice.enumpackage.AdmissionStatus;
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
	private final AdmissionSignoffRepository signoffRepo;
	private final PaymentModeService service;
	
	
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

		Admission2 a = this.admissionRepo.findByStudentStudentIdAndYearYearIdAndCourseCourseId(req.getStudentId(), year.getYearId(),course.getCourseId());
		if(a == null) {
			a = new Admission2();
			a.setStatus(AdmissionStatus.PENDING);
			a.setFormDate(req.getFormDate());
		}
		a.setStudent(student);
		a.setYear(year);
		a.setCourse(course);
		a.setCollege(college);
		a.setTotalFees(req.getTotalFees());
		a.setDiscount(req.getDiscount());
		a.setDiscountRemark(req.getDiscountRemark());
		a.setDiscountRemarkOther(req.getDiscountRemarkOther());
		a.setNoOfInstallments(req.getNoOfInstallments());
		a=admissionRepo.save(a);
		
		return this.updateOfficeDetails(a.getAdmissionId(), req.getOfficeUpdateRequest().getLastCollege(), req.getOfficeUpdateRequest().getCollegeAttended(), req.getOfficeUpdateRequest().getCollegeLocation(), 
				req.getOfficeUpdateRequest().getRemarks(), req.getOfficeUpdateRequest().getExamDueDate(), req.getOfficeUpdateRequest().getDateOfAdmission());
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
			DocumentType dt = docTypeRepo.findByCode(uploadReq.getDocTypeCode()).orElse(null); // uploads may be misc (null)

			FileUpload f = new FileUpload();
			if(uploadReq.getInstallmentId() == null) {
				f = this.uploadRepo.findByAdmissionAdmissionIdAndDocTypeDocTypeId(admissionId, dt.getDocTypeId());
			}else {
				f = this.uploadRepo.findByAdmissionAdmissionIdAndDocTypeDocTypeIdAndInstallmentInstallmentId(admissionId, dt.getDocTypeId(),uploadReq.getInstallmentId());
			}
				
			
			FeeInstallment feeInstallment = this.feeRepo.findByInstallmentId(uploadReq.getInstallmentId()).orElse(null);
			
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
					if(status!=null && status.equalsIgnoreCase("Paid") && role.equalsIgnoreCase("BRANCH_USER")) {
						f.setStatus("Under Verification");
					}else {
						f.setStatus(status);
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
		if(status!=null && status.equalsIgnoreCase("Paid") && role.equalsIgnoreCase("BRANCH_USER")) {
			fee.setStatus("Under Verification");
		}else if(status!=null){
			fee.setStatus(status);
		}
		
		fee.setTxnRef(txnRef);
		fee.setReceivedBy(receivedBy);
		fee.setAmountDue(amountDue);
		fee.setDueDate(dueDate);
		return feeRepo.save(fee);
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
			admission.setStatus(AdmissionStatus.ADMITTED);
			this.admissionRepo.save(admission);
			return admission;
		}
		return null;
	}


	@Transactional
	public Admission2 getAdmission(Long admissionId) {
		return admissionRepo.findById(admissionId)
				.orElseThrow(() -> new RuntimeException("Admission not found"));
	}

	@Transactional
	public void updateStatus(Long admissionId, AdmissionStatus status) {
		Admission2 admission = getAdmission(admissionId);
		admission.setStatus(status);
		admissionRepo.save(admission);
	}
}
