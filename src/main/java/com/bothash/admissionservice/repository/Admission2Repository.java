package com.bothash.admissionservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.Admission2;

@Repository
public interface Admission2Repository extends JpaRepository<Admission2, Long> {
	  @EntityGraph(attributePaths = {"student","course","year"})
	  Optional<Admission2> findByAdmissionId(Long id);

	  List<Admission2> findByCourseCourseIdAndYearYearId(Long courseId, Long yearId);

	  @Query("select a from Admission2 a where a.examDueDate between :from and :to")
	  List<Admission2> findExamDueBetween(LocalDate from, LocalDate to);

	  Optional<Admission2> findByStudentStudentIdAndYearYearId(Long studentId, Long yearId);

	Admission2 findByStudentStudentIdAndYearYearIdAndCourseCourseId(Long studentId, Long yearId, Long courseId);

}
