package com.bothash.admissionservice.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import com.bothash.admissionservice.dto.SkydiveAuditDto;
import com.bothash.admissionservice.entity.SkydiveAudit;
import com.bothash.admissionservice.repository.SkydiveAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SkydiveService {
    private static final int DETAILS_JSON_FALLBACK_LIMIT = 240;

    private final SkydiveAuditRepository skydiveAuditRepository;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext applicationContext;

    public SkydiveAuditDto execute(String triggeredBy, String triggeredRole) {
        String actor = triggeredBy != null && !triggeredBy.isBlank() ? triggeredBy : "unknown";
        String role = triggeredRole != null && !triggeredRole.isBlank() ? triggeredRole : "UNKNOWN";

        SkydiveAudit audit = skydiveAuditRepository.save(SkydiveAudit.builder()
                .triggeredBy(actor)
                .triggeredRole(role)
                .triggeredAt(LocalDateTime.now())
                .actionName("erp shutdown initiated")
                .status("STARTED")
                .detailsJson(buildDetailsJson("started", actor, role, null))
                .build());

        try {
            audit.setStatus("SUCCESS");
            audit.setDetailsJson(buildDetailsJson("success", actor, role, null));
            skydiveAuditRepository.save(audit);
            scheduleShutdown();
        } catch (Exception ex) {
            audit.setStatus("FAILED");
            audit.setDetailsJson(buildDetailsJson("failed", actor, role, ex.getMessage()));
            skydiveAuditRepository.save(audit);
            throw new IllegalStateException("ERP shutdown failed", ex);
        }

        return toDto(audit);
    }

    public List<SkydiveAuditDto> listAudits() {
        return skydiveAuditRepository.findTop100ByOrderByTriggeredAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"message\":\"Unable to serialize details\"}";
        }
    }

    private void scheduleShutdown() {
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            SpringApplication.exit(applicationContext, () -> 0);
            System.exit(0);
        }, "erp-admission-service-shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    private String buildDetailsJson(String phase, String actor, String role, String error) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("phase", phase);
        compact.put("target", "admission-service");
        compact.put("triggeredBy", actor);
        compact.put("triggeredRole", role);
        if (error != null && !error.isBlank()) {
            compact.put("error", trim(error, 80));
        }

        String json = toJson(compact);
        if (json.length() <= DETAILS_JSON_FALLBACK_LIMIT) {
            return json;
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("phase", phase);
        fallback.put("target", "admission-service");
        fallback.put("actor", trim(actor, 48));
        if (error != null && !error.isBlank()) {
            fallback.put("error", trim(error, 40));
        }
        return trim(toJson(fallback), DETAILS_JSON_FALLBACK_LIMIT);
    }

    private String trim(String value, int limit) {
        if (value == null) {
            return null;
        }
        return value.length() <= limit ? value : value.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private SkydiveAuditDto toDto(SkydiveAudit audit) {
        return SkydiveAuditDto.builder()
                .auditId(audit.getAuditId())
                .triggeredBy(audit.getTriggeredBy())
                .triggeredRole(audit.getTriggeredRole())
                .triggeredAt(audit.getTriggeredAt())
                .actionName(audit.getActionName())
                .status(audit.getStatus())
                .detailsJson(audit.getDetailsJson())
                .build();
    }
}
