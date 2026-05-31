package com.bothash.admissionservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.BranchCashbookDay;

public interface BranchCashbookDayRepository extends JpaRepository<BranchCashbookDay, Long> {
    Optional<BranchCashbookDay> findByBranch_IdAndBusinessDate(Long branchId, LocalDate businessDate);
    Optional<BranchCashbookDay> findFirstByBranch_IdAndBusinessDateLessThanOrderByBusinessDateDesc(Long branchId, LocalDate businessDate);
    Optional<BranchCashbookDay> findFirstByBranch_IdAndBusinessDateLessThanEqualAndSentToHoAmountGreaterThanOrderByBusinessDateDesc(
            Long branchId, LocalDate businessDate, BigDecimal sentToHoAmount);
    List<BranchCashbookDay> findByBranch_IdAndBusinessDateBetweenOrderByBusinessDateAsc(Long branchId, LocalDate fromDate, LocalDate toDate);
    List<BranchCashbookDay> findByBranch_IdAndSentToHoAmountGreaterThanOrderByBusinessDateDesc(Long branchId, BigDecimal sentToHoAmount);
}
