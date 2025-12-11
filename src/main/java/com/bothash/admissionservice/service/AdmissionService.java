package com.bothash.admissionservice.service;

import java.util.List;

import com.bothash.admissionservice.dto.AdmissionDTO;
import com.bothash.admissionservice.entity.Admission;

public interface AdmissionService {
    Admission createAdmission(AdmissionDTO dto);
    List<Admission> getAllAdmissions();
    Admission getAdmissionById(Long id);
    Admission updateAdmission(Long id, AdmissionDTO dto);
    void updateStatus(Long id, String status);
    boolean checkSeatAvailability(String course, String branch);
}