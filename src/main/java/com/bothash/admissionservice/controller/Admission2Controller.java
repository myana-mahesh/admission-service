package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.dto.*;
import com.bothash.admissionservice.entity.*;
import com.bothash.admissionservice.enumpackage.AdmissionStatus;
import com.bothash.admissionservice.repository.Admission2Repository;
import com.bothash.admissionservice.service.Admission2Service;

import com.bothash.admissionservice.service.AdmissionCancellationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admissions")
@Validated
@RequiredArgsConstructor
public class Admission2Controller {
  private final Admission2Service admissionService;
  private final Admission2Repository admission2Repository;
  private final AdmissionCancellationService cancellationService;


  @PostMapping
  public ResponseEntity<Admission2> create( @RequestBody CreateAdmissionRequest req){
    Admission2 a = admissionService.createAdmission(
        req);
    return ResponseEntity.ok(a);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Admission2> get(@PathVariable Long id){
    return admissionService.getById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}/office")
  public ResponseEntity<Admission2> updateOffice(@PathVariable Long id,  @RequestBody OfficeUpdateRequest req){
    return ResponseEntity.ok(
        admissionService.updateOfficeDetails(id, req.getLastCollege(), req.getCollegeAttended(),
            req.getCollegeLocation(), req.getRemarks(), req.getExamDueDate(), req.getDateOfAdmission())
    );
  }

  @PostMapping("/{id}/documents")
  public ResponseEntity<AdmissionDocument> setReceived(@PathVariable Long id,  @RequestBody DocReceivedRequest req){
    return ResponseEntity.ok(admissionService.setDocumentReceived(id, req.getDocTypeCode(), req.isReceived()));
  }

  @PostMapping("/{id}/uploads")
  public ResponseEntity<?> addUpload(@PathVariable Long id,  @RequestBody MultipleUploadRequest req){
    return ResponseEntity.ok(
        admissionService.addUpload(id, req)
    );
  }

  @PostMapping("/{id}/installments")
  public ResponseEntity<FeeInstallment> upsertInstallment(@PathVariable Long id,  @RequestBody InstallmentUpsertRequest req){
    return ResponseEntity.ok(
        admissionService.upsertInstallment(id, req.getStudyYear(), req.getInstallmentNo(), req.getAmountDue(), req.getDueDate(),req.getMode(),req.getReceivedBy(),req.getStatus())
    );
  }
  @PostMapping("/{id}/installments/bulk")
  @Transactional
  public ResponseEntity<List<FeeInstallment>> upsertInstallments(
      @PathVariable Long id,
      @RequestParam String role,
      @RequestBody  List< InstallmentUpsertRequest> items
  ) {
    // basic same-request duplicate guard
    var seen = new java.util.HashSet<String>();
    for (var it : items) {
      String key = it.getStudyYear() + ":" + it.getInstallmentNo();
      if (!seen.add(key)) {
        throw new IllegalArgumentException("Duplicate (studyYear, installmentNo) in request: " + key);
      }
    }
    return ResponseEntity.ok(admissionService.upsertInstallments(id, items,role));
  }

  @PostMapping("/{id}/payments")
  public ResponseEntity<List<FeeInstallmentPayment>> applyPartialPayment(
      @PathVariable Long id,
      @RequestParam String role,
      @RequestBody PartialPaymentRequest request
  ) {
    return ResponseEntity.ok(admissionService.applyPartialPayment(id, request, role));
  }


//  @PostMapping("/installments/{installmentId}/payment")
//  public ResponseEntity<FeeInstallment> recordPayment(@PathVariable Long installmentId,  @RequestBody PaymentRequest req){
//    return ResponseEntity.ok(
//        admissionService.recordPayment(installmentId, req.getAmountPaid(), req.getPaidOn(), req.getPaymentMode(), req.getTxnRef())
//    );
//  }

  @PostMapping("/{id}/signoff/head")
  public ResponseEntity<AdmissionSignoff> signHead(@PathVariable Long id){
    return ResponseEntity.ok(admissionService.signByHead(id));
  }
  @PostMapping("/{id}/signoff/clerk")
  public ResponseEntity<AdmissionSignoff> signClerk(@PathVariable Long id){
    return ResponseEntity.ok(admissionService.signByClerk(id));
  }
  @PostMapping("/{id}/signoff/counsellor")
  public ResponseEntity<AdmissionSignoff> signCounsellor(@PathVariable Long id){
    return ResponseEntity.ok(admissionService.signByCounsellor(id));
  }

  @PostMapping("/{id}/college-verification")
  public ResponseEntity<Admission2> updateCollegeVerification(
          @PathVariable Long id,
          @RequestParam String status,
          @RequestParam String actor
  ) {
    return ResponseEntity.ok(admissionService.updateCollegeVerification(id, status, actor));
  }

  @GetMapping
  public ResponseEntity<List<Admission2>> listByCourseAndYear(@RequestParam String courseCode, @RequestParam String yearLabel){
    return ResponseEntity.ok(admissionService.listByCourseAndYear(courseCode, yearLabel));
  }
  
  @PostMapping("/send-acknowledgement")
	public ResponseEntity<Admission2> sendAcknowledgement(@RequestParam Long id) {
		
		Admission2 admisison = this.admissionService.acknowledgeAdmission(id);
		if(admisison!=null) {
			return new ResponseEntity<Admission2>(admisison, HttpStatus.OK);
		}

		return new ResponseEntity<Admission2>(admisison, HttpStatus.INTERNAL_SERVER_ERROR);
	}



}
