package com.bothash.admissionservice.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bothash.admissionservice.dto.OtherPaymentFieldValueRequest;
import com.bothash.admissionservice.dto.OtherPaymentValueEntryDto;
import com.bothash.admissionservice.dto.StudentOtherPaymentValueDto;
import com.bothash.admissionservice.entity.OtherPaymentField;
import com.bothash.admissionservice.entity.OtherPaymentFieldOption;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.entity.StudentOtherPaymentValue;
import com.bothash.admissionservice.repository.OtherPaymentFieldOptionRepository;
import com.bothash.admissionservice.repository.OtherPaymentFieldRepository;
import com.bothash.admissionservice.repository.StudentOtherPaymentValueRepository;
import com.bothash.admissionservice.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentOtherPaymentValueService {

    private final StudentOtherPaymentValueRepository valueRepository;
    private final StudentRepository studentRepository;
    private final OtherPaymentFieldRepository fieldRepository;
    private final OtherPaymentFieldOptionRepository optionRepository;

    public List<StudentOtherPaymentValueDto> listValues(Long studentId) {
        List<StudentOtherPaymentValue> values = valueRepository.findByStudentStudentId(studentId);
        List<StudentOtherPaymentValueDto> result = new ArrayList<>();
        for (StudentOtherPaymentValue value : values) {
            result.add(StudentOtherPaymentValueDto.builder()
                    .fieldId(value.getField().getId())
                    .optionId(value.getOption() != null ? value.getOption().getId() : null)
                    .value(value.getValue())
                    .build());
        }
        return result;
    }

    @Transactional
    public void saveValues(Long studentId, List<OtherPaymentFieldValueRequest> requests) {
        if (requests == null) {
            return;
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        Map<Long, List<OtherPaymentValueEntryDto>> byField = new HashMap<>();
        for (OtherPaymentFieldValueRequest req : requests) {
            if (req == null || req.getFieldId() == null) {
                continue;
            }
            byField.put(req.getFieldId(), req.getEntries() == null ? List.of() : req.getEntries());
        }

        for (Map.Entry<Long, List<OtherPaymentValueEntryDto>> entry : byField.entrySet()) {
            Long fieldId = entry.getKey();
            valueRepository.deleteByStudentStudentIdAndField_Id(studentId, fieldId);

            OtherPaymentField field = fieldRepository.findById(fieldId)
                    .orElse(null);
            if (field == null) {
                continue;
            }

            for (OtherPaymentValueEntryDto valueEntry : entry.getValue()) {
                if (valueEntry == null) {
                    continue;
                }
                String value = valueEntry.getValue();
                OtherPaymentFieldOption option = null;
                if (valueEntry.getOptionId() != null) {
                    option = optionRepository.findById(valueEntry.getOptionId()).orElse(null);
                    if (option != null && (value == null || value.isBlank())) {
                        value = option.getValue();
                    }
                }
                if (value == null || value.isBlank()) {
                    continue;
                }
                StudentOtherPaymentValue saved = StudentOtherPaymentValue.builder()
                        .student(student)
                        .field(field)
                        .option(option)
                        .value(value)
                        .build();
                valueRepository.save(saved);
            }
        }
    }
}
