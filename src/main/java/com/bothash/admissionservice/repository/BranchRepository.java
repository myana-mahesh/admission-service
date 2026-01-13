package com.bothash.admissionservice.repository;

import com.bothash.admissionservice.entity.BranchMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<BranchMaster, Long> {
    Optional<BranchMaster> findByCodeIgnoreCase(String code);
    Optional<BranchMaster> findByNameIgnoreCase(String name);
}
