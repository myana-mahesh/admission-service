package com.bothash.admissionservice.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtherPaymentFieldDto {
    private Long id;
    private String label;
    private String inputType;
    private Boolean required;
    private Integer sortOrder;
    private Boolean active;
    private List<OtherPaymentFieldOptionDto> options;
}
