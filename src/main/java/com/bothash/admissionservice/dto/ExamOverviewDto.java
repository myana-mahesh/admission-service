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
public class ExamOverviewDto {
    private Long examId;
    private String examName;
    private Long courseId;
    private String courseName;
    private List<ExamSubjectDetailDto> subjects;
    private List<ExamStudentRowDto> students;
    private long totalStudents;
    private int page;
    private int size;
    private Integer maxFailedSubjects;
}
