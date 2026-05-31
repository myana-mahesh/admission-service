package com.bothash.admissionservice.repository;

import java.util.List;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bothash.admissionservice.entity.FeeInstallmentPayment;

public interface FeeInstallmentPaymentRepository extends JpaRepository<FeeInstallmentPayment, Long> {
    List<FeeInstallmentPayment> findByInstallment_InstallmentIdOrderByCreatedAtAsc(Long installmentId);
    List<FeeInstallmentPayment> findByInstallment_InstallmentIdInOrderByCreatedAtAscPaymentIdAsc(List<Long> installmentIds);
    List<FeeInstallmentPayment> findByInstallment_Admission_AdmissionIdOrderByCreatedAtAscPaymentIdAsc(Long admissionId);
    List<FeeInstallmentPayment> findByPaymentGroupIdOrderByCreatedAtAscPaymentIdAsc(String paymentGroupId);
    List<FeeInstallmentPayment> findByPaymentGroupIdIn(List<String> paymentGroupIds);

    @Query("""
            select p from FeeInstallmentPayment p
            join p.installment i
            join i.admission a
            join a.admissionBranch b
            where b.id = :branchId
              and p.amount > 0
              and upper(trim(coalesce(p.paymentType, ''))) in ('CASH', 'CHEQUE', 'CHECK')
              and (p.status is null or upper(p.status) not in ('REJECTED', 'CANCELLED'))
              and function('date', p.createdAt) = :businessDate
            order by p.createdAt asc, p.paymentId asc
            """)
    List<FeeInstallmentPayment> findBranchCollectionsForDate(
            @Param("branchId") Long branchId,
            @Param("businessDate") LocalDate businessDate
    );

    @Query("""
            select p from FeeInstallmentPayment p
            join p.installment i
            join i.admission a
            join a.admissionBranch b
            where b.id = :branchId
              and p.amount > 0
              and upper(trim(coalesce(p.paymentType, ''))) in ('CASH', 'CHEQUE', 'CHECK')
              and (p.status is null or upper(p.status) not in ('REJECTED', 'CANCELLED'))
              and function('date', p.createdAt) between :fromDate and :toDate
            order by p.createdAt asc, p.paymentId asc
            """)
    List<FeeInstallmentPayment> findBranchCollectionsForDateRange(
            @Param("branchId") Long branchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            select p from FeeInstallmentPayment p
            join p.installment i
            join i.admission a
            join a.admissionBranch b
            where b.id = :branchId
              and p.amount > 0
              and upper(trim(coalesce(p.paymentType, ''))) in ('CASH', 'CHEQUE', 'CHECK')
              and (p.status is null or upper(p.status) not in ('REJECTED', 'CANCELLED'))
            order by p.createdAt asc, p.paymentId asc
            """)
    List<FeeInstallmentPayment> findBranchCollectionCandidates(@Param("branchId") Long branchId);

    @Query("""
            select p from FeeInstallmentPayment p
            join p.installment i
            join i.admission a
            join a.admissionBranch b
            where b.id = :branchId
              and p.amount > 0
              and function('date', p.createdAt) between :fromDate and :toDate
            order by p.createdAt asc, p.paymentId asc
            """)
    List<FeeInstallmentPayment> findAllPositiveByBranchAndCreatedDateRange(
            @Param("branchId") Long branchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /**
     * Cash/cheque candidates for the branch that have not yet been remitted.
     * The dashboard's "Student Fees Collected" table populates from this.
     */
    @Query("""
            select p from FeeInstallmentPayment p
            join p.installment i
            join i.admission a
            join a.admissionBranch b
            where b.id = :branchId
              and p.amount > 0
              and upper(trim(coalesce(p.paymentType, ''))) in ('CASH', 'CHEQUE', 'CHECK')
              and (p.status is null or upper(p.status) not in ('REJECTED', 'CANCELLED'))
              and p.remittanceId is null
            order by p.createdAt asc, p.paymentId asc
            """)
    List<FeeInstallmentPayment> findUnremittedBranchCollectionCandidates(@Param("branchId") Long branchId);

    /**
     * Dashboard view: every unremitted payment for the branch PLUS payments
     * whose remittance was sent at or after {@code sinceTimestamp}. Using a
     * full {@code OffsetDateTime} (rather than a date extracted in DB-tz)
     * avoids timezone-skew bugs where a row collected just before midnight
     * IST would have a UTC date one day behind.
     */
    @Query("""
            select p from FeeInstallmentPayment p
            join p.installment i
            join i.admission a
            join a.admissionBranch b
            where b.id = :branchId
              and p.amount > 0
              and upper(trim(coalesce(p.paymentType, ''))) in ('CASH', 'CHEQUE', 'CHECK')
              and (p.status is null or upper(p.status) not in ('REJECTED', 'CANCELLED'))
              and (
                   p.remittanceId is null
                   or p.remittanceId in (
                       select r.id
                         from com.bothash.admissionservice.entity.BranchCashbookRemittance r
                        where r.sentAt >= :sinceTimestamp
                   )
              )
            order by p.createdAt asc, p.paymentId asc
            """)
    List<FeeInstallmentPayment> findBranchCollectionCandidatesForDashboard(
            @Param("branchId") Long branchId,
            @Param("sinceTimestamp") java.time.OffsetDateTime sinceTimestamp
    );

    /** Payments belonging to a specific remittance (post-stamping). */
    List<FeeInstallmentPayment> findByRemittanceIdOrderByCreatedAtAscPaymentIdAsc(Long remittanceId);

    /**
     * Stamp every payment in {@code paymentIds} with {@code remittanceId},
     * but only if the row currently has no remittance (defensive double-spend guard).
     * Returns the number of rows actually updated.
     */
    @Modifying
    @Query("""
            update FeeInstallmentPayment p
               set p.remittanceId = :remittanceId
             where p.paymentId in :paymentIds
               and p.remittanceId is null
            """)
    int stampRemittanceId(
            @Param("remittanceId") Long remittanceId,
            @Param("paymentIds") List<Long> paymentIds
    );
}
