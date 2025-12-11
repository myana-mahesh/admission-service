package com.bothash.admissionservice.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfficeUpdateRequest {
	private String lastCollege;
	private String collegeAttended;
	private String collegeLocation;
	private String remarks;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate examDueDate;
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dateOfAdmission;
}
