package com.bothash.admissionservice.dto;

import lombok.Data;

@Data
public class StudentAddressDto {
    private Long id;              // or studentAddressId (match backend JSON)
    private String type;          // "current", "permanent", etc.
    private AddressDto address;
}
