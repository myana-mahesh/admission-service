package com.bothash.admissionservice.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.AdmissionAudit;
import com.bothash.admissionservice.repository.AdmissionAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdmissionAuditService {
    private final AdmissionAuditRepository auditRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AdmissionAudit record(Admission2 admission, String action, String changedBy,
                                 Object details, Object changedFields) {
        if (admission == null || action == null) {
            return null;
        }
        AdmissionAudit audit = AdmissionAudit.builder()
                .admission(admission)
                .action(action)
                .changedBy(changedBy)
                .changedAt(OffsetDateTime.now())
                .detailsJson(toJson(details))
                .changedFieldsJson(toJson(changedFields))
                .build();
        return auditRepository.save(audit);
    }

    public java.util.List<AdmissionAudit> listByAdmission(Long admissionId) {
        if (admissionId == null) {
            return java.util.List.of();
        }
        return auditRepository.findByAdmissionAdmissionIdOrderByChangedAtDesc(admissionId);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
