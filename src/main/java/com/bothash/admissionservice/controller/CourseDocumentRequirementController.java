package com.bothash.admissionservice.controller;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bothash.admissionservice.dto.CourseDocumentRequirementDto;
import com.bothash.admissionservice.dto.CourseDocumentRequirementRequest;
import com.bothash.admissionservice.service.CourseDocumentRequirementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/course-documents")
@RequiredArgsConstructor
@Slf4j
public class CourseDocumentRequirementController {

    private final CourseDocumentRequirementService requirementService;

    @GetMapping
    public List<CourseDocumentRequirementDto> listAll() {
        return requirementService.listAll();
    }

    @PostMapping
    public ResponseEntity<CourseDocumentRequirementDto> create(@RequestBody CourseDocumentRequirementRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ensureCanEdit(jwt, "course-documents:EDIT");
        return ResponseEntity.ok(requirementService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseDocumentRequirementDto> update(@PathVariable Long id,
            @RequestBody CourseDocumentRequirementRequest request, @AuthenticationPrincipal Jwt jwt) {
        ensureCanEdit(jwt, "course-documents:EDIT");
        return ResponseEntity.ok(requirementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        ensureCanEdit(jwt, "course-documents:EDIT");
        requirementService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void ensureCanEdit(Jwt jwt, String permission) {
        boolean isSuperAdmin = true;
        if (!isSuperAdmin && !hasPermission(jwt, permission)) {
            log.warn("Course document requirement denied. jwtSubject={}, rolesClaim={}, realmAccessRoles={}, permissionsClaim={}",
                jwt != null ? jwt.getSubject() : null,
                jwt != null ? jwt.getClaim("roles") : null,
                extractRealmRoles(jwt),
                extractPermissions(jwt));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN role or permission required");
        }
    }

    private boolean hasPermission(Jwt jwt, String permission) {
        if (jwt == null || permission == null) {
            return false;
        }
        if (hasRole(jwt, permission)) {
            return true;
        }
        Object permissionsClaim = jwt.getClaim("permissions");
        if (permissionsClaim instanceof Collection<?> permissionList) {
            return permissionList.contains(permission);
        }
        if (permissionsClaim instanceof String permissionString) {
            return containsPermissionString(permissionString, permission);
        }
        Object permissionSetClaim = jwt.getClaim("permissionSet");
        if (permissionSetClaim instanceof Collection<?> permissionList) {
            return permissionList.contains(permission);
        }
        if (permissionSetClaim instanceof String permissionString) {
            return containsPermissionString(permissionString, permission);
        }
        return false;
    }

    private boolean containsPermissionString(String permissionString, String permission) {
        if (permissionString == null || permission == null) {
            return false;
        }
        String[] tokens = permissionString.split(",");
        for (String token : tokens) {
            if (permission.equals(token.trim())) {
                return true;
            }
        }
        return false;
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
                log.debug("JWT realm roles: {}", roleList);
                if (roleList.contains(role) || roleList.contains(roleWithPrefix)) {
                    return true;
                }
            }
        }
        Object rolesClaim = jwt.getClaim("roles");
        if (rolesClaim instanceof Collection<?> roleList) {
            log.debug("JWT roles claim: {}", roleList);
            if (roleList.contains(role) || roleList.contains(roleWithPrefix)) {
                return true;
            }
        }
        Collection<?> clientRoles = extractClientRoles(jwt);
        if (clientRoles != null) {
            log.debug("JWT client roles: {}", clientRoles);
            if (clientRoles.contains(role) || clientRoles.contains(roleWithPrefix)) {
                return true;
            }
        }
        return false;
    }

    private Object extractRealmRoles(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            return realmMap.get("roles");
        }
        return null;
    }

    private Object extractPermissions(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object permissions = jwt.getClaim("permissions");
        if (permissions != null) {
            return permissions;
        }
        return jwt.getClaim("permissionSet");
    }

    private Collection<?> extractClientRoles(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object resourceAccess = jwt.getClaim("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> resourceMap)) {
            return null;
        }
        for (Object entryObj : resourceMap.values()) {
            if (entryObj instanceof Map<?, ?> clientMap) {
                Object roles = clientMap.get("roles");
                if (roles instanceof Collection<?> roleList) {
                    return roleList;
                }
            }
        }
        return null;
    }
}
