package com.bothash.admissionservice.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UploadRequest {
  private String docTypeCode; // nullable for misc
  private String filename;
  private String mimeType;
  private Integer sizeBytes;
  private String storageUrl;
  private String sha256;
  private String label;
  private Long   installmentId;      // use if referring to an existing installment
  private String installmentTempId; 
}
