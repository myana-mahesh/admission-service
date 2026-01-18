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
public class AdmissionDocumentReturnRequest {
    private String docTypeCode;
    private LocalDate returnedOn;
    private String reason;
    private String returnedBy;
    private String actionType;
}
