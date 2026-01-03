package com.bothash.admissionservice.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.dto.CreateStudentFeeScheduleRequest;
import com.bothash.admissionservice.dto.StudentFeeScheduleDto;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.entity.StudentFeeSchedule;
import com.bothash.admissionservice.repository.StudentFeeScheduleRepository;
import com.bothash.admissionservice.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentFeeScheduleService {

    private final StudentFeeScheduleRepository scheduleRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public StudentFeeScheduleDto createSchedule(CreateStudentFeeScheduleRequest request) {
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException("Student ID is required");
        }
        if (request.getScheduledDate() == null) {
            throw new IllegalArgumentException("Scheduled date is required");
        }
        if (!StringUtils.hasText(request.getCreatedByUser())) {
            throw new IllegalArgumentException("Created by user is required");
        }

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + request.getStudentId()));

        String scheduleType = StringUtils.hasText(request.getScheduleType())
                ? request.getScheduleType()
                : "REMINDER";

        StudentFeeSchedule schedule = StudentFeeSchedule.builder()
                .student(student)
                .scheduledDate(request.getScheduledDate())
                .expectedAmount(request.getExpectedAmount())
                .scheduleType(scheduleType)
                .status("PENDING")
                .notes(request.getNotes())
                .createdByUser(request.getCreatedByUser().trim())
                .build();

        StudentFeeSchedule saved = scheduleRepository.save(schedule);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<StudentFeeScheduleDto> getSchedulesByStudentId(Long studentId) {
        return scheduleRepository.findByStudentIdOrderByScheduledDateDesc(studentId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudentFeeScheduleDto> getPendingSchedulesByStudentId(Long studentId) {
        return scheduleRepository.findByStudentIdAndStatus(studentId, "PENDING")
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudentFeeScheduleDto> getSchedulesForDate(LocalDate date) {
        return scheduleRepository.findPendingSchedulesForDate(date)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudentFeeScheduleDto> getSchedulesBetweenDates(LocalDate startDate, LocalDate endDate) {
        return scheduleRepository.findPendingSchedulesBetweenDates(startDate, endDate)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudentFeeScheduleDto> getOverdueSchedules() {
        return scheduleRepository.findOverdueSchedules(LocalDate.now())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Long countPendingByStudentId(Long studentId) {
        return scheduleRepository.countPendingByStudentId(studentId);
    }

    @Transactional
    public StudentFeeScheduleDto updateScheduleStatus(Long scheduleId, String status, String completedBy) {
        StudentFeeSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found with ID: " + scheduleId));

        schedule.setStatus(status);
        if ("COMPLETED".equals(status)) {
            schedule.setCompletedBy(completedBy);
            schedule.setCompletedDate(LocalDate.now());
        }

        StudentFeeSchedule updated = scheduleRepository.save(schedule);
        return toDto(updated);
    }

    @Transactional
    public StudentFeeScheduleDto updateSchedule(Long scheduleId, CreateStudentFeeScheduleRequest request) {
        StudentFeeSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found with ID: " + scheduleId));

        if (request.getScheduledDate() != null) {
            schedule.setScheduledDate(request.getScheduledDate());
        }
        if (request.getExpectedAmount() != null) {
            schedule.setExpectedAmount(request.getExpectedAmount());
        }
        if (StringUtils.hasText(request.getScheduleType())) {
            schedule.setScheduleType(request.getScheduleType());
        }
        if (request.getNotes() != null) {
            schedule.setNotes(request.getNotes());
        }

        StudentFeeSchedule updated = scheduleRepository.save(schedule);
        return toDto(updated);
    }

    @Transactional
    public void deleteSchedule(Long scheduleId) {
        scheduleRepository.deleteById(scheduleId);
    }

    @Transactional
    public void autoUpdateOverdueSchedules() {
        LocalDate today = LocalDate.now();
        List<StudentFeeSchedule> overdueSchedules = scheduleRepository.findOverdueSchedules(today);

        for (StudentFeeSchedule schedule : overdueSchedules) {
            schedule.setStatus("OVERDUE");
        }

        scheduleRepository.saveAll(overdueSchedules);
    }

    private StudentFeeScheduleDto toDto(StudentFeeSchedule schedule) {
        return StudentFeeScheduleDto.builder()
                .scheduleId(schedule.getScheduleId())
                .studentId(schedule.getStudent() != null ? schedule.getStudent().getStudentId() : null)
                .studentName(schedule.getStudent() != null ? schedule.getStudent().getFullName() : null)
                .scheduledDate(schedule.getScheduledDate())
                .expectedAmount(schedule.getExpectedAmount())
                .scheduleType(schedule.getScheduleType())
                .status(schedule.getStatus())
                .notes(schedule.getNotes())
                .createdByUser(schedule.getCreatedByUser())
                .completedBy(schedule.getCompletedBy())
                .completedDate(schedule.getCompletedDate())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
