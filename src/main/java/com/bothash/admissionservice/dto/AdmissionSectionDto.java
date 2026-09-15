package com.bothash.admissionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionSectionDto {
    private Long id;
    private String name;
    private Integer sortOrder;
    private Boolean active;
}
