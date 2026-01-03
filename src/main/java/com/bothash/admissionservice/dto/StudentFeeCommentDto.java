package com.bothash.admissionservice.dto;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentFeeCommentDto {

    private Long commentId;
    private Long studentId;
    private String studentName;
    private String comment;
    private String commentedBy;
    private String commentType;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
