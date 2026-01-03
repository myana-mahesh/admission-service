package com.bothash.admissionservice.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bothash.admissionservice.dto.OtherPaymentFieldDto;
import com.bothash.admissionservice.dto.OtherPaymentFieldOptionDto;
import com.bothash.admissionservice.entity.OtherPaymentField;
import com.bothash.admissionservice.entity.OtherPaymentFieldOption;
import com.bothash.admissionservice.repository.OtherPaymentFieldOptionRepository;
import com.bothash.admissionservice.repository.OtherPaymentFieldRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtherPaymentFieldService {

    private final OtherPaymentFieldRepository fieldRepository;
    private final OtherPaymentFieldOptionRepository optionRepository;

    public List<OtherPaymentFieldDto> listFields(boolean includeInactive) {
        List<OtherPaymentField> fields = includeInactive
                ? fieldRepository.findAllByOrderBySortOrderAscLabelAsc()
                : fieldRepository.findByActiveTrueOrderBySortOrderAscLabelAsc();

        List<OtherPaymentFieldDto> result = new ArrayList<>();
        for (OtherPaymentField field : fields) {
            result.add(toDto(field, includeInactive));
        }
        return result;
    }

    @Transactional
    public OtherPaymentFieldDto createField(OtherPaymentFieldDto req) {
        OtherPaymentField field = OtherPaymentField.builder()
                .label(req.getLabel())
                .inputType(req.getInputType())
                .required(Boolean.TRUE.equals(req.getRequired()))
                .sortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder())
                .active(req.getActive() == null || req.getActive())
                .build();
        OtherPaymentField saved = fieldRepository.save(field);
        syncOptions(saved, req.getOptions());
        return toDto(saved, true);
    }

    @Transactional
    public OtherPaymentFieldDto updateField(Long id, OtherPaymentFieldDto req) {
        OtherPaymentField field = fieldRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Field not found"));
        field.setLabel(req.getLabel());
        field.setInputType(req.getInputType());
        field.setRequired(Boolean.TRUE.equals(req.getRequired()));
        field.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        if (req.getActive() != null) {
            field.setActive(req.getActive());
        }
        OtherPaymentField saved = fieldRepository.save(field);
        syncOptions(saved, req.getOptions());
        return toDto(saved, true);
    }

    @Transactional
    public void deactivateField(Long id) {
        OtherPaymentField field = fieldRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Field not found"));
        field.setActive(false);
        fieldRepository.save(field);
    }

    private OtherPaymentFieldDto toDto(OtherPaymentField field, boolean includeInactive) {
        List<OtherPaymentFieldOption> options = includeInactive
                ? optionRepository.findByField_IdOrderBySortOrderAscLabelAsc(field.getId())
                : optionRepository.findByField_IdAndActiveTrueOrderBySortOrderAscLabelAsc(field.getId());

        List<OtherPaymentFieldOptionDto> optionDtos = new ArrayList<>();
        for (OtherPaymentFieldOption option : options) {
            optionDtos.add(OtherPaymentFieldOptionDto.builder()
                    .id(option.getId())
                    .label(option.getLabel())
                    .value(option.getValue())
                    .sortOrder(option.getSortOrder())
                    .active(option.isActive())
                    .build());
        }

        return OtherPaymentFieldDto.builder()
                .id(field.getId())
                .label(field.getLabel())
                .inputType(field.getInputType())
                .required(field.isRequired())
                .sortOrder(field.getSortOrder())
                .active(field.isActive())
                .options(optionDtos)
                .build();
    }

    private void syncOptions(OtherPaymentField field, List<OtherPaymentFieldOptionDto> reqOptions) {
        if (reqOptions == null) {
            return;
        }

        List<OtherPaymentFieldOption> existing = optionRepository.findByField_IdOrderBySortOrderAscLabelAsc(field.getId());
        Map<Long, OtherPaymentFieldOption> existingById = new HashMap<>();
        for (OtherPaymentFieldOption option : existing) {
            existingById.put(option.getId(), option);
        }

        List<Long> seenIds = new ArrayList<>();
        for (OtherPaymentFieldOptionDto req : reqOptions) {
            if (req.getId() != null && existingById.containsKey(req.getId())) {
                OtherPaymentFieldOption option = existingById.get(req.getId());
                option.setLabel(req.getLabel());
                option.setValue(req.getValue() == null ? req.getLabel() : req.getValue());
                option.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
                if (req.getActive() != null) {
                    option.setActive(req.getActive());
                } else {
                    option.setActive(true);
                }
                optionRepository.save(option);
                seenIds.add(option.getId());
            } else {
                OtherPaymentFieldOption created = OtherPaymentFieldOption.builder()
                        .field(field)
                        .label(req.getLabel())
                        .value(req.getValue() == null ? req.getLabel() : req.getValue())
                        .sortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder())
                        .active(req.getActive() == null || req.getActive())
                        .build();
                optionRepository.save(created);
                if (created.getId() != null) {
                    seenIds.add(created.getId());
                }
            }
        }

        for (OtherPaymentFieldOption option : existing) {
            if (option.getId() != null && !seenIds.contains(option.getId())) {
                option.setActive(false);
                optionRepository.save(option);
            }
        }
    }
}
