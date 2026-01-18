package com.bothash.admissionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherPaymentUploadRequest {
    private Long fieldId;
    private String filename;
    private String mimeType;
    private Integer sizeBytes;
    private String storageUrl;
    private String sha256;
}
