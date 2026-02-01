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
public class ExamMasterRequest {
    private String examName;
    private Long courseId;
    private Integer maxFailedSubjects;
    private List<Long> branchIds;
    private List<Long> batchIds;
    private List<ExamSubjectDetailDto> subjects;
}
