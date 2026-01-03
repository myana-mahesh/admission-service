package com.bothash.admissionservice.dto;

import java.time.LocalDate;
import java.util.List;



import lombok.Data;

@Data
public class StudentDto {
	private Long studentId;
    private String absId;
    private String fullName;
    private LocalDate dob;
    private String gender;     // or use same enum name if shared
    private String aadhaar;
    private String nationality;
    private String religion;
    private String caste;
    private String email;
    private String mobile;
    private Integer age;
    private String batch;
    private String registrationNumber;
    private Long courseId;
    private String courseName;
    private List<GuardianDto> guardians;
    private List<StudentAddressDto> addresses;
}
