package com.bothash.admissionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAdditionalQualificationDto {
    private Long qualificationId;
    private Long admissionId;
    private String qualificationType;
    private String courseName;
    private String collegeName;
    private Double percentage;
}
