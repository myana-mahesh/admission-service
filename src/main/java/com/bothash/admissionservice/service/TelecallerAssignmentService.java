package com.bothash.admissionservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.dto.TelecallerAssignmentDto;
import com.bothash.admissionservice.dto.TelecallerAssignmentRequest;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.TelecallerAssignment;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.TelecallerAssignmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TelecallerAssignmentService {

    private static final Set<String> VALID_OPS = Set.of("LT", "LTE", "EQ", "GTE", "GT");

    private final TelecallerAssignmentRepository repository;
    private final CourseRepository courseRepository;

    @Transactional
    public TelecallerAssignmentDto create(TelecallerAssignmentRequest request, String createdBy) {
        validate(request);
        String actor = StringUtils.hasText(createdBy) ? createdBy : "unknown";
        TelecallerAssignment entity = TelecallerAssignment.builder()
                .telecallerUserId(request.getTelecallerUserId().trim())
                .paidAmountOp(normalizeOp(request.getPaidAmountOp()))
                .paidAmountValue(request.getPaidAmountValue())
                .batchCode(blankToNull(request.getBatchCode()))
                .courseId(request.getCourseId())
                .active(Boolean.TRUE)
                .createdBy(actor)
                .createdAt(LocalDateTime.now())
                .build();
        TelecallerAssignment saved = repository.save(entity);
        return toDto(saved, resolveCourseNames(List.of(saved)));
    }

    @Transactional
    public void deactivate(Long id, String actor) {
        TelecallerAssignment entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + id));
        if (Boolean.FALSE.equals(entity.getActive())) {
            return;
        }
        entity.setActive(Boolean.FALSE);
        entity.setDeactivatedBy(StringUtils.hasText(actor) ? actor : "unknown");
        entity.setDeactivatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<TelecallerAssignmentDto> listAll(Boolean activeOnly) {
        List<TelecallerAssignment> rows = Boolean.TRUE.equals(activeOnly)
                ? repository.findByActiveTrueOrderByCreatedAtDesc()
                : repository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> courseNames = resolveCourseNames(rows);
        return rows.stream().map(row -> toDto(row, courseNames)).toList();
    }

    @Transactional(readOnly = true)
    public List<TelecallerAssignmentDto> listActiveForTelecaller(String telecallerUserId) {
        if (!StringUtils.hasText(telecallerUserId)) {
            return List.of();
        }
        List<TelecallerAssignment> rows = repository.findByTelecallerUserIdAndActiveTrue(telecallerUserId.trim());
        Map<Long, String> courseNames = resolveCourseNames(rows);
        return rows.stream().map(row -> toDto(row, courseNames)).toList();
    }

    /**
     * Raw entities for use inside the FeeLedgerService predicate builder — bypasses
     * DTO conversion so the CriteriaBuilder can read fields directly.
     */
    @Transactional(readOnly = true)
    public List<TelecallerAssignment> findActiveEntitiesForTelecaller(String telecallerUserId) {
        if (!StringUtils.hasText(telecallerUserId)) {
            return List.of();
        }
        return repository.findByTelecallerUserIdAndActiveTrue(telecallerUserId.trim());
    }

    private void validate(TelecallerAssignmentRequest request) {
        if (request == null || !StringUtils.hasText(request.getTelecallerUserId())) {
            throw new IllegalArgumentException("telecallerUserId is required.");
        }
        boolean hasOp = StringUtils.hasText(request.getPaidAmountOp());
        boolean hasValue = request.getPaidAmountValue() != null;
        if (hasOp != hasValue) {
            throw new IllegalArgumentException("paidAmountOp and paidAmountValue must be provided together.");
        }
        if (hasOp && !VALID_OPS.contains(request.getPaidAmountOp().trim().toUpperCase())) {
            throw new IllegalArgumentException("Invalid paidAmountOp. Allowed: " + VALID_OPS);
        }
        if (hasValue && request.getPaidAmountValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("paidAmountValue cannot be negative.");
        }
        boolean anyCriterion = hasOp
                || StringUtils.hasText(request.getBatchCode())
                || request.getCourseId() != null;
        if (!anyCriterion) {
            throw new IllegalArgumentException("At least one of paidAmount, batch, or course must be set.");
        }
    }

    private String normalizeOp(String op) {
        return StringUtils.hasText(op) ? op.trim().toUpperCase() : null;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Map<Long, String> resolveCourseNames(Collection<TelecallerAssignment> rows) {
        Set<Long> courseIds = rows.stream()
                .map(TelecallerAssignment::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courseRepository.findAllById(courseIds).stream()
                .filter(c -> c != null && c.getCourseId() != null)
                .collect(Collectors.toMap(Course::getCourseId, c -> c.getName() == null ? "" : c.getName()));
    }

    private TelecallerAssignmentDto toDto(TelecallerAssignment entity, Map<Long, String> courseNames) {
        return TelecallerAssignmentDto.builder()
                .id(entity.getId())
                .telecallerUserId(entity.getTelecallerUserId())
                .paidAmountOp(entity.getPaidAmountOp())
                .paidAmountValue(entity.getPaidAmountValue())
                .batchCode(entity.getBatchCode())
                .courseId(entity.getCourseId())
                .courseName(entity.getCourseId() != null ? courseNames.get(entity.getCourseId()) : null)
                .active(entity.getActive())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .deactivatedBy(entity.getDeactivatedBy())
                .deactivatedAt(entity.getDeactivatedAt())
                .build();
    }
}
