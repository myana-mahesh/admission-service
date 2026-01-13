package com.bothash.admissionservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bulk_upload_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUploadJob {
    @Id
    private UUID id;

    @Column(length = 50, nullable = false)
    private String type;

    @Column(length = 255)
    private String fileName;

    @Column(length = 120)
    private String uploadedBy;

    private LocalDateTime uploadedAt;

    private Integer totalRows;
    private Integer successRows;
    private Integer failedRows;

    @Column(length = 30)
    private String status;

    @Column(length = 512)
    private String errorReportPath;
}
