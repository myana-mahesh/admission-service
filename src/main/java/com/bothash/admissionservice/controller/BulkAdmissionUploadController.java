package com.bothash.admissionservice.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.bothash.admissionservice.dto.BulkUploadResponse;
import com.bothash.admissionservice.dto.BulkUploadHistoryItem;
import com.bothash.admissionservice.service.BulkAdmissionUploadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admissions/bulk-upload")
@RequiredArgsConstructor
public class BulkAdmissionUploadController {
    private static final String TEMPLATE_NAME = "admission-bulk-template.xlsx";
    private static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final BulkAdmissionUploadService bulkUploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ensureAdminOrHo(jwt);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required.");
        }
        String uploadedBy = jwt != null ? jwt.getClaimAsString("preferred_username") : "bulk-upload";
        BulkUploadResponse response = bulkUploadService.processUpload(file, uploadedBy, "2025-2026");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> template(@AuthenticationPrincipal Jwt jwt) {
        ensureAdminOrHo(jwt);
        byte[] bytes = bulkUploadService.generateTemplate();
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEMPLATE_NAME + "\"")
                .contentType(MediaType.parseMediaType(EXCEL_MIME))
                .contentLength(bytes.length)
                .body(resource);
    }

    @GetMapping("/history")
    public ResponseEntity<java.util.List<BulkUploadHistoryItem>> history(@AuthenticationPrincipal Jwt jwt) {
        ensureAdminOrHo(jwt);
        return ResponseEntity.ok(bulkUploadService.listHistory());
    }

    @GetMapping("/{uploadId}/error-report")
    public ResponseEntity<Resource> errorReport(
            @PathVariable UUID uploadId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ensureAdminOrHo(jwt);
        try {
            Path path = bulkUploadService.resolveErrorReport(uploadId);
            byte[] bytes = Files.readAllBytes(path);
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"error-report-" + uploadId + ".xlsx\"")
                    .contentType(MediaType.parseMediaType(EXCEL_MIME))
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Error report not found.");
        }
    }

    private void ensureAdminOrHo(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }
        if (hasRole(jwt, "HO") || hasRole(jwt, "ADMIN") || hasRole(jwt, "SUPER_ADMIN")) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HO or ADMIN role required");
    }

    private boolean hasRole(Jwt jwt, String role) {
        if (jwt == null || role == null) {
            return false;
        }
        String roleWithPrefix = "ROLE_" + role;
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            Object roles = realmMap.get("roles");
            if (roles instanceof Collection<?> roleList) {
                if (roleList.contains(role) || roleList.contains(roleWithPrefix)) {
                    return true;
                }
            }
        }
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof Collection<?> roleList) {
            if (roleList.contains(role) || roleList.contains(roleWithPrefix)) {
                return true;
            }
        }
        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> resMap) {
            for (Object entry : resMap.values()) {
                if (entry instanceof Map<?, ?> clientMap) {
                    Object roles = clientMap.get("roles");
                    if (roles instanceof Collection<?> roleList) {
                        if (roleList.contains(role) || roleList.contains(roleWithPrefix)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
