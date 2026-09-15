package com.bothash.admissionservice.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bothash.admissionservice.dto.AdmissionSectionDto;
import com.bothash.admissionservice.entity.AdmissionSection;
import com.bothash.admissionservice.repository.AdmissionSectionRepository;
import com.bothash.admissionservice.repository.OtherPaymentFieldRepository;

import lombok.RequiredArgsConstructor;

/**
 * Admin-created sections that appear on the admission view/new pages above
 * the built-in "Other Details" area. Fields with a null section_id continue
 * to render inside the fixed "Other Details" section — so this feature is
 * additive and requires no data migration.
 */
@Service
@RequiredArgsConstructor
public class AdmissionSectionService {

    private final AdmissionSectionRepository sectionRepository;
    private final OtherPaymentFieldRepository fieldRepository;

    public List<AdmissionSectionDto> list(boolean includeInactive) {
        List<AdmissionSection> sections = includeInactive
                ? sectionRepository.findAllByOrderBySortOrderAscNameAsc()
                : sectionRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        List<AdmissionSectionDto> out = new ArrayList<>();
        for (AdmissionSection s : sections) out.add(toDto(s));
        return out;
    }

    @Transactional
    public AdmissionSectionDto create(AdmissionSectionDto req) {
        AdmissionSection s = AdmissionSection.builder()
                .name(req.getName() == null ? "" : req.getName().trim())
                .sortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder())
                .active(req.getActive() == null || req.getActive())
                .build();
        return toDto(sectionRepository.save(s));
    }

    @Transactional
    public AdmissionSectionDto update(Long id, AdmissionSectionDto req) {
        AdmissionSection s = sectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));
        if (req.getName() != null) s.setName(req.getName().trim());
        if (req.getSortOrder() != null) s.setSortOrder(req.getSortOrder());
        if (req.getActive() != null) s.setActive(req.getActive());
        return toDto(sectionRepository.save(s));
    }

    /**
     * Soft-delete: mark section inactive and unassign all its fields, so
     * fields don't disappear from admin — they fall back to "Other Details".
     */
    @Transactional
    public void deactivate(Long id) {
        AdmissionSection s = sectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));
        s.setActive(false);
        sectionRepository.save(s);
        fieldRepository.findBySectionId(id).forEach(f -> {
            f.setSectionId(null);
            fieldRepository.save(f);
        });
    }

    private AdmissionSectionDto toDto(AdmissionSection s) {
        return AdmissionSectionDto.builder()
                .id(s.getId())
                .name(s.getName())
                .sortOrder(s.getSortOrder())
                .active(s.isActive())
                .build();
    }
}
