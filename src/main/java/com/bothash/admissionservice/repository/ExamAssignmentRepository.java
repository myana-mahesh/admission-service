package com.bothash.admissionservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.ExamAssignment;

public interface ExamAssignmentRepository extends JpaRepository<ExamAssignment, Long> {
    boolean existsByExamExamIdAndAdmissionAdmissionId(Long examId, Long admissionId);
    Optional<ExamAssignment> findByExamExamIdAndAdmissionAdmissionId(Long examId, Long admissionId);
    List<ExamAssignment> findByExamExamIdAndAdmissionAdmissionIdIn(Long examId, List<Long> admissionIds);
    List<ExamAssignment> findByExamExamId(Long examId);
}
