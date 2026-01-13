package com.bothash.admissionservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUploadHistoryItem {
    private UUID uploadId;
    private String fileName;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private int totalRows;
    private int successRows;
    private int failedRows;
    private String status;
    private boolean errorReportAvailable;
}
