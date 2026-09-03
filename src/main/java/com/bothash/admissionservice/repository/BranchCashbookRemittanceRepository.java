package com.bothash.admissionservice.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.BranchCashbookRemittance;
import com.bothash.admissionservice.enumpackage.RemittanceSource;

public interface BranchCashbookRemittanceRepository extends JpaRepository<BranchCashbookRemittance, Long> {
    List<BranchCashbookRemittance> findByBranch_IdOrderBySentAtDescIdDesc(Long branchId);

    Page<BranchCashbookRemittance> findByBranch_IdOrderBySentAtDescIdDesc(Long branchId, Pageable pageable);

    Optional<BranchCashbookRemittance> findFirstByBranch_IdAndSentAtLessThanOrderBySentAtDescIdDesc(
            Long branchId, OffsetDateTime sentAt);

    List<BranchCashbookRemittance> findByBranch_IdAndStatusAndSentAtAfterOrderBySentAtDesc(
            Long branchId, String status, OffsetDateTime since);

    List<BranchCashbookRemittance> findByBranch_IdAndSourceAndBusinessDateLessThanEqual(
            Long branchId, RemittanceSource source, LocalDate businessDate);
}
