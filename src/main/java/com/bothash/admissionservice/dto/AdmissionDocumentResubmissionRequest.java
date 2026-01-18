package com.bothash.admissionservice.dto;

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
public class AdmissionDocumentResubmissionRequest {
    private String resubmittedTo;
    private String resubmissionReason;
    private String resubmittedBy;
}
