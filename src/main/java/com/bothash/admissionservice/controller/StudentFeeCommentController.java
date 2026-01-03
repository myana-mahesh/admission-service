package com.bothash.admissionservice.controller;

import java.util.List;

import org.springframework.data.domain.Page;
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

import com.bothash.admissionservice.dto.CreateStudentFeeCommentRequest;
import com.bothash.admissionservice.dto.StudentFeeCommentDto;
import com.bothash.admissionservice.service.StudentFeeCommentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fees/comments")
@RequiredArgsConstructor
public class StudentFeeCommentController {

    private final StudentFeeCommentService commentService;

    @PostMapping
    public ResponseEntity<StudentFeeCommentDto> createComment(@RequestBody CreateStudentFeeCommentRequest request) {
        try {
            StudentFeeCommentDto created = commentService.createComment(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<StudentFeeCommentDto>> getCommentsByStudentId(@PathVariable Long studentId) {
        List<StudentFeeCommentDto> comments = commentService.getCommentsByStudentId(studentId);
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/student/{studentId}/paginated")
    public ResponseEntity<Page<StudentFeeCommentDto>> getCommentsByStudentIdPaginated(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<StudentFeeCommentDto> comments = commentService.getCommentsByStudentId(studentId, page, size);
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/student/{studentId}/count")
    public ResponseEntity<Long> getCommentCount(@PathVariable Long studentId) {
        Long count = commentService.getCommentCountByStudentId(studentId);
        return ResponseEntity.ok(count);
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<StudentFeeCommentDto> updateComment(
            @PathVariable Long commentId,
            @RequestBody String newComment) {
        try {
            StudentFeeCommentDto updated = commentService.updateComment(commentId, newComment);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}
