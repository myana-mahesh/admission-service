package com.bothash.admissionservice.controller;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bothash.admissionservice.dto.SkydiveAuditDto;
import com.bothash.admissionservice.service.SkydiveService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/skydive")
@RequiredArgsConstructor
public class SkydiveController {

    private final SkydiveService skydiveService;

    @PostMapping
    public ResponseEntity<SkydiveAuditDto> trigger(@AuthenticationPrincipal Jwt jwt) {
        ensureHoOrSuperAdmin(jwt);
        String actor = jwt != null ? jwt.getClaimAsString("preferred_username") : "unknown";
        String role = hasRole(jwt, "SUPER_ADMIN") ? "SUPER_ADMIN" : "HO";
        return ResponseEntity.ok(skydiveService.execute(actor, role));
    }

    @GetMapping("/audits")
    public ResponseEntity<List<SkydiveAuditDto>> audits(@AuthenticationPrincipal Jwt jwt) {
        ensureHoOrSuperAdmin(jwt);
        return ResponseEntity.ok(skydiveService.listAudits());
    }

    private void ensureHoOrSuperAdmin(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }
        if (hasRole(jwt, "HO") || hasRole(jwt, "SUPER_ADMIN")) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HO or SUPER_ADMIN role required");
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
