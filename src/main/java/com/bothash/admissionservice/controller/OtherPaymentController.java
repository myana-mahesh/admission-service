package com.bothash.admissionservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.OtherPaymentFieldDto;
import com.bothash.admissionservice.dto.OtherPaymentFieldValueRequest;
import com.bothash.admissionservice.dto.StudentOtherPaymentValueDto;
import com.bothash.admissionservice.service.OtherPaymentFieldService;
import com.bothash.admissionservice.service.StudentOtherPaymentValueService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/other-payments")
@RequiredArgsConstructor
public class OtherPaymentController {

    private final OtherPaymentFieldService fieldService;
    private final StudentOtherPaymentValueService valueService;

    @GetMapping("/fields")
    public List<OtherPaymentFieldDto> listFields(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return fieldService.listFields(includeInactive);
    }

    @PostMapping("/fields")
    public OtherPaymentFieldDto createField(@RequestBody OtherPaymentFieldDto req) {
        return fieldService.createField(req);
    }

    @PutMapping("/fields/{id}")
    public OtherPaymentFieldDto updateField(@PathVariable Long id, @RequestBody OtherPaymentFieldDto req) {
        return fieldService.updateField(id, req);
    }

    @DeleteMapping("/fields/{id}")
    public ResponseEntity<Void> deleteField(@PathVariable Long id) {
        fieldService.deactivateField(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/{studentId}")
    public List<StudentOtherPaymentValueDto> listStudentValues(@PathVariable Long studentId) {
        return valueService.listValues(studentId);
    }

    @PutMapping("/students/{studentId}")
    public ResponseEntity<Void> saveStudentValues(
            @PathVariable Long studentId,
            @RequestBody List<OtherPaymentFieldValueRequest> values) {
        valueService.saveValues(studentId, values);
        return ResponseEntity.noContent().build();
    }
}
