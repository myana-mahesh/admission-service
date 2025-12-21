package com.bothash.admissionservice.repository;

import com.bothash.admissionservice.entity.BranchMaster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<BranchMaster, Long> {
}
