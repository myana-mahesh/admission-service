package com.bothash.admissionservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.dto.FeeLedgerResponseDto;
import com.bothash.admissionservice.dto.FeeLedgerRowDto;
import com.bothash.admissionservice.dto.FeeLedgerSummaryDto;
import com.bothash.admissionservice.entity.AcademicYear;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.BranchMaster;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.FileUpload;
import com.bothash.admissionservice.entity.PaymentModeMaster;
import com.bothash.admissionservice.entity.Student;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeeLedgerService {

    private final EntityManager entityManager;

    public FeeLedgerResponseDto search(
            String q,
            List<Long> branchIds,
            List<Long> courseIds,
            String batch,
            List<String> batchCodes,
            Long academicYearId,
            LocalDate startDate,
            LocalDate endDate,
            String dateType,
            List<String> statusList,
            String dueStatus,
            List<String> paymentModes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly,
            Pageable pageable
    ) {
        FeeLedgerSummaryDto summary = querySummary(
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly
        );

        AdmissionPage admissionPage = queryAdmissionsPage(
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly, pageable
        );

        List<FeeInstallment> installments = admissionPage.admissionIds.isEmpty()
                ? List.of()
                : fetchInstallments(admissionPage.admissionIds);

        Map<Long, List<FeeInstallment>> byAdmission = installments.stream()
                .filter(inst -> inst.getAdmission() != null)
                .collect(Collectors.groupingBy(inst -> inst.getAdmission().getAdmissionId()));

        List<FeeLedgerRowDto> rows = admissionPage.admissionIds.stream()
                .map(id -> buildStudentRow(byAdmission.getOrDefault(id, List.of())))
                .filter(Objects::nonNull)
                .toList();

        return FeeLedgerResponseDto.builder()
                .content(rows)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(admissionPage.totalElements)
                .totalPages(admissionPage.totalPages)
                .summary(summary)
                .build();
    }

    private FeeLedgerRowDto buildStudentRow(List<FeeInstallment> installments) {
        if (installments == null || installments.isEmpty()) {
            return null;
        }
        Admission2 admission = installments.get(0).getAdmission();
        if (admission == null) {
            return null;
        }
        Student student = admission.getStudent();
        Course course = admission.getCourse();
        AcademicYear year = admission.getYear();
        BranchMaster branch = admission.getAdmissionBranch();

        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        LocalDate nextDueDate = null;
        BigDecimal nextDueAmount = BigDecimal.ZERO;

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        statusCounts.put("Pending", 0);
        statusCounts.put("Under Verification", 0);
        statusCounts.put("Partial Received", 0);
        statusCounts.put("Paid", 0);
        statusCounts.put("Cancelled", 0);

        for (FeeInstallment inst : installments) {
            BigDecimal due = inst.getAmountDue() != null ? inst.getAmountDue() : BigDecimal.ZERO;
            BigDecimal paid = inst.getAmountPaid() != null ? inst.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal pending = due.subtract(paid);

            totalDue = totalDue.add(due);
            totalPaid = totalPaid.add(paid);

            String computedStatus = computeStatus(inst.getStatus(), due, paid);
            statusCounts.computeIfPresent(computedStatus, (k, v) -> v + 1);

            if (pending.compareTo(BigDecimal.ZERO) > 0 && inst.getDueDate() != null) {
                if (nextDueDate == null || inst.getDueDate().isBefore(nextDueDate)) {
                    nextDueDate = inst.getDueDate();
                    nextDueAmount = pending;
                }
            }
        }

        BigDecimal pendingAmount = totalDue.subtract(totalPaid);

        String statusSummary = statusCounts.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining(", "));

        return FeeLedgerRowDto.builder()
                .admissionId(admission.getAdmissionId())
                .studentId(student != null ? student.getStudentId() : null)
                .studentName(student != null ? student.getFullName() : null)
                .absId(student != null ? student.getAbsId() : null)
                .mobile(student != null ? student.getMobile() : null)
                .branchId(branch != null ? branch.getId() : null)
                .branchName(branch != null ? branch.getName() : null)
                .courseId(course != null ? course.getCourseId() : null)
                .courseName(course != null ? course.getName() : null)
                .batch(admission.getBatch())
                .academicYear(year != null ? year.getLabel() : null)
                .totalFeeAmount(totalDue)
                .paidAmount(totalPaid)
                .pendingAmount(pendingAmount)
                .dueNextDate(nextDueDate)
                .dueNextAmount(nextDueAmount)
                .statusSummary(statusSummary)
                .build();
    }

    private AdmissionPage queryAdmissionsPage(
            String q,
            List<Long> branchIds,
            List<Long> courseIds,
            String batch,
            List<String> batchCodes,
            Long academicYearId,
            LocalDate startDate,
            LocalDate endDate,
            String dateType,
            List<String> statusList,
            String dueStatus,
            List<String> paymentModes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly,
            Pageable pageable
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<FeeInstallment> root = cq.from(FeeInstallment.class);

        Join<FeeInstallment, Admission2> admission = root.join("admission", JoinType.LEFT);
        Join<Admission2, Student> student = admission.join("student", JoinType.LEFT);
        Join<Admission2, Course> course = admission.join("course", JoinType.LEFT);
        Join<Admission2, AcademicYear> year = admission.join("year", JoinType.LEFT);
        Join<Admission2, BranchMaster> branch = admission.join("admissionBranch", JoinType.LEFT);
        Join<FeeInstallment, PaymentModeMaster> paymentMode = root.join("paymentMode", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(
                cq, cb, root, admission, student, course, year, branch, paymentMode,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly
        );

        // Apply payment-related filters using FeeInstallmentPayment table
        // These filters check actual payment records, not installment records
        boolean hasPaymentFilters = (paymentModes != null && !paymentModes.isEmpty()) ||
                                    StringUtils.hasText(verification) ||
                                    StringUtils.hasText(proofAttached) ||
                                    StringUtils.hasText(txnPresent);

        if (hasPaymentFilters) {
            Subquery<Long> paymentSubquery = cq.subquery(Long.class);
            Root<FeeInstallmentPayment> paymentRoot = paymentSubquery.from(FeeInstallmentPayment.class);
            Join<FeeInstallmentPayment, FeeInstallment> paymentInstallment = paymentRoot.join("installment", JoinType.INNER);
            Join<FeeInstallment, Admission2> paymentAdmission = paymentInstallment.join("admission", JoinType.INNER);

            List<Predicate> paymentPredicates = new ArrayList<>();

            // Payment Mode filter
            if (paymentModes != null && !paymentModes.isEmpty()) {
                List<String> lower = paymentModes.stream()
                        .filter(StringUtils::hasText)
                        .map(String::toLowerCase)
                        .toList();
                if (!lower.isEmpty()) {
                    Join<FeeInstallmentPayment, PaymentModeMaster> feePaymentMode = paymentRoot.join("paymentMode", JoinType.INNER);
                    paymentPredicates.add(cb.lower(feePaymentMode.get("code")).in(lower));
                }
            }

            // Verification filter
            if (StringUtils.hasText(verification)) {
                String v = verification.trim().toUpperCase();
                switch (v) {
                    case "VERIFIED" -> paymentPredicates.add(cb.isTrue(paymentRoot.get("isVerified")));
                    case "NOT_VERIFIED" -> paymentPredicates.add(cb.or(
                            cb.isFalse(paymentRoot.get("isVerified")),
                            cb.isNull(paymentRoot.get("isVerified"))
                    ));
                    default -> {}
                }
            }

            // Proof attached filter (check FileUpload table for payment receipts)
            if (StringUtils.hasText(proofAttached)) {
                String p = proofAttached.trim().toUpperCase();
                Subquery<Long> fileSubquery = cq.subquery(Long.class);
                Root<FileUpload> fileRoot = fileSubquery.from(FileUpload.class);
                fileSubquery.select(cb.literal(1L));
                fileSubquery.where(cb.equal(fileRoot.get("installmentPayment").get("paymentId"), paymentRoot.get("paymentId")));

                if ("YES".equals(p)) {
                    paymentPredicates.add(cb.exists(fileSubquery));
                } else if ("NO".equals(p)) {
                    paymentPredicates.add(cb.not(cb.exists(fileSubquery)));
                }
            }

            // Transaction reference filter
            if (StringUtils.hasText(txnPresent)) {
                String t = txnPresent.trim().toUpperCase();
                if ("YES".equals(t)) {
                    paymentPredicates.add(cb.and(
                            cb.isNotNull(paymentRoot.get("txnRef")),
                            cb.notEqual(paymentRoot.get("txnRef"), "")
                    ));
                } else if ("NO".equals(t)) {
                    paymentPredicates.add(cb.or(
                            cb.isNull(paymentRoot.get("txnRef")),
                            cb.equal(paymentRoot.get("txnRef"), "")
                    ));
                }
            }

            paymentSubquery.select(paymentAdmission.get("admissionId"));
            paymentSubquery.where(paymentPredicates.toArray(new Predicate[0]));

            predicates.add(admission.get("admissionId").in(paymentSubquery));
        }

        cq.select(admission.get("admissionId")).distinct(true);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(admission.get("admissionId")));

        TypedQuery<Long> query = entityManager.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<Long> admissionIds = query.getResultList();

        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<FeeInstallment> countRoot = countCq.from(FeeInstallment.class);
        Join<FeeInstallment, Admission2> countAdmission = countRoot.join("admission", JoinType.LEFT);
        Join<Admission2, Student> countStudent = countAdmission.join("student", JoinType.LEFT);
        Join<Admission2, Course> countCourse = countAdmission.join("course", JoinType.LEFT);
        Join<Admission2, AcademicYear> countYear = countAdmission.join("year", JoinType.LEFT);
        Join<Admission2, BranchMaster> countBranch = countAdmission.join("admissionBranch", JoinType.LEFT);
        Join<FeeInstallment, PaymentModeMaster> countPaymentMode = countRoot.join("paymentMode", JoinType.LEFT);

        List<Predicate> countPredicates = buildPredicates(
                countCq, cb, countRoot, countAdmission, countStudent, countCourse, countYear, countBranch, countPaymentMode,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly
        );
        countCq.select(cb.countDistinct(countAdmission.get("admissionId")));
        countCq.where(countPredicates.toArray(new Predicate[0]));
        long total = entityManager.createQuery(countCq).getSingleResult();

        int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) total / pageable.getPageSize());
        return new AdmissionPage(admissionIds, total, totalPages);
    }

    private List<FeeInstallment> fetchInstallments(List<Long> admissionIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<FeeInstallment> cq = cb.createQuery(FeeInstallment.class);
        Root<FeeInstallment> root = cq.from(FeeInstallment.class);
        cq.select(root);
        cq.where(root.get("admission").get("admissionId").in(admissionIds));
        return entityManager.createQuery(cq).getResultList();
    }

    private record AdmissionPage(List<Long> admissionIds, long totalElements, int totalPages) {}

    private FeeLedgerSummaryDto querySummary(
            String q,
            List<Long> branchIds,
            List<Long> courseIds,
            String batch,
            List<String> batchCodes,
            Long academicYearId,
            LocalDate startDate,
            LocalDate endDate,
            String dateType,
            List<String> statusList,
            String dueStatus,
            List<String> paymentModes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<FeeInstallment> root = cq.from(FeeInstallment.class);

        Join<FeeInstallment, Admission2> admission = root.join("admission", JoinType.LEFT);
        Join<Admission2, Student> student = admission.join("student", JoinType.LEFT);
        Join<Admission2, Course> course = admission.join("course", JoinType.LEFT);
        Join<Admission2, AcademicYear> year = admission.join("year", JoinType.LEFT);
        Join<Admission2, BranchMaster> branch = admission.join("admissionBranch", JoinType.LEFT);
        Join<FeeInstallment, PaymentModeMaster> paymentMode = root.join("paymentMode", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(
                cq, cb, root, admission, student, course, year, branch, paymentMode,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly
        );

        Expression<BigDecimal> due = cb.coalesce(root.get("amountDue").as(BigDecimal.class), BigDecimal.ZERO);
        Expression<BigDecimal> paid = cb.coalesce(root.get("amountPaid").as(BigDecimal.class), BigDecimal.ZERO);
        Expression<BigDecimal> pending = cb.diff(due, paid);

        LocalDate today = LocalDate.now();
        LocalDate next7 = today.plusDays(7);

        Expression<BigDecimal> overdueAmount = cb.<BigDecimal>selectCase()
                .when(cb.and(
                        cb.isNotNull(root.get("dueDate")),
                        cb.lessThan(root.get("dueDate"), today),
                        cb.greaterThan(pending, BigDecimal.ZERO)
                ), pending)
                .otherwise(BigDecimal.ZERO);

        Expression<BigDecimal> dueNext7Amount = cb.<BigDecimal>selectCase()
                .when(cb.and(
                        cb.isNotNull(root.get("dueDate")),
                        cb.between(root.get("dueDate"), today.plusDays(1), next7),
                        cb.greaterThan(pending, BigDecimal.ZERO)
                ), pending)
                .otherwise(BigDecimal.ZERO);

        Expression<String> statusNorm = cb.lower(cb.trim(root.get("status")));
        Expression<Long> underVerificationCount = cb.<Long>selectCase()
                .when(cb.like(statusNorm, "%verification%"), 1L)
                .otherwise(0L);
        Expression<Long> underVerificationStudentCount = cb.countDistinct(
                cb.<Long>selectCase()
                        .when(cb.like(statusNorm, "%verification%"), student.get("studentId"))
                        .otherwise(cb.nullLiteral(Long.class))
        );

        cq.multiselect(
                cb.sum(due),
                cb.sum(paid),
                cb.sum(pending),
                cb.sum(overdueAmount),
                cb.sum(dueNext7Amount),
                cb.sum(underVerificationCount),
                underVerificationStudentCount
        );
        cq.where(predicates.toArray(new Predicate[0]));

        Object[] row = entityManager.createQuery(cq).getSingleResult();

        return FeeLedgerSummaryDto.builder()
                .totalFeeAmount(asBigDecimal(row[0]))
                .totalCollected(asBigDecimal(row[1]))
                .totalPending(asBigDecimal(row[2]))
                .overdueAmount(asBigDecimal(row[3]))
                .dueNext7DaysAmount(asBigDecimal(row[4]))
                .underVerificationCount(asLong(row[5]))
                .underVerificationStudentCount(asLong(row[6]))
                .build();
    }

    private List<Predicate> buildPredicates(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<FeeInstallment> root,
            Join<FeeInstallment, Admission2> admission,
            Join<Admission2, Student> student,
            Join<Admission2, Course> course,
            Join<Admission2, AcademicYear> year,
            Join<Admission2, BranchMaster> branch,
            Join<FeeInstallment, PaymentModeMaster> paymentMode,
            String q,
            List<Long> branchIds,
            List<Long> courseIds,
            String batch,
            List<String> batchCodes,
            Long academicYearId,
            LocalDate startDate,
            LocalDate endDate,
            String dateType,
            List<String> statusList,
            String dueStatus,
            List<String> paymentModes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly
    ) {
        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(q)) {
            String like = "%" + q.toLowerCase().trim() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(student.get("fullName")), like),
                    cb.like(cb.lower(student.get("absId")), like),
                    cb.like(cb.lower(student.get("mobile")), like)
            ));
        }

        if (branchIds != null && !branchIds.isEmpty()) {
            predicates.add(branch.get("id").in(branchIds));
        }
        if (courseIds != null && !courseIds.isEmpty()) {
            predicates.add(course.get("courseId").in(courseIds));
        }
        if (StringUtils.hasText(batch)) {
            predicates.add(cb.equal(admission.get("batch"), batch));
        } else if (batchCodes != null && !batchCodes.isEmpty()) {
            predicates.add(admission.get("batch").in(batchCodes));
        }
        if (academicYearId != null) {
            predicates.add(cb.equal(year.get("yearId"), academicYearId));
        }
        if (branchApprovedOnly != null) {
            predicates.add(cb.equal(admission.get("branchApproved"), branchApprovedOnly));
        }

        if ("PAID".equalsIgnoreCase(dateType)) {
            applyPaymentDateFilter(query, cb, admission, predicates, startDate, endDate);
        } else {
            applyDateFilter(cb, root, predicates, startDate, endDate, dateType);
        }

        applyStatusFilter(cb, root, predicates, statusList);
        applyDueStatusFilter(cb, root, predicates, dueStatus);

        // Note: Payment mode, verification, proof attached, and txn filters are now handled
        // via subquery in the search method to check FeeInstallmentPayment table instead of FeeInstallment

        applyPaidAmountFilter(query, cb, admission, predicates, paidAmountOp, paidAmount);
        applyPendingRangeFilter(query, cb, admission, predicates, pendingMin, pendingMax);

        return predicates;
    }

    private void applyDateFilter(CriteriaBuilder cb, Root<FeeInstallment> root, List<Predicate> predicates,
                                 LocalDate startDate, LocalDate endDate, String dateType) {
        if (startDate == null && endDate == null) {
            return;
        }
        String type = dateType == null ? "DUE" : dateType.toUpperCase();
        if ("CREATED".equals(type)) {
            OffsetDateTime start = startDate != null
                    ? OffsetDateTime.of(startDate, LocalTime.MIN, ZoneOffset.UTC) : null;
            OffsetDateTime end = endDate != null
                    ? OffsetDateTime.of(endDate.plusDays(1), LocalTime.MIN, ZoneOffset.UTC) : null;
            if (start != null && end != null) {
                predicates.add(cb.between(root.get("createdAt"), start, end));
            } else if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            } else if (end != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), end));
            }
        } else {
            applyLocalDateRange(cb, predicates, root.get("dueDate"), startDate, endDate);
        }
    }

    private void applyPaymentDateFilter(CriteriaQuery<?> cq, CriteriaBuilder cb,
                                        Join<FeeInstallment, Admission2> admission,
                                        List<Predicate> predicates,
                                        LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return;
        }
        Subquery<Long> paymentSubquery = cq.subquery(Long.class);
        Root<FeeInstallmentPayment> paymentRoot = paymentSubquery.from(FeeInstallmentPayment.class);
        Join<FeeInstallmentPayment, FeeInstallment> paymentInstallment = paymentRoot.join("installment", JoinType.INNER);
        Join<FeeInstallment, Admission2> paymentAdmission = paymentInstallment.join("admission", JoinType.INNER);

        List<Predicate> paymentPredicates = new ArrayList<>();
        applyLocalDateRange(cb, paymentPredicates, paymentRoot.get("paidOn"), startDate, endDate);

        paymentSubquery.select(paymentAdmission.get("admissionId"));
        paymentSubquery.where(paymentPredicates.toArray(new Predicate[0]));

        predicates.add(admission.get("admissionId").in(paymentSubquery));
    }

    private void applyLocalDateRange(CriteriaBuilder cb, List<Predicate> predicates,
                                     Expression<LocalDate> field, LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            predicates.add(cb.between(field, start, end));
        } else if (start != null) {
            predicates.add(cb.greaterThanOrEqualTo(field, start));
        } else if (end != null) {
            predicates.add(cb.lessThanOrEqualTo(field, end));
        }
    }

    private void applyStatusFilter(CriteriaBuilder cb, Root<FeeInstallment> root,
                                   List<Predicate> predicates, List<String> statusList) {
        if (statusList == null || statusList.isEmpty()) {
            return;
        }
        Expression<BigDecimal> due = cb.coalesce(root.get("amountDue"), BigDecimal.ZERO);
        Expression<BigDecimal> paid = cb.coalesce(root.get("amountPaid"), BigDecimal.ZERO);
        Expression<BigDecimal> pending = cb.diff(due, paid);
        Expression<String> statusNorm = cb.lower(cb.trim(root.get("status")));

        List<Predicate> statusPreds = new ArrayList<>();
        for (String status : statusList) {
            if (!StringUtils.hasText(status)) continue;
            String s = status.trim().toLowerCase();
            switch (s) {
                case "paid" -> statusPreds.add(cb.greaterThanOrEqualTo(paid, due));
                case "partially%20paid", "partial%20received", "partial_received" -> statusPreds.add(
                        cb.and(cb.greaterThan(paid, BigDecimal.ZERO), cb.lessThan(paid, due))
                );
                case "pending" -> statusPreds.add(
                        cb.and(cb.equal(paid, BigDecimal.ZERO),
                                cb.notLike(statusNorm, "%verification%"),
                                cb.notEqual(statusNorm, "paid"),
                                cb.notEqual(statusNorm, "cancelled"))
                );
                case "under verification", "under_verification", "under%20verification" -> statusPreds.add(
                        cb.like(statusNorm, "%verification%")
                );
                case "cancelled" -> statusPreds.add(
                        cb.equal(statusNorm, "cancelled")
                );
                default -> statusPreds.add(
                        cb.equal(statusNorm, s)
                );
            }
        }
        if (!statusPreds.isEmpty()) {
            predicates.add(cb.or(statusPreds.toArray(new Predicate[0])));
        }
    }

    private void applyDueStatusFilter(CriteriaBuilder cb, Root<FeeInstallment> root,
                                      List<Predicate> predicates, String dueStatus) {
        if (!StringUtils.hasText(dueStatus)) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate next7 = today.plusDays(7);
        Expression<BigDecimal> due = cb.coalesce(root.get("amountDue"), BigDecimal.ZERO);
        Expression<BigDecimal> paid = cb.coalesce(root.get("amountPaid"), BigDecimal.ZERO);
        Expression<BigDecimal> pending = cb.diff(due, paid);

        String s = dueStatus.trim().toUpperCase();
        switch (s) {
            case "DUE_TODAY" -> predicates.add(cb.and(
                    cb.equal(root.get("dueDate"), today),
                    cb.greaterThan(pending, BigDecimal.ZERO)
            ));
            case "DUE_NEXT_7" -> predicates.add(cb.and(
                    cb.between(root.get("dueDate"), today.plusDays(1), next7),
                    cb.greaterThan(pending, BigDecimal.ZERO)
            ));
            case "OVERDUE" -> predicates.add(cb.and(
                    cb.isNotNull(root.get("dueDate")),
                    cb.lessThan(root.get("dueDate"), today),
                    cb.greaterThan(pending, BigDecimal.ZERO)
            ));
            case "NOT_DUE" -> predicates.add(cb.or(
                    cb.isNull(root.get("dueDate")),
                    cb.lessThanOrEqualTo(pending, BigDecimal.ZERO),
                    cb.greaterThan(root.get("dueDate"), next7)
            ));
            default -> {
            }
        }
    }

    // Removed applyVerificationFilter, applyProofFilter, and applyTxnFilter methods
    // These filters are now handled via subquery checking FeeInstallmentPayment table
    // See search() method where payment-related filters are applied

    private void applyPendingRangeFilter(CriteriaQuery<?> query, CriteriaBuilder cb,
                                         Join<FeeInstallment, Admission2> admission,
                                         List<Predicate> predicates, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return;
        }
        Subquery<BigDecimal> pendingSubquery = query.subquery(BigDecimal.class);
        Root<FeeInstallment> pendingRoot = pendingSubquery.from(FeeInstallment.class);
        Join<FeeInstallment, Admission2> pendingAdmission = pendingRoot.join("admission", JoinType.INNER);
        Expression<BigDecimal> dueSum = cb.sum(cb.coalesce(pendingRoot.get("amountDue"), BigDecimal.ZERO));
        Expression<BigDecimal> paidSum = cb.sum(cb.coalesce(pendingRoot.get("amountPaid"), BigDecimal.ZERO));
        Expression<BigDecimal> pending = cb.diff(dueSum, paidSum);
        pendingSubquery.select(pending);
        pendingSubquery.where(cb.equal(pendingAdmission.get("admissionId"), admission.get("admissionId")));
        if (min != null) {
            predicates.add(cb.greaterThanOrEqualTo(pendingSubquery, min));
        }
        if (max != null) {
            predicates.add(cb.lessThanOrEqualTo(pendingSubquery, max));
        }
    }

    private void applyPaidAmountFilter(CriteriaQuery<?> query, CriteriaBuilder cb,
                                       Join<FeeInstallment, Admission2> admission,
                                       List<Predicate> predicates, String op, BigDecimal amount) {
        if (!StringUtils.hasText(op) || amount == null) {
            return;
        }
        Subquery<BigDecimal> paidSumSubquery = query.subquery(BigDecimal.class);
        Root<FeeInstallment> paidRoot = paidSumSubquery.from(FeeInstallment.class);
        Join<FeeInstallment, Admission2> paidAdmission = paidRoot.join("admission", JoinType.INNER);
        Expression<BigDecimal> paidSum = cb.sum(cb.coalesce(paidRoot.get("amountPaid"), BigDecimal.ZERO));
        paidSumSubquery.select(paidSum);
        paidSumSubquery.where(cb.equal(paidAdmission.get("admissionId"), admission.get("admissionId")));

        String norm = op.trim().toUpperCase();
        switch (norm) {
            case "LT" -> predicates.add(cb.lessThan(paidSumSubquery, amount));
            case "LTE" -> predicates.add(cb.lessThanOrEqualTo(paidSumSubquery, amount));
            case "EQ" -> predicates.add(cb.equal(paidSumSubquery, amount));
            case "GT" -> predicates.add(cb.greaterThan(paidSumSubquery, amount));
            case "GTE" -> predicates.add(cb.greaterThanOrEqualTo(paidSumSubquery, amount));
            default -> {
            }
        }
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private String computeStatus(String storedStatus, BigDecimal due, BigDecimal paid) {
        if (storedStatus != null && storedStatus.equalsIgnoreCase("Under Verification")) {
            return "Under Verification";
        }
        if (storedStatus != null && storedStatus.equalsIgnoreCase("Cancelled")) {
            return "Cancelled";
        }
        if (paid.compareTo(due) >= 0 && due.compareTo(BigDecimal.ZERO) > 0) {
            return "Paid";
        }
        if (paid.compareTo(BigDecimal.ZERO) > 0 && paid.compareTo(due) < 0) {
            return "Partial Received";
        }
        if (storedStatus != null && storedStatus.equalsIgnoreCase("Partial Received")) {
            return "Partial Received";
        }
        if (storedStatus != null && storedStatus.equalsIgnoreCase("Partially Paid")) {
            return "Partial Received";
        }
        return "Pending";
    }

    private String computeDueStatus(LocalDate dueDate, BigDecimal pending) {
        if (dueDate == null || pending.compareTo(BigDecimal.ZERO) <= 0) {
            return "Not due";
        }
        LocalDate today = LocalDate.now();
        if (dueDate.isBefore(today)) {
            return "Overdue";
        }
        if (dueDate.isEqual(today)) {
            return "Due today";
        }
        if (!dueDate.isAfter(today.plusDays(7))) {
            return "Due next 7 days";
        }
        return "Not due";
    }
}
