package com.bothash.admissionservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.dto.FeeLedgerPaymentResponseDto;
import com.bothash.admissionservice.dto.FeeLedgerResponseDto;
import com.bothash.admissionservice.dto.FeeLedgerRowDto;
import com.bothash.admissionservice.dto.FeeLedgerSummaryDto;
import com.bothash.admissionservice.dto.FeePaymentGroupDto;
import com.bothash.admissionservice.dto.LedgerOtherPaymentRowDto;
import com.bothash.admissionservice.entity.AcademicYear;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.AdmissionOtherPayment;
import com.bothash.admissionservice.entity.BranchMaster;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInvoice;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.FileUpload;
import com.bothash.admissionservice.entity.Guardian;
import com.bothash.admissionservice.entity.PaymentModeMaster;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.entity.StudentFeeSchedule;
import com.bothash.admissionservice.entity.TelecallerAssignment;
import com.bothash.admissionservice.enumpackage.GuardianRelation;
import com.bothash.admissionservice.repository.FeeInstallmentPaymentRepository;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import com.bothash.admissionservice.repository.FileUploadRepository;
import com.bothash.admissionservice.service.impl.InvoiceServiceImpl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
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
    private final FileUploadRepository uploadRepo;
    private final FeeInvoiceRepository invoiceRepo;
    private final FeeInstallmentPaymentRepository paymentRepo;

    /**
     * Per-request scope carrying the active telecaller assignment rules that must
     * further restrict the fees-ledger query for the current caller. Populated by
     * {@link #runInTelecallerScope(List, java.util.function.Supplier)}; read by the
     * predicate builders. Non-telecaller callers never set this, so their queries
     * are unaffected.
     */
    private static final ThreadLocal<List<TelecallerAssignment>> TELECALLER_RULES_TL = new ThreadLocal<>();

    /**
     * Runs {@code action} with a telecaller scope active on the current thread.
     *
     * <p>Semantics of {@code rules}:
     * <ul>
     *   <li>{@code null} — no scope active. Action sees the default (unrestricted)
     *       query. Non-telecaller callers use this path.</li>
     *   <li>empty list — scope active with no matching rules. The query is
     *       forced to return no rows (block-all). Used when a telecaller has
     *       zero assignments so they can't accidentally see everything.</li>
     *   <li>non-empty list — scope active with rules OR'd together.</li>
     * </ul>
     * ThreadLocal is guaranteed cleared afterwards even on exception.
     */
    public <T> T runInTelecallerScope(List<TelecallerAssignment> rules, java.util.function.Supplier<T> action) {
        boolean entered = false;
        try {
            if (rules != null) {
                TELECALLER_RULES_TL.set(rules);
                entered = true;
            }
            return action.get();
        } finally {
            if (entered) {
                TELECALLER_RULES_TL.remove();
            }
        }
    }

    /**
     * OR-of-ANDs across the active telecaller rules. Each rule contributes an AND
     * over its non-null criteria (batch, course, running paid-amount comparator).
     * When no rules are active, no predicate is added — the caller sees everything.
     * The controller layer short-circuits to an empty ledger for a telecaller with
     * zero active rules, so this method treats absent-rules as "no additional scope".
     */
    private void applyTelecallerScope(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            From<?, Admission2> admission,
            List<Predicate> predicates
    ) {
        List<TelecallerAssignment> rules = TELECALLER_RULES_TL.get();
        if (rules == null) {
            return;
        }
        if (rules.isEmpty()) {
            // Scope explicitly active but zero rules — block every row so the
            // telecaller sees nothing until an HO assigns criteria to them.
            predicates.add(cb.disjunction());
            return;
        }
        List<Predicate> ruleOr = new ArrayList<>();
        for (TelecallerAssignment rule : rules) {
            List<Predicate> ruleAnd = new ArrayList<>();
            if (StringUtils.hasText(rule.getBatchCode())) {
                ruleAnd.add(cb.equal(admission.get("batch"), rule.getBatchCode()));
            }
            if (rule.getCourseId() != null) {
                ruleAnd.add(cb.equal(admission.get("course").get("courseId"), rule.getCourseId()));
            }
            if (StringUtils.hasText(rule.getPaidAmountOp()) && rule.getPaidAmountValue() != null) {
                Predicate paidPredicate = buildTelecallerPaidAmountPredicate(
                        query, cb, admission, rule.getPaidAmountOp(), rule.getPaidAmountValue()
                );
                if (paidPredicate != null) {
                    ruleAnd.add(paidPredicate);
                }
            }
            if (ruleAnd.isEmpty()) {
                continue;
            }
            ruleOr.add(ruleAnd.size() == 1 ? ruleAnd.get(0) : cb.and(ruleAnd.toArray(new Predicate[0])));
        }
        if (ruleOr.isEmpty()) {
            return;
        }
        predicates.add(cb.or(ruleOr.toArray(new Predicate[0])));
    }

    private Predicate buildTelecallerPaidAmountPredicate(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            From<?, Admission2> admission,
            String op,
            BigDecimal amount
    ) {
        Subquery<BigDecimal> paidSumSubquery = query.subquery(BigDecimal.class);
        Root<FeeInstallment> paidRoot = paidSumSubquery.from(FeeInstallment.class);
        Join<FeeInstallment, Admission2> paidAdmission = paidRoot.join("admission", JoinType.INNER);
        Expression<BigDecimal> paidSum = cb.sum(cb.coalesce(paidRoot.get("amountPaid"), BigDecimal.ZERO));
        paidSumSubquery.select(paidSum);
        paidSumSubquery.where(cb.equal(paidAdmission.get("admissionId"), admission.get("admissionId")));

        return switch (op.trim().toUpperCase()) {
            case "LT" -> cb.lessThan(paidSumSubquery, amount);
            case "LTE" -> cb.lessThanOrEqualTo(paidSumSubquery, amount);
            case "EQ" -> cb.equal(paidSumSubquery, amount);
            case "GT" -> cb.greaterThan(paidSumSubquery, amount);
            case "GTE" -> cb.greaterThanOrEqualTo(paidSumSubquery, amount);
            default -> null;
        };
    }

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
            List<String> paymentTypes,
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
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly
        );

        AdmissionPage admissionPage = queryAdmissionsPage(
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly, pageable
        );

        List<FeeInstallment> installments = admissionPage.admissionIds.isEmpty()
                ? List.of()
                : fetchInstallments(admissionPage.admissionIds);

        Map<Long, List<FeeInstallment>> byAdmission = installments.stream()
                .filter(inst -> inst.getAdmission() != null)
                .collect(Collectors.groupingBy(inst -> inst.getAdmission().getAdmissionId()));
        Map<Long, List<FeeInstallmentPayment>> paymentsByInstallment = fetchPaymentsByInstallment(installments);
        Map<Long, Long> scheduleCountsByStudent = fetchScheduleCounts(admissionPage.admissionIds, byAdmission);

        List<FeeLedgerRowDto> rows = admissionPage.admissionIds.stream()
                .map(id -> buildStudentRow(
                        byAdmission.getOrDefault(id, List.of()),
                        paymentsByInstallment,
                        scheduleCountsByStudent
                ))
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

    public FeeLedgerPaymentResponseDto searchPayments(
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
            List<String> paymentTypes,
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
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly
        );

        PaymentGroupPage paymentGroupPage = queryPaymentGroupPage(
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly, pageable
        );

        List<FeePaymentGroupDto> groups = paymentGroupPage.groups().isEmpty()
                ? List.of()
                : buildLedgerPaymentGroups(fetchPaymentsForGroupPage(paymentGroupPage.groups()));

        return FeeLedgerPaymentResponseDto.builder()
                .content(groups)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(paymentGroupPage.totalElements())
                .totalPages(paymentGroupPage.totalPages())
                .summary(summary)
                .build();
    }

    public List<LedgerOtherPaymentRowDto> searchOtherPayments(
            String q,
            List<Long> branchIds,
            List<Long> courseIds,
            String batch,
            List<String> batchCodes,
            Long academicYearId,
            LocalDate startDate,
            LocalDate endDate,
            List<String> paymentModes,
            List<String> paymentTypes,
            Boolean branchApprovedOnly
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AdmissionOtherPayment> cq = cb.createQuery(AdmissionOtherPayment.class);
        Root<AdmissionOtherPayment> root = cq.from(AdmissionOtherPayment.class);
        Join<AdmissionOtherPayment, Admission2> admission = root.join("admission", JoinType.INNER);
        Join<Admission2, Student> student = admission.join("student", JoinType.LEFT);
        Join<Admission2, BranchMaster> branch = admission.join("lectureBranch", JoinType.LEFT);
        Join<Admission2, Course> course = admission.join("course", JoinType.LEFT);
        Join<Admission2, AcademicYear> year = admission.join("year", JoinType.LEFT);
        Join<AdmissionOtherPayment, PaymentModeMaster> mode = root.join("paymentMode", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();

        // Exclude other-payments belonging to cancelled admissions from the
        // fees-overview ledger, matching the main student / payment views.
        predicates.add(cb.or(
                cb.isNull(admission.get("status")),
                cb.notEqual(admission.get("status"), com.bothash.admissionservice.enumpackage.AdmissionStatus.CANCELLED)
        ));
        predicates.add(cb.or(
                cb.isNull(admission.get("temporaryAdmission")),
                cb.equal(admission.get("temporaryAdmission"), Boolean.FALSE)
        ));

        Predicate searchPredicate = buildTokenizedSearchPredicate(cb, q,
                student.get("fullName"),
                student.get("absId"),
                student.get("mobile"),
                root.get("txnRef"));
        if (searchPredicate != null) {
            predicates.add(searchPredicate);
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
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("paidOn"), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("paidOn"), endDate));
        }
        if (paymentModes != null && !paymentModes.isEmpty()) {
            predicates.add(mode.get("code").in(paymentModes));
        }
        if (paymentTypes != null && !paymentTypes.isEmpty()) {
            List<String> lowerTypes = paymentTypes.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toList();
            if (!lowerTypes.isEmpty()) {
                predicates.add(cb.lower(root.get("paymentType")).in(lowerTypes));
            }
        }
        if (Boolean.TRUE.equals(branchApprovedOnly)) {
            predicates.add(cb.equal(admission.get("branchApproved"), Boolean.TRUE));
        }

        applyTelecallerScope(cq, cb, admission, predicates);

        cq.select(root).where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.desc(root.get("paidOn")), cb.desc(root.get("paymentId")));

        List<AdmissionOtherPayment> rows = entityManager.createQuery(cq)
                .setMaxResults(2000)
                .getResultList();

        List<LedgerOtherPaymentRowDto> result = new ArrayList<>(rows.size());
        for (AdmissionOtherPayment p : rows) {
            result.add(toOtherPaymentRow(p));
        }
        return result;
    }

    private LedgerOtherPaymentRowDto toOtherPaymentRow(AdmissionOtherPayment p) {
        LedgerOtherPaymentRowDto dto = new LedgerOtherPaymentRowDto();
        Admission2 ad = p.getAdmission();
        Student st = ad != null ? ad.getStudent() : null;
        BranchMaster br = ad != null ? ad.getLectureBranch() : null;
        Course co = ad != null ? ad.getCourse() : null;
        AcademicYear yr = ad != null ? ad.getYear() : null;

        dto.setPaymentId(p.getPaymentId());
        dto.setAdmissionId(ad != null ? ad.getAdmissionId() : null);
        dto.setStudentId(st != null ? st.getStudentId() : null);
        dto.setStudentName(st != null ? st.getFullName() : null);
        dto.setAbsId(st != null ? st.getAbsId() : null);
        dto.setMobile(st != null ? st.getMobile() : null);
        dto.setBranchId(br != null ? br.getId() : null);
        dto.setBranchName(br != null ? br.getName() : null);
        dto.setCourseId(co != null ? co.getCourseId() : null);
        dto.setCourseName(co != null ? co.getName() : null);
        dto.setBatch(ad != null ? ad.getBatch() : null);
        dto.setAcademicYear(yr != null ? yr.getLabel() : null);

        BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
        BigDecimal returned = p.getReturnedAmount() != null ? p.getReturnedAmount() : BigDecimal.ZERO;
        dto.setAmount(amount);
        dto.setReturnedAmount(returned);
        dto.setNetAmount(amount.subtract(returned));
        dto.setPaidOn(p.getPaidOn());
        dto.setPaymentMode(p.getPaymentMode() != null ? p.getPaymentMode().getCode() : null);
        dto.setPaymentType(p.getPaymentType());
        dto.setTxnRef(p.getTxnRef());
        dto.setCategory(p.getCategory());
        dto.setRemarks(p.getRemarks());
        dto.setReceivedBy(p.getReceivedBy());
        dto.setReferencePaymentId(p.getReferencePayment() != null ? p.getReferencePayment().getPaymentId() : null);
        dto.setReceiptName(p.getReceiptName());
        dto.setReceiptUrl(p.getReceiptStorageUrl());
        dto.setInvoiceNumber(p.getInvoiceNumber());
        dto.setInvoiceUrl(p.getInvoiceDownloadUrl());
        dto.setAccountHeadVerified(p.getIsAccountHeadVerified());
        dto.setAccountHeadVerifiedBy(p.getAccountHeadVerifiedBy());
        dto.setAccountHeadVerifiedAt(p.getAccountHeadVerifiedAt());
        return dto;
    }

    private FeeLedgerRowDto buildStudentRow(
            List<FeeInstallment> installments,
            Map<Long, List<FeeInstallmentPayment>> paymentsByInstallment,
            Map<Long, Long> scheduleCountsByStudent
    ) {
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
        BranchMaster branch = admission.getLectureBranch();

        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        LocalDate nextDueDate = null;
        BigDecimal nextDueAmount = BigDecimal.ZERO;

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        statusCounts.put("Pending", 0);
        statusCounts.put("Under Verification", 0);
        statusCounts.put("Partial Received", 0);
        statusCounts.put("Paid", 0);
        statusCounts.put("Rejected", 0);
        statusCounts.put("Cancelled", 0);

        for (FeeInstallment inst : installments) {
            BigDecimal due = inst.getAmountDue() != null ? inst.getAmountDue() : BigDecimal.ZERO;
            BigDecimal paid = inst.getAmountPaid() != null ? inst.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal pending = due.subtract(paid);
            List<FeeInstallmentPayment> installmentPayments = paymentsByInstallment.getOrDefault(
                    inst.getInstallmentId(),
                    List.of()
            );

            totalDue = totalDue.add(due);
            totalPaid = totalPaid.add(paid);

            long rejectedPayments = installmentPayments.stream()
                    .filter(this::isRejectedPayment)
                    .count();
            long underVerificationPayments = installmentPayments.stream()
                    .filter(this::isUnderVerificationPayment)
                    .count();

            if (rejectedPayments > 0) {
                statusCounts.computeIfPresent("Rejected", (k, v) -> v + Math.toIntExact(rejectedPayments));
            }
            if (underVerificationPayments > 0) {
                statusCounts.computeIfPresent("Under Verification", (k, v) -> v + Math.toIntExact(underVerificationPayments));
            }

            if (rejectedPayments == 0 && underVerificationPayments == 0) {
                String computedStatus = computeStatus(inst.getStatus(), due, paid);
                statusCounts.computeIfPresent(computedStatus, (k, v) -> v + 1);
            } else if (pending.compareTo(BigDecimal.ZERO) > 0) {
                String pendingStatus = paid.compareTo(BigDecimal.ZERO) > 0 ? "Partial Received" : "Pending";
                statusCounts.computeIfPresent(pendingStatus, (k, v) -> v + 1);
            }

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
        Long studentId = student != null ? student.getStudentId() : null;
        long scheduleCount = (studentId != null && scheduleCountsByStudent != null)
                ? scheduleCountsByStudent.getOrDefault(studentId, 0L)
                : 0L;

        return FeeLedgerRowDto.builder()
                .admissionId(admission.getAdmissionId())
                .studentId(studentId)
                .studentName(student != null ? student.getFullName() : null)
                .absId(student != null ? student.getAbsId() : null)
                .mobile(student != null ? student.getMobile() : null)
                .fatherMobile(resolveGuardianMobile(student, GuardianRelation.Father))
                .motherMobile(resolveGuardianMobile(student, GuardianRelation.Mother))
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
                .hasSchedule(scheduleCount > 0)
                .scheduleCount(scheduleCount)
                .temporaryAdmission(admission.getTemporaryAdmission())
                .build();
    }

    private Map<Long, List<FeeInstallmentPayment>> fetchPaymentsByInstallment(List<FeeInstallment> installments) {
        if (installments == null || installments.isEmpty()) {
            return Map.of();
        }
        List<Long> installmentIds = installments.stream()
                .map(FeeInstallment::getInstallmentId)
                .filter(Objects::nonNull)
                .toList();
        if (installmentIds.isEmpty()) {
            return Map.of();
        }
        return paymentRepo.findByInstallment_InstallmentIdInOrderByCreatedAtAscPaymentIdAsc(installmentIds).stream()
                .filter(payment -> payment.getInstallment() != null && payment.getInstallment().getInstallmentId() != null)
                .collect(Collectors.groupingBy(payment -> payment.getInstallment().getInstallmentId()));
    }

    private String resolveGuardianMobile(Student student, GuardianRelation relation) {
        if (student == null || student.getGuardians() == null || relation == null) {
            return null;
        }
        return student.getGuardians().stream()
                .filter(Objects::nonNull)
                .filter(guardian -> relation.equals(guardian.getRelation()))
                .map(Guardian::getMobile)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private Map<Long, Long> fetchScheduleCounts(List<Long> admissionIds, Map<Long, List<FeeInstallment>> byAdmission) {
        if (admissionIds == null || admissionIds.isEmpty() || byAdmission == null || byAdmission.isEmpty()) {
            return Map.of();
        }

        List<Long> studentIds = admissionIds.stream()
                .map(byAdmission::get)
                .filter(Objects::nonNull)
                .map(list -> list.isEmpty() ? null : list.get(0))
                .filter(Objects::nonNull)
                .map(FeeInstallment::getAdmission)
                .filter(Objects::nonNull)
                .map(Admission2::getStudent)
                .filter(Objects::nonNull)
                .map(Student::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (studentIds.isEmpty()) {
            return Map.of();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<StudentFeeSchedule> root = cq.from(StudentFeeSchedule.class);
        cq.multiselect(
                root.get("student").get("studentId"),
                cb.count(root.get("scheduleId"))
        );
        cq.where(root.get("student").get("studentId").in(studentIds));
        cq.groupBy(root.get("student").get("studentId"));

        return entityManager.createQuery(cq).getResultList().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
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
            List<String> paymentTypes,
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
        Join<Admission2, BranchMaster> lectureBranch = admission.join("lectureBranch", JoinType.LEFT);
        Join<Admission2, BranchMaster> admissionBranch = admission.join("admissionBranch", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(
                cq, cb, root, admission, student, course, year, lectureBranch, admissionBranch,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                false
        );

        applyAdmissionPaymentFilters(cq, cb, admission, predicates, paymentModes, paymentTypes, verification, proofAttached, txnPresent);

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
        Join<Admission2, BranchMaster> countLectureBranch = countAdmission.join("lectureBranch", JoinType.LEFT);
        Join<Admission2, BranchMaster> countAdmissionBranch = countAdmission.join("admissionBranch", JoinType.LEFT);

        List<Predicate> countPredicates = buildPredicates(
                countCq, cb, countRoot, countAdmission, countStudent, countCourse, countYear, countLectureBranch, countAdmissionBranch,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                false
        );
        applyAdmissionPaymentFilters(countCq, cb, countAdmission, countPredicates, paymentModes, paymentTypes, verification, proofAttached, txnPresent);
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
    private record PaymentGroupDescriptor(Long admissionId, String resolvedGroupKey) {}
    private record PaymentGroupPage(List<PaymentGroupDescriptor> groups, long totalElements, int totalPages) {}

    private PaymentGroupPage queryPaymentGroupPage(
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
            List<String> paymentTypes,
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
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<FeeInstallmentPayment> paymentRoot = cq.from(FeeInstallmentPayment.class);
        Join<FeeInstallmentPayment, FeeInstallment> installment = paymentRoot.join("installment", JoinType.INNER);
        Join<FeeInstallment, Admission2> admission = installment.join("admission", JoinType.LEFT);
        Join<Admission2, Student> student = admission.join("student", JoinType.LEFT);
        Join<Admission2, Course> course = admission.join("course", JoinType.LEFT);
        Join<Admission2, AcademicYear> year = admission.join("year", JoinType.LEFT);
        Join<Admission2, BranchMaster> lectureBranch = admission.join("lectureBranch", JoinType.LEFT);
        Join<Admission2, BranchMaster> admissionBranch = admission.join("admissionBranch", JoinType.LEFT);
        Join<FeeInstallmentPayment, PaymentModeMaster> paymentMode = paymentRoot.join("paymentMode", JoinType.LEFT);

        List<Predicate> predicates = buildPaymentPredicates(
                cq, cb, paymentRoot, installment, admission, student, course, year, lectureBranch, admissionBranch,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                false
        );
        applyPaymentRecordFilters(cq, cb, predicates, paymentRoot, paymentModes, paymentTypes, verification, proofAttached, txnPresent);

        Expression<String> resolvedGroupKey = buildResolvedGroupKeyExpression(cb, paymentRoot, paymentMode);
        Expression<LocalDate> sortDate = cb.function(
                "max",
                LocalDate.class,
                cb.coalesce(paymentRoot.get("paidOn"), installment.get("dueDate"))
        );
        Expression<Long> maxPaymentId = cb.max(paymentRoot.get("paymentId"));

        cq.multiselect(admission.get("admissionId"), resolvedGroupKey, sortDate, maxPaymentId);
        cq.where(predicates.toArray(new Predicate[0]));
        cq.groupBy(admission.get("admissionId"), resolvedGroupKey);
        cq.orderBy(cb.desc(sortDate), cb.desc(maxPaymentId));

        TypedQuery<Object[]> query = entityManager.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<PaymentGroupDescriptor> groups = query.getResultList().stream()
                .map(row -> new PaymentGroupDescriptor((Long) row[0], (String) row[1]))
                .toList();

        CriteriaQuery<Object[]> countCq = cb.createQuery(Object[].class);
        Root<FeeInstallmentPayment> countPaymentRoot = countCq.from(FeeInstallmentPayment.class);
        Join<FeeInstallmentPayment, FeeInstallment> countInstallment = countPaymentRoot.join("installment", JoinType.INNER);
        Join<FeeInstallment, Admission2> countAdmission = countInstallment.join("admission", JoinType.LEFT);
        Join<Admission2, Student> countStudent = countAdmission.join("student", JoinType.LEFT);
        Join<Admission2, Course> countCourse = countAdmission.join("course", JoinType.LEFT);
        Join<Admission2, AcademicYear> countYear = countAdmission.join("year", JoinType.LEFT);
        Join<Admission2, BranchMaster> countLectureBranch = countAdmission.join("lectureBranch", JoinType.LEFT);
        Join<Admission2, BranchMaster> countAdmissionBranch = countAdmission.join("admissionBranch", JoinType.LEFT);
        Join<FeeInstallmentPayment, PaymentModeMaster> countPaymentMode = countPaymentRoot.join("paymentMode", JoinType.LEFT);

        List<Predicate> countPredicates = buildPaymentPredicates(
                countCq, cb, countPaymentRoot, countInstallment, countAdmission, countStudent, countCourse, countYear, countLectureBranch, countAdmissionBranch,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                false
        );
        applyPaymentRecordFilters(countCq, cb, countPredicates, countPaymentRoot, paymentModes, paymentTypes, verification, proofAttached, txnPresent);
        Expression<String> countResolvedGroupKey = buildResolvedGroupKeyExpression(cb, countPaymentRoot, countPaymentMode);
        countCq.multiselect(countAdmission.get("admissionId"), countResolvedGroupKey);
        countCq.where(countPredicates.toArray(new Predicate[0]));
        countCq.groupBy(countAdmission.get("admissionId"), countResolvedGroupKey);

        long total = entityManager.createQuery(countCq).getResultList().size();
        int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) total / pageable.getPageSize());
        return new PaymentGroupPage(groups, total, totalPages);
    }

    private List<FeeInstallmentPayment> fetchPaymentsForGroupPage(List<PaymentGroupDescriptor> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<FeeInstallmentPayment> cq = cb.createQuery(FeeInstallmentPayment.class);
        Root<FeeInstallmentPayment> paymentRoot = cq.from(FeeInstallmentPayment.class);
        paymentRoot.fetch("paymentMode", JoinType.LEFT);
        var installmentFetch = paymentRoot.fetch("installment", JoinType.INNER);
        var admissionFetch = installmentFetch.fetch("admission", JoinType.LEFT);
        admissionFetch.fetch("student", JoinType.LEFT);
        admissionFetch.fetch("course", JoinType.LEFT);
        admissionFetch.fetch("year", JoinType.LEFT);
        admissionFetch.fetch("lectureBranch", JoinType.LEFT);
        admissionFetch.fetch("admissionBranch", JoinType.LEFT);

        Join<FeeInstallmentPayment, FeeInstallment> installment = paymentRoot.join("installment", JoinType.INNER);
        Join<FeeInstallment, Admission2> admission = installment.join("admission", JoinType.LEFT);
        Join<FeeInstallmentPayment, PaymentModeMaster> paymentMode = paymentRoot.join("paymentMode", JoinType.LEFT);
        Expression<String> resolvedGroupKey = buildResolvedGroupKeyExpression(cb, paymentRoot, paymentMode);

        List<Predicate> groupPredicates = new ArrayList<>();
        for (PaymentGroupDescriptor group : groups) {
            if (group == null || group.admissionId() == null || !StringUtils.hasText(group.resolvedGroupKey())) {
                continue;
            }
            groupPredicates.add(cb.and(
                    cb.equal(admission.get("admissionId"), group.admissionId()),
                    cb.equal(resolvedGroupKey, group.resolvedGroupKey())
            ));
        }
        if (groupPredicates.isEmpty()) {
            return List.of();
        }

        cq.select(paymentRoot).distinct(true);
        cq.where(cb.or(groupPredicates.toArray(new Predicate[0])));
        return entityManager.createQuery(cq).getResultList();
    }

    private Expression<String> buildResolvedGroupKeyExpression(
            CriteriaBuilder cb,
            Root<FeeInstallmentPayment> paymentRoot,
            Join<FeeInstallmentPayment, PaymentModeMaster> paymentMode
    ) {
        Expression<String> paymentGroupId = paymentRoot.get("paymentGroupId");
        Expression<String> normalizedGroupId = cb.trim(cb.coalesce(paymentGroupId, ""));
        Predicate hasGroupId = cb.notEqual(normalizedGroupId, "");
        Expression<String> sign = cb.<String>selectCase()
                .when(cb.lessThan(paymentRoot.get("amount"), BigDecimal.ZERO), "NEG")
                .otherwise("POS");
        Expression<String> paidOn = cb.coalesce(paymentRoot.get("paidOn").as(String.class), "");
        Expression<String> mode = cb.upper(cb.trim(cb.coalesce(paymentMode.get("code"), "")));
        Expression<String> txnRef = cb.upper(cb.trim(cb.coalesce(paymentRoot.get("txnRef"), "")));
        Expression<String> receivedBy = cb.upper(cb.trim(cb.coalesce(paymentRoot.get("receivedBy"), "")));
        Expression<String> createdSecond = cb.coalesce(
                cb.function("DATE_FORMAT", String.class, paymentRoot.get("createdAt"), cb.literal("%Y-%m-%dT%H:%i:%s")),
                cb.literal("NO_CREATED_AT")
        );
        Expression<String> legacyKey = concatWithPipes(cb, sign, paidOn, mode, txnRef, receivedBy, createdSecond);
        return cb.<String>selectCase()
                .when(hasGroupId, normalizedGroupId)
                .otherwise(legacyKey);
    }

    private Expression<String> concatWithPipes(CriteriaBuilder cb, Expression<String>... parts) {
        if (parts == null || parts.length == 0) {
            return cb.literal("");
        }
        Expression<String> result = parts[0];
        for (int index = 1; index < parts.length; index++) {
            result = cb.concat(cb.concat(result, "|"), parts[index]);
        }
        return result;
    }

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
            List<String> paymentTypes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly
    ) {
        Object[] admissionOnly = runSummaryQuery(
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                true
        );
        Object[] combined = runSummaryQuery(
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                false
        );
        BigDecimal totalCollected = asBigDecimal(admissionOnly[1]);
        if (hasDateRange(startDate, endDate) && "PAID".equalsIgnoreCase(dateType)) {
            totalCollected = queryCollectedAmount(
                    q, branchIds, courseIds, batch, batchCodes, academicYearId,
                    startDate, endDate, dateType, statusList, dueStatus,
                    paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                    paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                    true
            );
        }

        return FeeLedgerSummaryDto.builder()
                .totalFeeAmount(asBigDecimal(admissionOnly[0]))
                .totalCollected(totalCollected)
                .totalPending(asBigDecimal(admissionOnly[2]))
                .overdueAmount(asBigDecimal(admissionOnly[3]))
                .dueNext7DaysAmount(asBigDecimal(combined[4]))
                .underVerificationCount(asLong(combined[5]))
                .underVerificationStudentCount(asLong(combined[6]))
                .build();
    }

    private Object[] runSummaryQuery(
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
            List<String> paymentTypes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly,
            boolean admissionBranchOnly
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<FeeInstallment> root = cq.from(FeeInstallment.class);

        Join<FeeInstallment, Admission2> admission = root.join("admission", JoinType.LEFT);
        Join<Admission2, Student> student = admission.join("student", JoinType.LEFT);
        Join<Admission2, Course> course = admission.join("course", JoinType.LEFT);
        Join<Admission2, AcademicYear> year = admission.join("year", JoinType.LEFT);
        Join<Admission2, BranchMaster> lectureBranch = admission.join("lectureBranch", JoinType.LEFT);
        Join<Admission2, BranchMaster> admissionBranch = admission.join("admissionBranch", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(
                cq, cb, root, admission, student, course, year, lectureBranch, admissionBranch,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                admissionBranchOnly
        );
        applyAdmissionPaymentFilters(cq, cb, admission, predicates, paymentModes, paymentTypes, verification, proofAttached, txnPresent);

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

        return entityManager.createQuery(cq).getSingleResult();
    }

    private BigDecimal queryCollectedAmount(
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
            List<String> paymentTypes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly,
            boolean admissionBranchOnly
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<FeeInstallmentPayment> paymentRoot = cq.from(FeeInstallmentPayment.class);

        Join<FeeInstallmentPayment, FeeInstallment> installment = paymentRoot.join("installment", JoinType.INNER);
        Join<FeeInstallment, Admission2> admission = installment.join("admission", JoinType.LEFT);
        Join<Admission2, Student> student = admission.join("student", JoinType.LEFT);
        Join<Admission2, Course> course = admission.join("course", JoinType.LEFT);
        Join<Admission2, AcademicYear> year = admission.join("year", JoinType.LEFT);
        Join<Admission2, BranchMaster> lectureBranch = admission.join("lectureBranch", JoinType.LEFT);
        Join<Admission2, BranchMaster> admissionBranch = admission.join("admissionBranch", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(
                cq, cb, installment, admission, student, course, year, lectureBranch, admissionBranch,
                q, branchIds, courseIds, batch, batchCodes, academicYearId,
                startDate, endDate, dateType, statusList, dueStatus,
                paymentModes, paymentTypes, verification, proofAttached, txnPresent,
                paidAmountOp, paidAmount, pendingMin, pendingMax, branchApprovedOnly,
                admissionBranchOnly
        );
        applyPaymentRecordFilters(cq, cb, predicates, paymentRoot, paymentModes, paymentTypes, verification, proofAttached, txnPresent);
        applyLocalDateRange(cb, predicates, paymentRoot.get("paidOn"), startDate, endDate);

        cq.select(cb.coalesce(cb.sum(cb.coalesce(paymentRoot.get("amount"), BigDecimal.ZERO)), BigDecimal.ZERO));
        cq.where(predicates.toArray(new Predicate[0]));
        return entityManager.createQuery(cq).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            From<?, FeeInstallment> root,
            Join<FeeInstallment, Admission2> admission,
            Join<Admission2, Student> student,
            Join<Admission2, Course> course,
            Join<Admission2, AcademicYear> year,
            Join<Admission2, BranchMaster> lectureBranch,
            Join<Admission2, BranchMaster> admissionBranch,
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
            List<String> paymentTypes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly,
            boolean admissionBranchOnly
    ) {
        List<Predicate> predicates = new ArrayList<>();

        // Cancelled admissions must never show up in the fees overview (rows,
        // summary cards, other-payment totals, everything). They still exist
        // in admission-list, but their fees are off the finance board.
        predicates.add(cb.or(
                cb.isNull(admission.get("status")),
                cb.notEqual(admission.get("status"), com.bothash.admissionservice.enumpackage.AdmissionStatus.CANCELLED)
        ));
        // Temporary admissions are hidden from the fees overview until confirmed.
        predicates.add(cb.or(
                cb.isNull(admission.get("temporaryAdmission")),
                cb.equal(admission.get("temporaryAdmission"), Boolean.FALSE)
        ));

        Predicate searchPredicate = buildTokenizedSearchPredicate(cb, q,
                student.get("fullName"),
                student.get("absId"),
                student.get("mobile"));
        if (searchPredicate != null) {
            predicates.add(searchPredicate);
        }

        if (branchIds != null && !branchIds.isEmpty()) {
            if (admissionBranchOnly) {
                predicates.add(admissionBranch.get("id").in(branchIds));
            } else {
                predicates.add(cb.or(
                        lectureBranch.get("id").in(branchIds),
                        admissionBranch.get("id").in(branchIds)
                ));
            }
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
        } else if ("SCHEDULE".equalsIgnoreCase(dateType)) {
            applyScheduleDateFilter(query, cb, student, predicates, startDate, endDate);
        } else {
            applyDateFilter(cb, root, predicates, startDate, endDate, dateType);
        }

        applyStatusFilter(cb, root, predicates, statusList);
        applyDueStatusFilter(cb, root, predicates, dueStatus);

        // Note: Payment mode, verification, proof attached, and txn filters are now handled
        // via subquery in the search method to check FeeInstallmentPayment table instead of FeeInstallment

        applyPaidAmountFilter(query, cb, admission, predicates, paidAmountOp, paidAmount);
        applyPendingRangeFilter(query, cb, admission, predicates, pendingMin, pendingMax);
        applyTelecallerScope(query, cb, admission, predicates);

        return predicates;
    }

    private void applyDateFilter(CriteriaBuilder cb, From<?, FeeInstallment> root, List<Predicate> predicates,
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

    private List<Predicate> buildPaymentPredicates(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<FeeInstallmentPayment> paymentRoot,
            Join<FeeInstallmentPayment, FeeInstallment> installment,
            Join<FeeInstallment, Admission2> admission,
            Join<Admission2, Student> student,
            Join<Admission2, Course> course,
            Join<Admission2, AcademicYear> year,
            Join<Admission2, BranchMaster> lectureBranch,
            Join<Admission2, BranchMaster> admissionBranch,
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
            List<String> paymentTypes,
            String verification,
            String proofAttached,
            String txnPresent,
            String paidAmountOp,
            BigDecimal paidAmount,
            BigDecimal pendingMin,
            BigDecimal pendingMax,
            Boolean branchApprovedOnly,
            boolean admissionBranchOnly
    ) {
        List<Predicate> predicates = new ArrayList<>();

        // Payments view mirrors the student view: drop payments belonging to
        // cancelled admissions from both rows and summary aggregates.
        predicates.add(cb.or(
                cb.isNull(admission.get("status")),
                cb.notEqual(admission.get("status"), com.bothash.admissionservice.enumpackage.AdmissionStatus.CANCELLED)
        ));
        predicates.add(cb.or(
                cb.isNull(admission.get("temporaryAdmission")),
                cb.equal(admission.get("temporaryAdmission"), Boolean.FALSE)
        ));

        Predicate searchPredicate = buildTokenizedSearchPredicate(cb, q,
                student.get("fullName"),
                student.get("absId"),
                student.get("mobile"),
                paymentRoot.get("txnRef"));
        if (searchPredicate != null) {
            predicates.add(searchPredicate);
        }

        if (branchIds != null && !branchIds.isEmpty()) {
            if (admissionBranchOnly) {
                predicates.add(admissionBranch.get("id").in(branchIds));
            } else {
                predicates.add(cb.or(
                        lectureBranch.get("id").in(branchIds),
                        admissionBranch.get("id").in(branchIds)
                ));
            }
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

        String normalizedDateType = dateType == null ? "DUE" : dateType.toUpperCase();
        if ("PAID".equals(normalizedDateType)) {
            applyLocalDateRange(cb, predicates, paymentRoot.get("paidOn"), startDate, endDate);
        } else if ("CREATED".equals(normalizedDateType)) {
            applyOffsetDateRange(cb, predicates, paymentRoot.get("createdAt"), startDate, endDate);
        } else if ("SCHEDULE".equals(normalizedDateType)) {
            applyScheduleDateFilter(query, cb, student, predicates, startDate, endDate);
        } else {
            applyLocalDateRange(cb, predicates, installment.get("dueDate"), startDate, endDate);
        }

        applyStatusFilter(cb, installment, predicates, statusList);
        applyDueStatusFilter(cb, installment, predicates, dueStatus);
        applyPaidAmountFilter(query, cb, admission, predicates, paidAmountOp, paidAmount);
        applyPendingRangeFilter(query, cb, admission, predicates, pendingMin, pendingMax);
        applyTelecallerScope(query, cb, admission, predicates);

        return predicates;
    }

    private void applyOffsetDateRange(
            CriteriaBuilder cb,
            List<Predicate> predicates,
            Expression<OffsetDateTime> field,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null && endDate == null) {
            return;
        }
        OffsetDateTime start = startDate != null
                ? OffsetDateTime.of(startDate, LocalTime.MIN, ZoneOffset.UTC)
                : null;
        OffsetDateTime end = endDate != null
                ? OffsetDateTime.of(endDate.plusDays(1), LocalTime.MIN, ZoneOffset.UTC)
                : null;

        if (start != null && end != null) {
            predicates.add(cb.between(field, start, end));
        } else if (start != null) {
            predicates.add(cb.greaterThanOrEqualTo(field, start));
        } else if (end != null) {
            predicates.add(cb.lessThan(field, end));
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

        // Also include admissions whose other-payments (non-installment, e.g. library/hostel) fall in range.
        Subquery<Long> otherSubquery = cq.subquery(Long.class);
        Root<AdmissionOtherPayment> otherRoot = otherSubquery.from(AdmissionOtherPayment.class);
        Join<AdmissionOtherPayment, Admission2> otherAdmission = otherRoot.join("admission", JoinType.INNER);

        List<Predicate> otherPredicates = new ArrayList<>();
        applyLocalDateRange(cb, otherPredicates, otherRoot.get("paidOn"), startDate, endDate);

        otherSubquery.select(otherAdmission.get("admissionId"));
        otherSubquery.where(otherPredicates.toArray(new Predicate[0]));

        predicates.add(cb.or(
                admission.get("admissionId").in(paymentSubquery),
                admission.get("admissionId").in(otherSubquery)
        ));
    }

    private void applyScheduleDateFilter(CriteriaQuery<?> cq, CriteriaBuilder cb,
                                         Join<Admission2, Student> student,
                                         List<Predicate> predicates,
                                         LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return;
        }

        Subquery<Long> scheduleSubquery = cq.subquery(Long.class);
        Root<StudentFeeSchedule> scheduleRoot = scheduleSubquery.from(StudentFeeSchedule.class);
        List<Predicate> schedulePredicates = new ArrayList<>();
        schedulePredicates.add(cb.equal(scheduleRoot.get("student").get("studentId"), student.get("studentId")));
        applyLocalDateRange(cb, schedulePredicates, scheduleRoot.get("scheduledDate"), startDate, endDate);
        scheduleSubquery.select(scheduleRoot.get("student").get("studentId"));
        scheduleSubquery.where(schedulePredicates.toArray(new Predicate[0]));

        predicates.add(cb.exists(scheduleSubquery));
    }

    /**
     * Builds a multi-word search predicate against any number of string
     * columns. The query is split on whitespace; each token must match at
     * least one of the supplied fields (LIKE %token%), and all tokens must
     * match (AND across tokens). This lets searches like "Mahesh Myana" find
     * "MYANA MAHESH KUMAR" and lets the user mix terms across fields, e.g.
     * a name word + a phone digit group.
     *
     * Returns null when the query is blank or no usable tokens are present,
     * so callers can skip adding it without an empty predicate.
     */
    @SafeVarargs
    private final Predicate buildTokenizedSearchPredicate(CriteriaBuilder cb,
                                                          String q,
                                                          Expression<String>... fields) {
        if (!StringUtils.hasText(q) || fields == null || fields.length == 0) {
            return null;
        }
        String[] tokens = q.trim().toLowerCase().split("\\s+");
        List<Predicate> tokenPredicates = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            String like = "%" + token + "%";
            List<Predicate> fieldMatches = new ArrayList<>(fields.length);
            for (Expression<String> field : fields) {
                fieldMatches.add(cb.like(cb.lower(field), like));
            }
            tokenPredicates.add(cb.or(fieldMatches.toArray(new Predicate[0])));
        }
        if (tokenPredicates.isEmpty()) {
            return null;
        }
        return cb.and(tokenPredicates.toArray(new Predicate[0]));
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

    private void applyStatusFilter(CriteriaBuilder cb, From<?, FeeInstallment> root,
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

    private void applyDueStatusFilter(CriteriaBuilder cb, From<?, FeeInstallment> root,
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

    private void applyAdmissionPaymentFilters(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Join<FeeInstallment, Admission2> admission,
            List<Predicate> predicates,
            List<String> paymentModes,
            List<String> paymentTypes,
            String verification,
            String proofAttached,
            String txnPresent
    ) {
        if (!hasPaymentRecordFilters(paymentModes, paymentTypes, verification, proofAttached, txnPresent)) {
            return;
        }
        Subquery<Long> paymentSubquery = query.subquery(Long.class);
        Root<FeeInstallmentPayment> paymentRoot = paymentSubquery.from(FeeInstallmentPayment.class);
        Join<FeeInstallmentPayment, FeeInstallment> paymentInstallment = paymentRoot.join("installment", JoinType.INNER);
        Join<FeeInstallment, Admission2> paymentAdmission = paymentInstallment.join("admission", JoinType.INNER);

        List<Predicate> paymentPredicates = new ArrayList<>();
        applyPaymentRecordFilters(paymentSubquery, cb, paymentPredicates, paymentRoot, paymentModes, paymentTypes, verification, proofAttached, txnPresent);

        paymentSubquery.select(paymentAdmission.get("admissionId"));
        paymentSubquery.where(paymentPredicates.toArray(new Predicate[0]));
        predicates.add(admission.get("admissionId").in(paymentSubquery));
    }

    private void applyPaymentRecordFilters(
            CommonAbstractCriteria query,
            CriteriaBuilder cb,
            List<Predicate> predicates,
            Root<FeeInstallmentPayment> paymentRoot,
            List<String> paymentModes,
            List<String> paymentTypes,
            String verification,
            String proofAttached,
            String txnPresent
    ) {
        if (paymentModes != null && !paymentModes.isEmpty()) {
            List<String> lower = paymentModes.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toList();
            if (!lower.isEmpty()) {
                Join<FeeInstallmentPayment, PaymentModeMaster> feePaymentMode = paymentRoot.join("paymentMode", JoinType.INNER);
                predicates.add(cb.lower(feePaymentMode.get("code")).in(lower));
            }
        }

        if (paymentTypes != null && !paymentTypes.isEmpty()) {
            List<String> lowerTypes = paymentTypes.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toList();
            if (!lowerTypes.isEmpty()) {
                predicates.add(cb.lower(paymentRoot.get("paymentType")).in(lowerTypes));
            }
        }

        if (StringUtils.hasText(verification)) {
            String value = verification.trim().toUpperCase();
            switch (value) {
                case "VERIFIED" -> predicates.add(cb.isTrue(paymentRoot.get("isVerified")));
                case "NOT_VERIFIED" -> predicates.add(cb.or(
                        cb.isFalse(paymentRoot.get("isVerified")),
                        cb.isNull(paymentRoot.get("isVerified"))
                ));
                default -> {
                }
            }
        }

        if (StringUtils.hasText(proofAttached)) {
            String value = proofAttached.trim().toUpperCase();
            Subquery<Long> fileSubquery = query.subquery(Long.class);
            Root<FileUpload> fileRoot = fileSubquery.from(FileUpload.class);
            fileSubquery.select(cb.literal(1L));
            fileSubquery.where(cb.equal(fileRoot.get("installmentPayment").get("paymentId"), paymentRoot.get("paymentId")));

            if ("YES".equals(value)) {
                predicates.add(cb.exists(fileSubquery));
            } else if ("NO".equals(value)) {
                predicates.add(cb.not(cb.exists(fileSubquery)));
            }
        }

        if (StringUtils.hasText(txnPresent)) {
            String value = txnPresent.trim().toUpperCase();
            if ("YES".equals(value)) {
                predicates.add(cb.and(
                        cb.isNotNull(paymentRoot.get("txnRef")),
                        cb.notEqual(paymentRoot.get("txnRef"), "")
                ));
            } else if ("NO".equals(value)) {
                predicates.add(cb.or(
                        cb.isNull(paymentRoot.get("txnRef")),
                        cb.equal(paymentRoot.get("txnRef"), "")
                ));
            }
        }
    }

    private boolean hasPaymentRecordFilters(
            List<String> paymentModes,
            List<String> paymentTypes,
            String verification,
            String proofAttached,
            String txnPresent
    ) {
        return (paymentModes != null && !paymentModes.isEmpty())
                || (paymentTypes != null && !paymentTypes.isEmpty())
                || StringUtils.hasText(verification)
                || StringUtils.hasText(proofAttached)
                || StringUtils.hasText(txnPresent);
    }

    private boolean hasDateRange(LocalDate startDate, LocalDate endDate) {
        return startDate != null || endDate != null;
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

    private List<FeePaymentGroupDto> buildLedgerPaymentGroups(List<FeeInstallmentPayment> payments) {
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }
        List<Long> paymentIds = payments.stream()
                .map(FeeInstallmentPayment::getPaymentId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<FileUpload>> uploadsByPayment = paymentIds.isEmpty()
                ? Map.of()
                : uploadRepo.findByInstallmentPayment_PaymentIdIn(paymentIds).stream()
                        .filter(upload -> upload.getInstallmentPayment() != null && upload.getInstallmentPayment().getPaymentId() != null)
                        .collect(Collectors.groupingBy(upload -> upload.getInstallmentPayment().getPaymentId(), LinkedHashMap::new, Collectors.toList()));

        Map<String, List<FeeInstallmentPayment>> grouped = payments.stream()
                .collect(Collectors.groupingBy(this::resolveLedgerPaymentGroupKey, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> toLedgerPaymentGroupDto(entry.getKey(), entry.getValue(), uploadsByPayment))
                .sorted(Comparator.comparing(FeePaymentGroupDto::getPaidOn, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                        .thenComparing(FeePaymentGroupDto::getPaymentGroupId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private String resolveLedgerPaymentGroupKey(FeeInstallmentPayment payment) {
        Long admissionId = payment != null
                && payment.getInstallment() != null
                && payment.getInstallment().getAdmission() != null
                ? payment.getInstallment().getAdmission().getAdmissionId()
                : null;
        String groupId = StringUtils.hasText(payment.getPaymentGroupId())
                ? payment.getPaymentGroupId()
                : buildLedgerLegacyClusterKey(payment);
        return (admissionId != null ? admissionId.toString() : "NO_ADMISSION") + "::" + groupId;
    }

    private String buildLedgerLegacyClusterKey(FeeInstallmentPayment payment) {
        String sign = payment.getAmount() != null && payment.getAmount().compareTo(BigDecimal.ZERO) < 0 ? "NEG" : "POS";
        String paidOn = payment.getPaidOn() != null ? payment.getPaidOn().toString() : "";
        String mode = payment.getPaymentMode() != null && StringUtils.hasText(payment.getPaymentMode().getCode())
                ? payment.getPaymentMode().getCode().trim().toUpperCase()
                : "";
        String txnRef = StringUtils.hasText(payment.getTxnRef()) ? payment.getTxnRef().trim().toUpperCase() : "";
        String receivedBy = StringUtils.hasText(payment.getReceivedBy()) ? payment.getReceivedBy().trim().toUpperCase() : "";
        String createdSecond = payment.getCreatedAt() != null
                ? payment.getCreatedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS).toString()
                : "NO_CREATED_AT";
        return String.join("|", sign, paidOn, mode, txnRef, receivedBy, createdSecond);
    }

    private FeePaymentGroupDto toLedgerPaymentGroupDto(
            String groupKey,
            List<FeeInstallmentPayment> groupPayments,
            Map<Long, List<FileUpload>> uploadsByPayment
    ) {
        groupPayments.sort(Comparator.comparing(FeeInstallmentPayment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FeeInstallmentPayment::getPaymentId, Comparator.nullsLast(Comparator.naturalOrder())));
        FeeInstallmentPayment firstPayment = groupPayments.get(0);
        FeeInstallment firstInstallment = firstPayment.getInstallment();
        Admission2 admission = firstInstallment != null ? firstInstallment.getAdmission() : null;
        Student student = admission != null ? admission.getStudent() : null;
        Course course = admission != null ? admission.getCourse() : null;
        AcademicYear year = admission != null ? admission.getYear() : null;
        BranchMaster branch = admission != null ? admission.getLectureBranch() : null;

        FeePaymentGroupDto dto = new FeePaymentGroupDto();
        dto.setAdmissionId(admission != null ? admission.getAdmissionId() : null);
        dto.setStudentId(student != null ? student.getStudentId() : null);
        dto.setStudentName(student != null ? student.getFullName() : null);
        dto.setAbsId(student != null ? student.getAbsId() : null);
        dto.setMobile(student != null ? student.getMobile() : null);
        dto.setFatherMobile(resolveGuardianMobile(student, GuardianRelation.Father));
        dto.setMotherMobile(resolveGuardianMobile(student, GuardianRelation.Mother));
        dto.setBranchId(branch != null ? branch.getId() : null);
        dto.setBranchName(branch != null ? branch.getName() : null);
        dto.setCourseId(course != null ? course.getCourseId() : null);
        dto.setCourseName(course != null ? course.getName() : null);
        dto.setBatch(admission != null ? admission.getBatch() : null);
        dto.setAcademicYear(year != null ? year.getLabel() : null);
        dto.setPaymentGroupId(StringUtils.hasText(firstPayment.getPaymentGroupId()) ? firstPayment.getPaymentGroupId() : groupKey);
        dto.setPaidOn(resolvePaidOn(groupPayments));
        dto.setTotalAmount(sumPaymentAmounts(groupPayments));
        dto.setPaymentMode(resolvePaymentModeCode(groupPayments));
        dto.setPaymentType(resolvePaymentType(groupPayments));
        dto.setTxnRef(firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getTxnRef).toList()));
        dto.setRemarks(firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getRemarks).toList()));
        dto.setReceivedBy(firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getReceivedBy).toList()));
        boolean verified = groupPayments.stream().allMatch(payment -> Boolean.TRUE.equals(payment.getIsVerified()));
        boolean accountHeadVerified = groupPayments.stream().allMatch(payment -> Boolean.TRUE.equals(payment.getIsAccountHeadVerified()));
        boolean rejected = groupPayments.stream().anyMatch(this::isHoRejectedPayment);
        boolean accountHeadRejected = groupPayments.stream().anyMatch(this::isAccountHeadRejectedPayment);
        dto.setVerified(verified);
        dto.setAccountHeadVerified(accountHeadVerified);
        dto.setAccountHeadRejected(accountHeadRejected);
        dto.setRejectionReason(firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getRejectionReason).toList()));
        dto.setAccountHeadRejectionReason(firstNonBlank(groupPayments.stream().map(FeeInstallmentPayment::getAccountHeadRejectionReason).toList()));
        dto.setStatus(rejected ? "Rejected" : (verified ? "Paid" : "Under Verification"));
        dto.setAllocationCount(groupPayments.size());
        FileUpload receipt = resolveFirstReceipt(groupPayments, uploadsByPayment);
        if (receipt != null) {
            dto.setReceiptUrl(receipt.getStorageUrl());
            dto.setReceiptName(receipt.getFilename());
        }
        FeeInvoice invoice = resolveGroupedInvoice(groupPayments);
        if (invoice != null) {
            dto.setInvoiceNumber(invoice.getInvoiceNumber());
            dto.setInvoiceUrl(invoice.getDownloadUrl());
        }
        return dto;
    }

    private FileUpload resolveFirstReceipt(List<FeeInstallmentPayment> groupPayments, Map<Long, List<FileUpload>> uploadsByPayment) {
        for (FeeInstallmentPayment payment : groupPayments) {
            List<FileUpload> uploads = uploadsByPayment.get(payment.getPaymentId());
            if (uploads != null && !uploads.isEmpty()) {
                return uploads.get(0);
            }
        }
        return null;
    }

    private FeeInvoice resolveGroupedInvoice(List<FeeInstallmentPayment> groupPayments) {
        List<Long> paymentIds = groupPayments.stream()
                .map(FeeInstallmentPayment::getPaymentId)
                .filter(Objects::nonNull)
                .toList();
        if (paymentIds.isEmpty()) {
            return null;
        }
        List<FeeInvoice> invoices = invoiceRepo.findByPayment_PaymentIdIn(paymentIds);
        Comparator<FeeInvoice> invoiceOrder = Comparator
                .comparing(FeeInvoice::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FeeInvoice::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        if (groupPayments.size() == 1) {
            return invoices.stream()
                    .sorted(invoiceOrder)
                    .filter(invoice -> !InvoiceServiceImpl.isPaymentGroupInvoiceNumber(invoice.getInvoiceNumber()))
                    .findFirst()
                    .orElseGet(() -> invoices.stream().sorted(invoiceOrder).findFirst().orElse(null));
        }
        return invoices.stream()
                .sorted(invoiceOrder)
                .filter(invoice -> InvoiceServiceImpl.isPaymentGroupInvoiceNumber(invoice.getInvoiceNumber()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal sumPaymentAmounts(List<FeeInstallmentPayment> payments) {
        return payments.stream()
                .map(FeeInstallmentPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolvePaymentModeCode(List<FeeInstallmentPayment> payments) {
        return payments.stream()
                .map(FeeInstallmentPayment::getPaymentMode)
                .filter(Objects::nonNull)
                .map(PaymentModeMaster::getCode)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String resolvePaymentType(List<FeeInstallmentPayment> payments) {
        return payments.stream()
                .map(FeeInstallmentPayment::getPaymentType)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private LocalDate resolvePaidOn(List<FeeInstallmentPayment> payments) {
        return payments.stream()
                .map(FeeInstallmentPayment::getPaidOn)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String firstNonBlank(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
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

    private boolean isRejectedPayment(FeeInstallmentPayment payment) {
        return isHoRejectedPayment(payment);
    }

    private boolean isHoRejectedPayment(FeeInstallmentPayment payment) {
        if (payment == null) {
            return false;
        }
        String status = payment.getStatus();
        return status != null && status.equalsIgnoreCase("Rejected");
    }

    private boolean isAccountHeadRejectedPayment(FeeInstallmentPayment payment) {
        if (payment == null || Boolean.TRUE.equals(payment.getIsAccountHeadVerified())) {
            return false;
        }
        if (StringUtils.hasText(payment.getAccountHeadRejectionReason())
                && payment.getAccountHeadRejectedAt() != null) {
            return true;
        }
        String status = payment.getStatus();
        if (status != null && status.equalsIgnoreCase("Account Head Rejected")) {
            return true;
        }
        return StringUtils.hasText(payment.getRejectionReason())
                && payment.getRejectedAt() != null
                && !isHoRejectedPayment(payment);
    }

    private boolean isUnderVerificationPayment(FeeInstallmentPayment payment) {
        if (payment == null || isRejectedPayment(payment)) {
            return false;
        }
        String status = payment.getStatus();
        if (status != null && status.equalsIgnoreCase("Under Verification")) {
            return true;
        }
        return !Boolean.TRUE.equals(payment.getIsVerified());
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
