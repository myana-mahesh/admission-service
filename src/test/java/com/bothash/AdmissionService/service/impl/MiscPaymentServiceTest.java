package com.bothash.AdmissionService.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.bothash.admissionservice.dto.MiscPaymentRequest;
import com.bothash.admissionservice.entity.MiscPayment;
import com.bothash.admissionservice.entity.PaymentModeMaster;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.MiscPaymentRepository;
import com.bothash.admissionservice.service.impl.MiscPaymentService;
import com.bothash.admissionservice.service.impl.PaymentModeService;
import com.bothash.admissionservice.service.impl.InvoiceServiceImpl;
import com.bothash.admissionservice.service.impl.R2InvoiceStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MiscPaymentServiceTest {
    private MiscPaymentRepository repository;
    private PaymentModeService modes;
    private InvoiceServiceImpl invoices;
    private MiscPaymentService service;
    private MiscPayment original;

    @BeforeEach
    void setUp() {
        repository = mock(MiscPaymentRepository.class);
        modes = mock(PaymentModeService.class);
        invoices = mock(InvoiceServiceImpl.class);
        service = new MiscPaymentService(repository, mock(CourseRepository.class), modes, invoices,
                mock(R2InvoiceStorageService.class));
        original = MiscPayment.builder().paymentId(10L).studentName("Original name")
                .feeType("Exam Fees").amount(new BigDecimal("1000.00"))
                .courseName("").batch("").contactNumber("").build();
        when(repository.findById(10L)).thenReturn(Optional.of(original));
    }

    @ParameterizedTest
    @ValueSource(strings = {"250.00", "-250.00"})
    void addsSeparateSignedPaymentUsingOriginalRecordDetails(String amount) {
        stubSave();
        MiscPaymentRequest request = request(amount);
        request.setStudentName("Tampered name");
        request.setFeeType("Tampered type");
        var result = service.create(request);
        assertEquals(10L, result.getParentPaymentId());
        assertEquals(20L, result.getPaymentId());
        assertEquals("Original name", result.getStudentName());
        assertEquals("Exam Fees", result.getFeeType());
        assertEquals(new BigDecimal(amount), result.getAmount());
        assertEquals(new BigDecimal("1000.00"), original.getAmount());
        verify(repository, never()).save(original);
        verify(invoices).generateInvoiceForMiscPayment(any(MiscPayment.class));
    }

    @Test
    void addingFromChildLinksToOriginalRecord() {
        stubSave();
        when(repository.findById(11L)).thenReturn(Optional.of(MiscPayment.builder()
                .paymentId(11L).parentPaymentId(10L).build()));
        MiscPaymentRequest request = request("100");
        request.setParentPaymentId(11L);
        assertEquals(10L, service.create(request).getParentPaymentId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.001", "10000000000"})
    void rejectsInvalidAmountsBeforeSaving(String amount) {
        assertThrows(IllegalArgumentException.class, () -> service.create(request(amount)));
        verify(repository, never()).save(any());
        verifyNoInteractions(invoices);
    }

    @Test
    void requiresPaymentTypeAndDate() {
        MiscPaymentRequest request = request("100");
        request.setPaymentType(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        request.setPaymentType("Cash");
        request.setPaymentDate(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingParent() {
        MiscPaymentRequest request = request("100");
        request.setParentPaymentId(999L);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsStandaloneNegativePayment() {
        MiscPaymentRequest request = request("-100");
        request.setParentPaymentId(null);
        request.setStudentName("Name");
        request.setFeeType("Exam Fees");
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(repository, never()).save(any());
    }

    @Test
    void cannotDeleteOriginalWithLinkedPayments() {
        when(repository.existsByParentPaymentId(10L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.delete(10L));
        verify(repository, never()).delete(any(MiscPayment.class));
    }

    @Test
    void editsExistingNegativePaymentWithoutRemovingItsLink() {
        stubSave();
        MiscPayment child = MiscPayment.builder().paymentId(11L).parentPaymentId(10L).build();
        when(repository.findById(11L)).thenReturn(Optional.of(child));
        MiscPaymentRequest request = request("-125.00");
        request.setStudentName("Original name");
        request.setFeeType("Exam Fees");
        request.setParentPaymentId(null);
        var result = service.update(11L, request);
        assertEquals(10L, result.getParentPaymentId());
        assertEquals(new BigDecimal("-125.00"), result.getAmount());
    }

    private void stubSave() {
        when(modes.findByCode("CASH")).thenReturn(Optional.of(new PaymentModeMaster()));
        when(repository.save(any(MiscPayment.class))).thenAnswer(invocation -> {
            MiscPayment payment = invocation.getArgument(0);
            if (payment.getPaymentId() == null) payment.setPaymentId(20L);
            return payment;
        });
        when(invoices.generateInvoiceForMiscPayment(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MiscPaymentRequest request(String amount) {
        MiscPaymentRequest request = new MiscPaymentRequest();
        request.setParentPaymentId(10L);
        request.setAmount(new BigDecimal(amount));
        request.setPaymentMode("CASH");
        request.setPaymentType("Cash");
        request.setPaymentDate(LocalDate.of(2026, 9, 15));
        return request;
    }
}
