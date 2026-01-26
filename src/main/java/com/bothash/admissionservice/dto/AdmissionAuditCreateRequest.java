package com.bothash.admissionservice.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionAuditCreateRequest {
    private String action;
    private String changedBy;
    private Map<String, Object> details;
    private Map<String, Object> changedFields;
}
