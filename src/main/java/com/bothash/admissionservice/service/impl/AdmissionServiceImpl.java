package com.bothash.admissionservice.service.impl;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bothash.admissionservice.dto.AdmissionDTO;
import com.bothash.admissionservice.entity.Admission;
import com.bothash.admissionservice.repository.AdmissionRepository;
import com.bothash.admissionservice.service.AdmissionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdmissionServiceImpl implements AdmissionService {

    private final AdmissionRepository repository;

    @Override
    public Admission createAdmission(AdmissionDTO dto) {
        Admission admission = Admission.builder()
                .applicationNumber("ABS" + System.currentTimeMillis())
                .studentName(dto.getStudentName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .course(dto.getCourse())
                .branch(dto.getBranch())
                .referralName(dto.getReferralName())
                .referralContact(dto.getReferralContact())
                .feePlanCode(dto.getFeePlanCode())
                .admissionStatus("PENDING")
                .admissionDate(LocalDate.now())
                .seatStatus("WAITLISTED")
                .createdBy("system")
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(admission);
    }

    @Override
    public List<Admission> getAllAdmissions() {
        return repository.findAll();
    }

    @Override
    public Admission getAdmissionById(Long id) {
        return repository.findById(id).orElseThrow();
    }

    @Override
    public Admission updateAdmission(Long id, AdmissionDTO dto) {
        Admission admission = getAdmissionById(id);
        admission.setStudentName(dto.getStudentName());
        admission.setEmail(dto.getEmail());
        admission.setPhone(dto.getPhone());
        admission.setCourse(dto.getCourse());
        admission.setBranch(dto.getBranch());
        admission.setReferralName(dto.getReferralName());
        admission.setReferralContact(dto.getReferralContact());
        admission.setFeePlanCode(dto.getFeePlanCode());
        return repository.save(admission);
    }

    @Override
    public void updateStatus(Long id, String status) {
        Admission admission = getAdmissionById(id);
        admission.setAdmissionStatus(status);
        repository.save(admission);
    }

    @Override
    public boolean checkSeatAvailability(String course, String branch) {
        return !repository.existsByCourseAndBranch(course, branch);
    }
}
