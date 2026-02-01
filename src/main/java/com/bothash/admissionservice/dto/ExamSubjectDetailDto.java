package com.bothash.admissionservice.dto;

import java.time.LocalDate;

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
public class ExamSubjectDetailDto {
    private Long subjectId;
    private String subjectName;
    private Integer totalMarks;
    private Integer passingMarks;
    private LocalDate examDate;
}
