package com.bothash.admissionservice.dto;

import lombok.Data;

@Data
public class AdmissionAccessMetaDto {
    private Long admissionId;
    private Boolean branchApproved;
    private String batch;
    private Long courseId;
    private Long admissionBranchId;
    private Long lectureBranchId;
}

