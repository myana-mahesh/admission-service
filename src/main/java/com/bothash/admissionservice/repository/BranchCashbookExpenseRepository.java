package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.BranchCashbookExpense;

public interface BranchCashbookExpenseRepository extends JpaRepository<BranchCashbookExpense, Long> {
    List<BranchCashbookExpense> findByCashbookDay_IdOrderByCreatedAtAsc(Long cashbookDayId);
}

