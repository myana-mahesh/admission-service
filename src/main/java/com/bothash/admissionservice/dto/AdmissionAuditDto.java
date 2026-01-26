package com.bothash.admissionservice.dto;

import java.time.OffsetDateTime;

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
public class AdmissionAuditDto {
    private Long auditId;
    private Long admissionId;
    private String action;
    private String changedBy;
    private OffsetDateTime changedAt;
    private String detailsJson;
    private String changedFieldsJson;
}
