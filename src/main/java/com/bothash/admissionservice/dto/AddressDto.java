package com.bothash.admissionservice.dto;

import lombok.Data;

@Data
public class AddressDto {
    private Long addressId;       // if present in backend
    private String line1;
    private String city;
    private String state;
    private String pincode;
}