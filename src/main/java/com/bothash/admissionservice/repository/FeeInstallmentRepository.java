package com.bothash.admissionservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.Student;

@Repository
public interface FeeInstallmentRepository extends JpaRepository<FeeInstallment, Long> {
	  List<FeeInstallment> findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(Long admissionId);
	  Optional<FeeInstallment> findByAdmissionAdmissionIdAndStudyYearAndInstallmentNo(Long admissionId, Integer studyYear, Integer installmentNo);
	Optional<FeeInstallment> findByInstallmentId(Long installmentId);
	List<FeeInstallment> findByAdmission_AdmissionIdAndStudyYearOrderByInstallmentNoAsc(
            Long admissionId, Integer studyYear
    );
}
