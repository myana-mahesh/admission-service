package com.bothash.admissionservice.repository;

import java.util.List;
import java.util.Optional;

import com.bothash.admissionservice.enumpackage.Gender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.bothash.admissionservice.entity.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
	  Optional<Student> findByAbsId(String absId);
	  Optional<Student> findByAadhaar(String aadhaar);
	  Student findByMobile(String mobile);
	  @Query("select s from Student s where lower(s.fullName) like lower(concat('%', :q, '%'))")
	  List<Student> searchByName(String q);
	Student findByAbsIdOrMobile(String absId, String mobile);
	Optional<Student> findByRegistrationNumber(String registrationNumber);
	
	// 🔍 Pagination + simple search (optional)
    @Query("""
           select s from Student s
           where (:q is null or :q = '' 
                  or lower(s.fullName) like lower(concat('%', :q, '%'))
                  or lower(s.absId)   like lower(concat('%', :q, '%'))
                  or s.mobile         like concat('%', :q, '%'))
           """)
    Page<Student> search(@Param("q") String q, Pageable pageable);


/*	@Query("""
    SELECT s FROM Student s
    LEFT JOIN s.course c
    WHERE
        (:q IS NULL OR
            LOWER(s.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
        )
    AND (:courseId IS NULL OR c.id = :courseId)
    AND (:batch IS NULL OR s.batch = :batch)
    AND (:academicYear IS NULL OR s.academicYear = :academicYear)
    AND (:gender IS NULL OR s.gender = :gender)
""")
	Page<Student> findWithFilters(
			@Param("q") String q,
			@Param("courseId") Long courseId,
			@Param("batch") String batch,
			@Param("academicYear") Integer academicYear,
			@Param("gender") Gender gender,
			Pageable pageable
	);*/

	@Query("""
    SELECT s FROM Student s
    WHERE
        (:q IS NULL OR
            LOWER(s.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR s.mobile LIKE CONCAT('%', :q, '%')
            OR s.email LIKE CONCAT('%', :q, '%')
            OR s.absId LIKE CONCAT('%', :q, '%')
        )
    AND (:batch IS NULL OR s.batch = :batch)
    AND (:gender IS NULL OR s.gender = :gender)
""")
	Page<Student> findWithFilters(
			@Param("q") String q,
			@Param("batch") String batch,
			@Param("gender") Gender gender,
			Pageable pageable
	);

	@Query("""
    SELECT s FROM Student s
    WHERE
        (:q IS NULL OR
            LOWER(s.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR s.mobile LIKE CONCAT('%', :q, '%')
            OR s.email LIKE CONCAT('%', :q, '%')
            OR s.absId LIKE CONCAT('%', :q, '%')
        )
    AND (:batch IS NULL OR s.batch = :batch)
    AND (:gender IS NULL OR s.gender = :gender)
    AND (:studentIds IS NULL OR s.studentId IN :studentIds)
""")
	Page<Student> findWithFiltersAndIds(
			@Param("q") String q,
			@Param("batch") String batch,
			@Param("gender") Gender gender,
			@Param("studentIds") List<Long> studentIds,
			Pageable pageable
	);


}
