package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.FeePaymentAllocation;

public interface FeePaymentAllocationRepository extends JpaRepository<FeePaymentAllocation, Long> {

    /** Allocations for a single expense (used to reverse on edit/delete). */
    List<FeePaymentAllocation> findByExpense_Id(Long expenseId);

    /** Allocations on a single payment, oldest first. */
    List<FeePaymentAllocation> findByPayment_PaymentIdOrderByCreatedAtAscIdAsc(Long paymentId);

    /**
     * Active petty-topup allocations for a branch with positive remaining
     * (sum of all allocations on the same expense_id > 0). Used by petty
     * return to FIFO-restore fees. Ordered by oldest topup first.
     */
    @org.springframework.data.jpa.repository.Query("""
            select a from FeePaymentAllocation a
            where a.expense.id in (
                select e.id from BranchCashbookExpense e
                where e.cashbookDay.branch.id = :branchId
                  and upper(e.sourceType) = 'PETTY_TOPUP'
            )
            order by a.createdAt asc, a.id asc
            """)
    List<FeePaymentAllocation> findPettyTopupAllocationsForBranch(
            @org.springframework.data.repository.query.Param("branchId") Long branchId);
}
