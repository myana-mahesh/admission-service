package com.bothash.admissionservice.dto;

import lombok.*;

import org.hibernate.annotations.processing.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import com.bothash.admissionservice.enumpackage.Gender;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStudentRequest {
	private String fullName;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dob;
	private Gender gender;
	
	private String aadhaar;
	
	private String email;
	private String nationality;
	private String religion;
	private String caste;
	private String mobile;
	private String absId;
	// Optional simple address fields for quick create
	private String addressLine1;
	private String city;
	private String state;
	private String pincode;
	// Optional guardians
	private String fatherName;
	private String fatherMobile;
	private String motherName;
	private String motherMobile;
	private Long studendId;
	
}
