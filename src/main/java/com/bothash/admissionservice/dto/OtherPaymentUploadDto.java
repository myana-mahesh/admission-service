package com.bothash.admissionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherPaymentUploadDto {
    private Long uploadId;
    private Long fieldId;
    private String fieldLabel;
    private String filename;
    private String storageUrl;
    private String mimeType;
    private Integer sizeBytes;
}
