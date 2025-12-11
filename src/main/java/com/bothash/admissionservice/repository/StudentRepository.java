package com.bothash.admissionservice.repository;

import java.util.List;
import java.util.Optional;

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
	
	// 🔍 Pagination + simple search (optional)
    @Query("""
           select s from Student s
           where (:q is null or :q = '' 
                  or lower(s.fullName) like lower(concat('%', :q, '%'))
                  or lower(s.absId)   like lower(concat('%', :q, '%'))
                  or s.mobile         like concat('%', :q, '%'))
           """)
    Page<Student> search(@Param("q") String q, Pageable pageable);
	}
