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
public class CourseDocumentRequirementDto {
    private Long id;
    private Long courseId;
    private String courseName;
    private Long docTypeId;
    private String docTypeCode;
    private String docTypeName;
    private boolean optional;
}
