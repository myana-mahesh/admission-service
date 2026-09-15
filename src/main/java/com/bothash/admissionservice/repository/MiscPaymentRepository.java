package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.bothash.admissionservice.entity.MiscPayment;

public interface MiscPaymentRepository extends JpaRepository<MiscPayment, Long>, JpaSpecificationExecutor<MiscPayment> {
    interface RecordTotal {
        Long getRecordId();
        java.math.BigDecimal getTotalAmount();
        Long getPaymentCount();
    }

    @org.springframework.data.jpa.repository.Query("""
            select coalesce(p.parentPaymentId, p.paymentId) as recordId,
                   sum(p.amount) as totalAmount, count(p) as paymentCount
            from MiscPayment p
            where p.paymentId in :ids or p.parentPaymentId in :ids
            group by coalesce(p.parentPaymentId, p.paymentId)
            """)
    List<RecordTotal> summarizeRecords(@org.springframework.data.repository.query.Param("ids") List<Long> ids);

    boolean existsByParentPaymentId(Long parentPaymentId);
    List<MiscPayment> findTop20ByOrderByPaymentDateDescCreatedAtDesc();
}
