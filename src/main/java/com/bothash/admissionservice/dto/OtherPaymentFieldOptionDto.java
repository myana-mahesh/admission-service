package com.bothash.admissionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtherPaymentFieldOptionDto {
    private Long id;
    private String label;
    private String value;
    private Integer sortOrder;
    private Boolean active;
}
