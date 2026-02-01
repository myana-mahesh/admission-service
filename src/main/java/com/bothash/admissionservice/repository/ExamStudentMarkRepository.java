package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.ExamStudentMark;

public interface ExamStudentMarkRepository extends JpaRepository<ExamStudentMark, Long> {
    List<ExamStudentMark> findByAssignmentAssignmentIdIn(List<Long> assignmentIds);
    List<ExamStudentMark> findByAssignmentAssignmentId(Long assignmentId);
}
