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
public class OtherPaymentFieldValueRequest {
    private Long fieldId;
    private List<OtherPaymentValueEntryDto> entries;
}
