package com.bothash.admissionservice.dto;

import java.util.List;

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
public class ExamStudentRowDto {
    private Long admissionId;
    private Long studentId;
    private String studentName;
    private String absId;
    private String studentMobile;
    private String fatherMobile;
    private String motherMobile;
    private String collegeName;
    private List<ExamStudentMarkDto> marks;
    private String status;
}
