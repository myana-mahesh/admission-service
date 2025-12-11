package com.bothash.admissionservice.dto;



import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseFeeRequestDto {

    private Long courseId;     // null for create, not null for update
    private String code;
    private String name;
    private Integer years;

    private FeeTemplateDto feeTemplate;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FeeTemplateDto {
        private Long id;       // courseFeeTemplateId
        private String name;
        private BigDecimal totalAmount;
        private List<InstallmentDto> installments;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstallmentDto {
        private Long id;            // courseFeeTemplateInstallmentId
        private Integer sequence;
        private BigDecimal amount;
        private Integer dueDaysFromAdmission;
        private Integer yearNumber; 
    }

}


