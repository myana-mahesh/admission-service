package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.StudentFeeComment;

@Repository
public interface StudentFeeCommentRepository extends JpaRepository<StudentFeeComment, Long> {

    @Query("SELECT c FROM StudentFeeComment c WHERE c.student.studentId = :studentId ORDER BY c.createdAt DESC")
    List<StudentFeeComment> findByStudentIdOrderByCreatedAtDesc(@Param("studentId") Long studentId);

    @Query("SELECT c FROM StudentFeeComment c WHERE c.student.studentId = :studentId ORDER BY c.createdAt DESC")
    Page<StudentFeeComment> findByStudentIdOrderByCreatedAtDesc(@Param("studentId") Long studentId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM StudentFeeComment c WHERE c.student.studentId = :studentId")
    Long countByStudentId(@Param("studentId") Long studentId);
}
