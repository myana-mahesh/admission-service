package com.bothash.admissionservice.dto;

import com.bothash.admissionservice.entity.HscDetails;
import com.bothash.admissionservice.entity.SscDetails;
import lombok.*;

import org.hibernate.annotations.processing.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import com.bothash.admissionservice.enumpackage.Gender;

import java.time.LocalDate;
import java.util.List;

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
	private String area;
	private String city;
	private String state;
	private String pincode;
	// Optional guardians
	private String fatherName;
	private String fatherMobile;
	private String motherName;
	private String motherMobile;
	private Long studendId;
	private String bloodGroup;
	private SscDetails sscDetails;
	private HscDetails hscDetails;
	private String batch;
	private String registrationNumber;
	private Integer age;
	private Long courseCode; // e.g., DPHARM
	private String academicYearLabel; // e.g., 2025-26
    private List<OtherPaymentFieldValueRequest> otherPayments;
	
}
