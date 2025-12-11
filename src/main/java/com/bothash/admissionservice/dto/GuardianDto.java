package com.bothash.admissionservice.dto;

import lombok.Data;

@Data
public class GuardianDto {
    private Long guardianId;      // optional, if present in JSON
    private String relation;      // "Father", "Mother", etc. (enum name as String)
    private String fullName;
    private String mobile;
}
