package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.Guardian;

@Repository
public interface GuardianRepository extends JpaRepository<Guardian, Long> {
	  List<Guardian> findByStudentStudentId(Long studentId);
	}
