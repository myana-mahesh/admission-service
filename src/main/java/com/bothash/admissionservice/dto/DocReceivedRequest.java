package com.bothash.admissionservice.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocReceivedRequest {
  private String docTypeCode; // SSC/HSC/LC_TC/MIG/AADHAAR/PHOTO
  private boolean received;
}
