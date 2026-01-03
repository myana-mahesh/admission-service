package com.bothash.admissionservice.controller;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
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

import com.bothash.admissionservice.entity.DiscountReasonMaster;
import com.bothash.admissionservice.repository.DiscountReasonMasterRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/discount-reasons")
@RequiredArgsConstructor
@Slf4j
public class DiscountReasonController {

    private final DiscountReasonMasterRepository discountReasonRepository;

    @GetMapping
    public List<DiscountReasonMaster> list() {
        return discountReasonRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @PostMapping
    public ResponseEntity<DiscountReasonMaster> create(@RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        ensureCanEdit(jwt, "discount-reasons:EDIT");
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (discountReasonRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount reason already exists");
        }
        DiscountReasonMaster master = new DiscountReasonMaster();
        master.setName(name);
        return ResponseEntity.ok(discountReasonRepository.save(master));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiscountReasonMaster> update(@PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        ensureCanEdit(jwt, "discount-reasons:EDIT");
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        DiscountReasonMaster master = discountReasonRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount reason not found"));
        if (!master.getName().equalsIgnoreCase(name) && discountReasonRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discount reason already exists");
        }
        master.setName(name);
        return ResponseEntity.ok(discountReasonRepository.save(master));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        ensureCanEdit(jwt, "discount-reasons:EDIT");
        if (!discountReasonRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Discount reason not found");
        }
        try {
            discountReasonRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot delete this discount reason because admissions already exist for it.");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to delete the discount reason.");
        }
    }

    private void ensureCanEdit(Jwt jwt, String permission) {
        boolean isSuperAdmin = true;
        if (!isSuperAdmin && !hasPermission(jwt, permission)) {
            log.warn("Discount reason denied. jwtSubject={}, rolesClaim={}, realmAccessRoles={}, permissionsClaim={}",
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
        Collection<?> clientRoles = extractClientRoles(jwt);
        if (clientRoles != null) {
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
