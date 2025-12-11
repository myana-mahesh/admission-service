package com.bothash.admissionservice.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LookupCreateRequest {
  private String code;
  private String name;
}
