package com.bothash.admissionservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.enumpackage.AdmissionStatus;

@Repository
public interface Admission2Repository extends JpaRepository<Admission2, Long> {
	  @EntityGraph(attributePaths = {"student","course","year"})
	  Optional<Admission2> findByAdmissionId(Long id);

	  List<Admission2> findByCourseCourseIdAndYearYearId(Long courseId, Long yearId);

	  @Query("select a from Admission2 a where a.examDueDate between :from and :to")
	  List<Admission2> findExamDueBetween(LocalDate from, LocalDate to);

	Optional<Admission2> findByStudentStudentIdAndYearYearId(Long studentId, Long yearId);

	Admission2 findByStudentStudentIdAndYearYearIdAndCourseCourseId(Long studentId, Long yearId, Long courseId);

	Admission2 findFirstByStudent_MobileOrderByCreatedAtDesc(String mobile);
	Admission2 findFirstByStudentStudentIdOrderByUpdatedAtDesc(Long studentId);

	long countByCollegeCollegeIdAndCourseCourseIdAndStatus(Long collegeId, Long courseId, AdmissionStatus status);

	@Query("""
	    select distinct a.student.studentId
	    from Admission2 a
	    where (:collegeId is null or a.college.collegeId = :collegeId)
	      and (:courseId is null or a.course.courseId = :courseId)
	      and (:yearId is null or a.year.yearId = :yearId)
	    """)
	List<Long> findStudentIdsByFilters(@Param("collegeId") Long collegeId,
	                                   @Param("courseId") Long courseId,
	                                   @Param("yearId") Long yearId);

}
