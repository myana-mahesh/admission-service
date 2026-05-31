package com.bothash.admissionservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.YearlyFees;


public interface YearlyFeesRepository extends JpaRepository<YearlyFees, Long>{

	Optional<YearlyFees> findFirstByAdmissionAdmissionIdAndYearOrderByIdDesc(Long admissionId, int studyYear);
	List<YearlyFees> findByAdmissionAdmissionIdAndYear(Long admissionId, int studyYear);

}
