package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.bothash.admissionservice.entity.FeeInvoice;

public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, Long> {

    List<FeeInvoice> findByInstallment_InstallmentId(Long installmentId);

    boolean existsByInstallment_InstallmentId(Long installmentId);

	List<FeeInvoice> findByInstallment_Admission_AdmissionId(Long admissionId);
    long deleteByInstallment_InstallmentId(Long installmentId);

    boolean existsByPayment_PaymentId(Long paymentId);
    List<FeeInvoice> findByPayment_PaymentId(Long paymentId);
    List<FeeInvoice> findByPayment_PaymentIdIn(List<Long> paymentIds);
}
