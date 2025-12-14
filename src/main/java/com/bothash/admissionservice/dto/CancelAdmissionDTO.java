package com.bothash.admissionservice.dto;

import lombok.Data;

@Data
public class CancelAdmissionDTO {
    private Long admissionId;
    private Double cancelCharges;
    private String remark;
    private String handlingPerson;
    private String refundProofFileName;
    private String role;
}
