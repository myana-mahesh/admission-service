package com.bothash.admissionservice.dto;

import java.time.LocalDate;

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
public class AdmissionDocumentReturnDto {
    private Long returnId;
    private Long admissionId;
    private String docTypeCode;
    private String docTypeName;
    private LocalDate returnedOn;
    private String reason;
    private String returnedBy;
    private String documentHandler;
    private String actionType;
    private String statusLabel;
    private String returnedTo;
    private String receivedBackFrom;
    private LocalDate resubmittedOn;
    private String resubmittedTo;
    private String resubmissionReason;
    private String resubmittedBy;
}
