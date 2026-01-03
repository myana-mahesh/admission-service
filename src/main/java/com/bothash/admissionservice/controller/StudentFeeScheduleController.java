package com.bothash.admissionservice.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import com.bothash.admissionservice.dto.CreateStudentFeeScheduleRequest;
import com.bothash.admissionservice.dto.StudentFeeScheduleDto;
import com.bothash.admissionservice.service.StudentFeeScheduleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fees/schedules")
@RequiredArgsConstructor
public class StudentFeeScheduleController {

    private final StudentFeeScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<StudentFeeScheduleDto> createSchedule(@RequestBody CreateStudentFeeScheduleRequest request) {
        try {
            StudentFeeScheduleDto created = scheduleService.createSchedule(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<StudentFeeScheduleDto>> getSchedulesByStudentId(@PathVariable Long studentId) {
        List<StudentFeeScheduleDto> schedules = scheduleService.getSchedulesByStudentId(studentId);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/student/{studentId}/pending")
    public ResponseEntity<List<StudentFeeScheduleDto>> getPendingSchedulesByStudentId(@PathVariable Long studentId) {
        List<StudentFeeScheduleDto> schedules = scheduleService.getPendingSchedulesByStudentId(studentId);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/student/{studentId}/count")
    public ResponseEntity<Long> getPendingCount(@PathVariable Long studentId) {
        Long count = scheduleService.countPendingByStudentId(studentId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<StudentFeeScheduleDto>> getSchedulesForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<StudentFeeScheduleDto> schedules = scheduleService.getSchedulesForDate(date);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/range")
    public ResponseEntity<List<StudentFeeScheduleDto>> getSchedulesBetweenDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<StudentFeeScheduleDto> schedules = scheduleService.getSchedulesBetweenDates(startDate, endDate);
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<StudentFeeScheduleDto>> getOverdueSchedules() {
        List<StudentFeeScheduleDto> schedules = scheduleService.getOverdueSchedules();
        return ResponseEntity.ok(schedules);
    }

    @PutMapping("/{scheduleId}/status")
    public ResponseEntity<StudentFeeScheduleDto> updateScheduleStatus(
            @PathVariable Long scheduleId,
            @RequestParam String status,
            @RequestParam(required = false) String completedBy) {
        try {
            StudentFeeScheduleDto updated = scheduleService.updateScheduleStatus(scheduleId, status, completedBy);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{scheduleId}")
    public ResponseEntity<StudentFeeScheduleDto> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody CreateStudentFeeScheduleRequest request) {
        try {
            StudentFeeScheduleDto updated = scheduleService.updateSchedule(scheduleId, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auto-update-overdue")
    public ResponseEntity<Void> autoUpdateOverdueSchedules() {
        scheduleService.autoUpdateOverdueSchedules();
        return ResponseEntity.ok().build();
    }
}
