package com.bothash.admissionservice.service;


import com.bothash.admissionservice.repository.Admission2Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bothash.admissionservice.dto.CancelAdmissionDTO;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.AdmissionCancellation;
import com.bothash.admissionservice.enumpackage.AdmissionStatus;
import com.bothash.admissionservice.repository.AdmissionCancellationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdmissionCancellationService {
    private final Admission2Repository admissionRepo;
    private final AdmissionService admissionService;
    private final AdmissionCancellationRepository cancellationRepository;
    private final Admission2Service admission2Service;
    /**
     * Step 1: Move admission status to UNDER_CANCELLATION
     */
    @Transactional
    public void initiateCancellation(Long admissionId) {
        admission2Service.updateStatus(admissionId, AdmissionStatus.UNDER_CANCELLATION);
    }

    /**
     * Step 2: Save cancellation details + change status to Cancelled
     */
    @Transactional
    public void confirmCancellation(CancelAdmissionDTO dto) {

        Admission2 admission = admission2Service.getAdmission(dto.getAdmissionId());

        // Check if already exists
        AdmissionCancellation existing = cancellationRepository.findByAdmissionAdmissionId(dto.getAdmissionId());

        if (existing != null) {
            // Update existing record
            existing.setCancelCharges(dto.getCancelCharges());
            existing.setRemark(dto.getRemark());
            existing.setHandlingPerson(dto.getHandlingPerson());
            
            if(dto.getRefundProofFileName()!=null && !dto.getRefundProofFileName().isEmpty())
            	existing.setRefundProofFileName(dto.getRefundProofFileName());
            
            cancellationRepository.save(existing);
        } else {
            // New record
            AdmissionCancellation cancellation = AdmissionCancellation.builder()
                    .admission(admission)
                    .cancelCharges(dto.getCancelCharges())
                    .remark(dto.getRemark())
                    .handlingPerson(dto.getHandlingPerson())
                    .refundProofFileName(dto.getRefundProofFileName())
                    .build();

            admission.setCancellation(cancellation);
            cancellationRepository.save(cancellation);
        }

        if(dto.getRole() != null && dto.getRole().equals("HO")){
            // Update admission status → CANCELLED
            admission.setStatus(AdmissionStatus.CANCELLED);
        }else{
            admission.setStatus(AdmissionStatus.UNDER_CANCELLATION);
        }
        admissionRepo.save(admission);
    }

    @Transactional
    public CancelAdmissionDTO fetchCancelAdmDetails(Long admissionId) {

        Admission2 admission = admission2Service.getAdmission(admissionId);
        CancelAdmissionDTO cancelAdmissionDTO = new CancelAdmissionDTO();
        if(admission.getCancellation()!=null) {
        	cancelAdmissionDTO.setCancelCharges(admission.getCancellation().getCancelCharges());
            cancelAdmissionDTO.setRemark(admission.getCancellation().getRemark());
            cancelAdmissionDTO.setHandlingPerson(admission.getCancellation().getHandlingPerson());
            cancelAdmissionDTO.setRefundProofFileName(admission.getCancellation().getRefundProofFileName());
            cancelAdmissionDTO.setStudentAcknowledgementProofFileName(admission.getCancellation().getStudentAcknowledgementProofFileName());
        }
        return cancelAdmissionDTO; // may return null if not cancelled
    }

    public CancelAdmissionDTO updateCancelAdmissionDetails(CancelAdmissionDTO dto) {

        Admission2 admission = admission2Service.getAdmission(dto.getAdmissionId());

        // Check if already exists
        AdmissionCancellation entity = cancellationRepository.findByAdmissionAdmissionId(dto.getAdmissionId());


        entity.setCancelCharges(dto.getCancelCharges());
        entity.setHandlingPerson(dto.getHandlingPerson());
        entity.setRemark(dto.getRemark());
        entity.setRefundProofFileName(dto.getRefundProofFileName());
        entity.setStudentAcknowledgementProofFileName(dto.getStudentAcknowledgementProofFileName());

        cancellationRepository.save(entity);

        return dto;
    }

}
