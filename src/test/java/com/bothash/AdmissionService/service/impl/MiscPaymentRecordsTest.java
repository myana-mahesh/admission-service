package com.bothash.AdmissionService.service.impl;

import com.bothash.admissionservice.entity.MiscPayment;
import com.bothash.admissionservice.repository.MiscPaymentRepository;
import com.bothash.admissionservice.service.impl.MiscPaymentService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MiscPaymentRecordsTest {
    private SessionFactory factory;
    private EntityManager em;
    private MiscPaymentService service;
    private Long recordId;
    private final LocalDate date = LocalDate.of(2026, 9, 15);

    @BeforeEach
    void setUp() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.generate_statistics", "true").build();
        factory = new MetadataSources(registry).addAnnotatedClass(MiscPayment.class)
                .buildMetadata().buildSessionFactory();
        em = factory.createEntityManager();
        service = new MiscPaymentService(new JpaRepositoryFactory(em).getRepository(MiscPaymentRepository.class),
                null, null, null, null);
        em.getTransaction().begin();
        recordId = payment(null, "Same name", "1000", "CASH", "Exam Fees", date.minusDays(5)).getPaymentId();
        payment(recordId, "Same name", "300", "ONLINE", "Exam Fees", date);
        payment(recordId, "Same name", "-100", "CASH", "Exam Fees", date);
        payment(null, "Same name", "200", "CASH", "Custom charge", date);
        em.getTransaction().commit();
        em.clear();
        factory.getStatistics().clear();
    }

    @AfterEach
    void close() {
        if (em != null) em.close();
        if (factory != null) factory.close();
    }

    @Test
    void sumsSignedLinkedPaymentsWithoutMergingIdenticalNamesOrChangingOriginalAmount() {
        var result = service.searchRecords(null, null, null, null, null, null, null, null, 0, 10);
        assertEquals(2, result.getTotalElements());
        var row = result.getContent().stream().filter(p -> p.getPaymentId().equals(recordId)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1200.00"), row.getTotalAmount());
        assertEquals(new BigDecimal("1000.00"), row.getAmount());
        assertEquals(3L, row.getPaymentCount());
        assertTrue(factory.getStatistics().getPrepareStatementCount() <= 3, "No query per record");
    }

    @Test
    void matchingChildSelectsRecordButTotalIncludesPaymentsOutsideFilter() {
        var result = service.searchRecords("same", 1L, "2026-27", "Exam Fees", null,
                "ONLINE", date, date, 0, 10);
        assertEquals(1, result.getTotalElements());
        assertEquals(recordId, result.getContent().getFirst().getPaymentId());
        assertEquals(new BigDecimal("1200.00"), result.getContent().getFirst().getTotalAmount());
        assertTrue(service.searchRecords(null, 99L, null, null, null, null, null, null, 0, 10).getContent().isEmpty());
    }

    @Test
    void customFeeFilterSupportsOthersWithAndWithoutSpecificText() {
        assertEquals(1, service.searchRecords(null, null, null, "Others", null, null, null, null, 0, 10).getTotalElements());
        assertEquals(1, service.searchRecords(null, null, null, "Others", "Custom", null, null, null, 0, 10).getTotalElements());
        assertEquals(0, service.searchRecords(null, null, null, "Others", "Exam", null, null, null, 0, 10).getTotalElements());
    }

    @Test
    void historyIncludesOriginalRefundAndTheirOwnInvoicesWithPagination() {
        var first = service.history(recordId, 0, 2);
        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getContent().size());
        assertEquals(new BigDecimal("-100.00"), first.getContent().getFirst().getAmount());
        for (var row : first.getContent()) {
            assertEquals("https://example.test/invoices/" + row.getPaymentId(), row.getInvoiceUrl());
            assertEquals("https://example.test/receipts/" + row.getPaymentId(), row.getReceiptUrl());
        }
        var last = service.history(recordId, 1, 2);
        assertEquals(recordId, last.getContent().getFirst().getPaymentId());
        assertTrue(last.isLast());
        assertThrows(IllegalArgumentException.class, () -> service.history(Long.MAX_VALUE, 0, 20));
    }

    @Test
    void paginatesRecordsBeyondOneHundredAndFindsLaterRecords() {
        em.getTransaction().begin();
        for (int i = 0; i < 105; i++) payment(null, "Later record " + i, "5", "CASH", "Exam Fees", date);
        em.getTransaction().commit();
        em.clear();
        var page = service.searchRecords(null, null, null, null, null, null, null, null, 10, 10);
        assertEquals(107, page.getTotalElements());
        assertEquals(7, page.getContent().size());
        assertEquals(1, service.searchRecords("Later record 104", null, null, null, null, null, null, null, 0, 10).getTotalElements());
    }

    private MiscPayment payment(Long parent, String name, String amount, String mode, String feeType, LocalDate paidOn) {
        var payment = MiscPayment.builder().parentPaymentId(parent).studentName(name)
                .amount(new BigDecimal(amount)).paymentMode(mode).paymentType("Cash").paymentDate(paidOn)
                .feeType(feeType).batch("2026-27").courseId(1L).build();
        em.persist(payment);
        payment.setInvoiceDownloadUrl("https://example.test/invoices/" + payment.getPaymentId());
        payment.setReceiptStorageUrl("https://example.test/receipts/" + payment.getPaymentId());
        return payment;
    }
}
