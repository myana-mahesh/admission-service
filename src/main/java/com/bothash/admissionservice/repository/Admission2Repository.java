package com.bothash.admissionservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	Admission2 findFirstByStudent_MobileAndCourse_CourseIdOrderByCreatedAtDesc(String mobile, Long courseId);
	Admission2 findFirstByStudentStudentIdOrderByUpdatedAtDesc(Long studentId);

	List<Admission2> findByCourseCourseIdAndLectureBranchIdInAndBatchIn(Long courseId, List<Long> lectureBranchIds,
	                                                                    List<String> batchCodes);

	Page<Admission2> findByCourseCourseIdAndLectureBranchIdInAndBatchIn(Long courseId, List<Long> lectureBranchIds,
	                                                                    List<String> batchCodes, Pageable pageable);

	@Query("""
	    select a
	    from Admission2 a
	    join a.student s
	    left join a.lectureBranch lb
	    where a.course.courseId = :courseId
	      and lb.id in :lectureBranchIds
	      and a.batch in :batchCodes
	      and (:collegeId is null or a.college.collegeId = :collegeId)
	      and (
	        :query is null or :query = '' or
	        lower(s.fullName) like lower(concat('%', :query, '%')) or
	        lower(s.mobile) like lower(concat('%', :query, '%')) or
	        lower(s.email) like lower(concat('%', :query, '%'))
	      )
	    """)
	Page<Admission2> searchExamAdmissions(@Param("courseId") Long courseId,
	                                     @Param("lectureBranchIds") List<Long> lectureBranchIds,
	                                     @Param("batchCodes") List<String> batchCodes,
	                                     @Param("collegeId") Long collegeId,
	                                     @Param("query") String query,
	                                     Pageable pageable);

	@EntityGraph(attributePaths = {"student", "college", "course"})
	@Query(value = """
	    select distinct a
	    from Admission2 a
	    join a.student s
	    left join a.lectureBranch lb
	    left join ExamAssignment ea on ea.admission = a and ea.exam.examId = :examId
	    left join ExamStudentMark esm on esm.assignment = ea
	    where a.course.courseId = :courseId
	      and lb.id in :lectureBranchIds
	      and a.batch in :batchCodes
	      and (:collegeId is null or a.college.collegeId = :collegeId)
	      and (:status is null or :status = '' or upper(coalesce(ea.examStatus, 'MARKS_NOT_ENTERED')) = upper(:status))
	      and (:absentOnly is null or :absentOnly = false or esm.absent = true)
	      and (
	        :query is null or :query = '' or
	        lower(s.fullName) like lower(concat('%', :query, '%')) or
	        lower(s.mobile) like lower(concat('%', :query, '%')) or
	        lower(s.email) like lower(concat('%', :query, '%'))
	      )
	    """,
	    countQuery = """
	    select count(distinct a.admissionId)
	    from Admission2 a
	    join a.student s
	    left join a.lectureBranch lb
	    left join ExamAssignment ea on ea.admission = a and ea.exam.examId = :examId
	    left join ExamStudentMark esm on esm.assignment = ea
	    where a.course.courseId = :courseId
	      and lb.id in :lectureBranchIds
	      and a.batch in :batchCodes
	      and (:collegeId is null or a.college.collegeId = :collegeId)
	      and (:status is null or :status = '' or upper(coalesce(ea.examStatus, 'MARKS_NOT_ENTERED')) = upper(:status))
	      and (:absentOnly is null or :absentOnly = false or esm.absent = true)
	      and (
	        :query is null or :query = '' or
	        lower(s.fullName) like lower(concat('%', :query, '%')) or
	        lower(s.mobile) like lower(concat('%', :query, '%')) or
	        lower(s.email) like lower(concat('%', :query, '%'))
	      )
	    """)
	Page<Admission2> searchExamAdmissionsWithFilters(@Param("examId") Long examId,
	                                               @Param("courseId") Long courseId,
	                                               @Param("lectureBranchIds") List<Long> lectureBranchIds,
	                                               @Param("batchCodes") List<String> batchCodes,
	                                               @Param("collegeId") Long collegeId,
	                                               @Param("query") String query,
	                                               @Param("status") String status,
	                                               @Param("absentOnly") Boolean absentOnly,
	                                               Pageable pageable);

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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Admission2 a set a.college = null where a.college.collegeId = :collegeId")
	int clearCollegeReferences(@Param("collegeId") Long collegeId);

}
