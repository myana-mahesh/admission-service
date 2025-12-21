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
public class CollegeDto {
    private Long collegeId;
    private String code;
    private String name;
    private List<CollegeCourseDto> courses;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CollegeCourseDto {
        private Long courseId;
        private String courseCode;
        private String courseName;
        private Integer totalSeats;
    }
}
