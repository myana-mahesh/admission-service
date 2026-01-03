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
public class CollegeCourseSeatDto {
    private Long courseId;
    private String courseCode;
    private String courseName;
    private Integer totalSeats;
    private Integer onHoldSeats;
    private Integer utilizedSeats;
    private Integer remainingSeats;
}
