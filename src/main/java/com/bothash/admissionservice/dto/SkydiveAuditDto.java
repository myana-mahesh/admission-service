package com.bothash.admissionservice.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkydiveAuditDto {
    private Long auditId;
    private String triggeredBy;
    private String triggeredRole;
    private LocalDateTime triggeredAt;
    private String actionName;
    private String status;
    private String detailsJson;
}
