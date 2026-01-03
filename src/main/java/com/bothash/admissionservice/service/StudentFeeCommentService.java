package com.bothash.admissionservice.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.dto.CreateStudentFeeCommentRequest;
import com.bothash.admissionservice.dto.StudentFeeCommentDto;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.entity.StudentFeeComment;
import com.bothash.admissionservice.repository.StudentFeeCommentRepository;
import com.bothash.admissionservice.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentFeeCommentService {

    private final StudentFeeCommentRepository commentRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public StudentFeeCommentDto createComment(CreateStudentFeeCommentRequest request) {
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException("Student ID is required");
        }
        if (!StringUtils.hasText(request.getComment())) {
            throw new IllegalArgumentException("Comment cannot be empty");
        }
        if (!StringUtils.hasText(request.getCommentedBy())) {
            throw new IllegalArgumentException("Commented by is required");
        }

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + request.getStudentId()));

        StudentFeeComment comment = StudentFeeComment.builder()
                .student(student)
                .comment(request.getComment().trim())
                .commentedBy(request.getCommentedBy().trim())
                .commentType(StringUtils.hasText(request.getCommentType()) ? request.getCommentType() : "GENERAL")
                .build();

        StudentFeeComment saved = commentRepository.save(comment);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<StudentFeeCommentDto> getCommentsByStudentId(Long studentId) {
        return commentRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<StudentFeeCommentDto> getCommentsByStudentId(Long studentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return commentRepository.findByStudentIdOrderByCreatedAtDesc(studentId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Long getCommentCountByStudentId(Long studentId) {
        return commentRepository.countByStudentId(studentId);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        commentRepository.deleteById(commentId);
    }

    @Transactional
    public StudentFeeCommentDto updateComment(Long commentId, String newComment) {
        StudentFeeComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found with ID: " + commentId));

        if (!StringUtils.hasText(newComment)) {
            throw new IllegalArgumentException("Comment cannot be empty");
        }

        comment.setComment(newComment.trim());
        StudentFeeComment updated = commentRepository.save(comment);
        return toDto(updated);
    }

    private StudentFeeCommentDto toDto(StudentFeeComment comment) {
        return StudentFeeCommentDto.builder()
                .commentId(comment.getCommentId())
                .studentId(comment.getStudent() != null ? comment.getStudent().getStudentId() : null)
                .studentName(comment.getStudent() != null ? comment.getStudent().getFullName() : null)
                .comment(comment.getComment())
                .commentedBy(comment.getCommentedBy())
                .commentType(comment.getCommentType())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
