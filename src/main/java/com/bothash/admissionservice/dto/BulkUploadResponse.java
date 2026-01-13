package com.bothash.admissionservice.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUploadResponse {
    private UUID uploadId;
    private String fileName;
    private int totalRows;
    private int successRows;
    private int failedRows;
    private String status;
    private boolean errorReportAvailable;
}
