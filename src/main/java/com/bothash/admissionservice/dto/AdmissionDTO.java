package com.bothash.admissionservice.dto;


import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionDTO {
    private String studentName;
    private String email;
    private String phone;
    private String course;
    private String branch;
    private String referralName;
    private String referralContact;
    private String feePlanCode;
}

