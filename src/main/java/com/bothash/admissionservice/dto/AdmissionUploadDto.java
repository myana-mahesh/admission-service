package com.bothash.admissionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionUploadDto {
    private Long fileId;
    private String docTypeCode;
    private String label;
    private String filename;
    private String storageUrl;
    private Long installmentId;
    /** True for curated main docs (SSC/HSC/…); false or null for custom "Other Documents" promoted from Add Other Document. */
    private Boolean isMainDoc;
}
