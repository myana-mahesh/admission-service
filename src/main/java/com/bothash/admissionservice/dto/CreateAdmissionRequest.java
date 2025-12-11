package com.bothash.admissionservice.dto;

import lombok.*;

import org.antlr.v4.runtime.misc.NotNull;
import org.springframework.format.annotation.DateTimeFormat;


import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAdmissionRequest {
	private Long studentId;
	private String academicYearLabel; // e.g., 2025-26
	private Long courseCode; // e.g., DPHARM
	private String formNo;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate formDate;
	private Double totalFees;
    
    private Double discount;
    
    private Integer noOfInstallments;
	private OfficeUpdateRequest officeUpdateRequest;
}
