package com.bothash.admissionservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.AdmissionSignoff;

@Repository
public interface AdmissionSignoffRepository extends JpaRepository<AdmissionSignoff, Long> {
	  Optional<AdmissionSignoff> findByAdmissionAdmissionId(Long admissionId);

}
