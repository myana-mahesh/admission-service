package com.bothash.admissionservice.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.OtherPaymentUploadDto;
import com.bothash.admissionservice.dto.OtherPaymentUploadRequest;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.OtherPaymentField;
import com.bothash.admissionservice.entity.OtherPaymentUpload;
import com.bothash.admissionservice.repository.Admission2Repository;
import com.bothash.admissionservice.repository.OtherPaymentFieldRepository;
import com.bothash.admissionservice.repository.OtherPaymentUploadRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/other-payments")
@RequiredArgsConstructor
public class OtherPaymentUploadController {
    private final Admission2Repository admissionRepository;
    private final OtherPaymentFieldRepository fieldRepository;
    private final OtherPaymentUploadRepository uploadRepository;

    @GetMapping("/{admissionId}/uploads")
    public ResponseEntity<List<OtherPaymentUploadDto>> listUploads(@PathVariable Long admissionId) {
        List<OtherPaymentUpload> uploads = uploadRepository.findByAdmission_AdmissionId(admissionId);
        return ResponseEntity.ok(toDtos(uploads));
    }

    @PostMapping("/{admissionId}/uploads")
    public ResponseEntity<List<OtherPaymentUploadDto>> addUploads(
            @PathVariable Long admissionId,
            @RequestBody List<OtherPaymentUploadRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Admission2 admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new IllegalArgumentException("Admission not found: " + admissionId));
        List<OtherPaymentUpload> uploads = new ArrayList<>();
        for (OtherPaymentUploadRequest request : requests) {
            if (request == null || request.getFieldId() == null) {
                continue;
            }
            OtherPaymentField field = fieldRepository.findById(request.getFieldId())
                    .orElseThrow(() -> new IllegalArgumentException("Other payment field not found: " + request.getFieldId()));
            if (!StringUtils.hasText(request.getFilename()) || !StringUtils.hasText(request.getStorageUrl())) {
                continue;
            }
            OtherPaymentUpload upload = new OtherPaymentUpload();
            upload.setAdmission(admission);
            upload.setField(field);
            upload.setFilename(request.getFilename());
            upload.setMimeType(request.getMimeType());
            upload.setSizeBytes(request.getSizeBytes());
            upload.setStorageUrl(request.getStorageUrl());
            upload.setSha256(request.getSha256());
            uploads.add(upload);
        }
        if (uploads.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<OtherPaymentUpload> saved = uploadRepository.saveAll(uploads);
        return ResponseEntity.ok(toDtos(saved));
    }

    private List<OtherPaymentUploadDto> toDtos(List<OtherPaymentUpload> uploads) {
        return uploads.stream()
                .map(upload -> OtherPaymentUploadDto.builder()
                        .uploadId(upload.getUploadId())
                        .fieldId(upload.getField() != null ? upload.getField().getId() : null)
                        .fieldLabel(upload.getField() != null ? upload.getField().getLabel() : null)
                        .filename(upload.getFilename())
                        .storageUrl(upload.getStorageUrl())
                        .mimeType(upload.getMimeType())
                        .sizeBytes(upload.getSizeBytes())
                        .build())
                .toList();
    }
}
