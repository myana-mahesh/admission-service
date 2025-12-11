package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.YearlyFees;


public interface YearlyFeesRepository extends JpaRepository<YearlyFees, Long>{

	YearlyFees findByAdmissionAdmissionIdAndYear(Long admissionId, int studyYear);

}
