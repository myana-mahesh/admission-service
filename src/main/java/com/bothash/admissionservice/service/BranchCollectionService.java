package com.bothash.admissionservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bothash.admissionservice.dto.BranchCashbookDayDto;
import com.bothash.admissionservice.dto.BranchCashbookDayUpsertRequest;
import com.bothash.admissionservice.dto.BranchCollectionDashboardDto;
import com.bothash.admissionservice.dto.BranchCollectionPaymentItemDto;
import com.bothash.admissionservice.dto.BranchCashbookExpenseDto;
import com.bothash.admissionservice.dto.BranchCashbookExpenseRequest;
import com.bothash.admissionservice.dto.BranchCashbookExpenseUpdateRequest;
import com.bothash.admissionservice.dto.BranchPettyCashAddRequest;
import com.bothash.admissionservice.dto.BranchPettyCashReturnRequest;
import com.bothash.admissionservice.dto.BranchRemittanceDetailDto;
import com.bothash.admissionservice.dto.BranchRemittanceHistoryDto;
import com.bothash.admissionservice.dto.BranchRemittancesGroupDto;
import com.bothash.admissionservice.dto.PagedResponse;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.BranchCashbookDay;
import com.bothash.admissionservice.entity.BranchCashbookExpense;
import com.bothash.admissionservice.entity.BranchMaster;
import com.bothash.admissionservice.entity.BranchCashbookRemittance;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.repository.BranchCashbookDayRepository;
import com.bothash.admissionservice.repository.BranchCashbookExpenseRepository;
import com.bothash.admissionservice.repository.BranchCashbookRemittanceRepository;
import com.bothash.admissionservice.repository.BranchRepository;
import com.bothash.admissionservice.repository.FeeInstallmentPaymentRepository;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Service
@Slf4j
@RequiredArgsConstructor
public class BranchCollectionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final BranchRepository branchRepository;
    private final BranchCashbookDayRepository cashbookDayRepository;
    private final BranchCashbookExpenseRepository expenseRepository;
    private final BranchCashbookRemittanceRepository remittanceRepository;
    private final FeeInstallmentPaymentRepository paymentRepository;
    private final com.bothash.admissionservice.repository.FeePaymentAllocationRepository allocationRepository;

    @Transactional(readOnly = true)
    public BranchCollectionDashboardDto getDaily(Long branchId, LocalDate businessDate) {
        if (branchId == null) {
            throw new IllegalArgumentException("Branch is required.");
        }
        LocalDate date = businessDate != null ? businessDate : LocalDate.now();
        BranchMaster branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));

        CycleContext cycle = loadCycleContext(branchId, date);
        // Dashboard view = all unremitted PLUS payments whose remittance was
        // sent at or after today's start in business tz (so partial-remit rows
        // stay visible with a "Sent" badge until the next day rolls over).
        // Sums below still use only unremitted.
        OffsetDateTime todayStart = LocalDate.now(BUSINESS_ZONE)
                .atStartOfDay(BUSINESS_ZONE)
                .toOffsetDateTime();
        List<FeeInstallmentPayment> collections =
                paymentRepository.findBranchCollectionCandidatesForDashboard(branchId, todayStart);
        List<FeeInstallmentPayment> unremittedCollections = collections.stream()
                .filter(p -> p.getRemittanceId() == null)
                .toList();
        BranchCashbookDay cashbook = cashbookDayRepository.findByBranch_IdAndBusinessDate(branchId, date).orElse(null);
        List<BranchCashbookExpense> expensesList = cycle.expenseEntries;
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal expenses = cycle.cycleExpenseTotal;
        BigDecimal petty = resolveGlobalPettyBalance(branchId, date);
        BigDecimal sent = amountOrZero(cashbook != null ? cashbook.getSentToHoAmount() : null);

        // Gross-original sum across every row visible on the dashboard
        // (remitted + unremitted). Powers the "Student Fees Collected" panel
        // header — distinct from the in-hand math below.
        BigDecimal grossStudentFeesCollected = collections.stream()
                .map(FeeInstallmentPayment::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sums power the KPI tiles. They reflect what's still available in
        // the box: cash uses each fee's REMAINING (amount − consumedAmount)
        // since some of it may already have been allocated to expenses/petty.
        // Cheques have no consumption model, so we use raw amount.
        BigDecimal cashCollected = unremittedCollections.stream()
                .filter(p -> "Cash".equalsIgnoreCase(p.getPaymentType()))
                .map(this::remainingOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal chequeCollected = unremittedCollections.stream()
                .filter(p -> "Cheque".equalsIgnoreCase(p.getPaymentType()) || "Check".equalsIgnoreCase(p.getPaymentType()))
                .map(FeeInstallmentPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // grossCollected = remaining cash + cheque already nets out every
        // petty topup and collection expense via per-fee allocations, so
        // no additional subtraction is needed here.
        BigDecimal grossCollected = cashCollected.add(chequeCollected);
        BigDecimal collectionInHandAfterPetty = grossCollected;
        if (collectionInHandAfterPetty.compareTo(BigDecimal.ZERO) < 0) {
            collectionInHandAfterPetty = BigDecimal.ZERO;
        }
        BigDecimal pendingToSend = collectionInHandAfterPetty;
        BigDecimal closing = grossCollected.subtract(sent);
        if (closing.compareTo(BigDecimal.ZERO) < 0) {
            closing = BigDecimal.ZERO;
        }

        if (log.isInfoEnabled()) {
            log.info(
                    "BranchCollection getDaily branchId={} branchName={} date={} cycleCollections={} cash={} cheque={} gross={} inHand={} petty={} expenses={} pending={} sent={} closing={}",
                    branchId,
                    branch.getName(),
                    date,
                    collections.size(),
                    cashCollected,
                    chequeCollected,
                    grossCollected,
                    collectionInHandAfterPetty,
                    petty,
                    expenses,
                    pendingToSend,
                    sent,
                    closing
            );
        }
        if (log.isDebugEnabled()) {
            collections.forEach(p -> {
                String modeCode = p.getPaymentMode() != null ? p.getPaymentMode().getCode() : null;
                String modeLabel = p.getPaymentMode() != null ? p.getPaymentMode().getLabel() : null;
                Long admissionId = p.getInstallment() != null && p.getInstallment().getAdmission() != null
                        ? p.getInstallment().getAdmission().getAdmissionId()
                        : null;
                log.debug(
                        "BranchCollection included paymentId={} admissionId={} amount={} paymentType={} modeCode={} modeLabel={} status={} paidOn={} createdAt={} groupId={}",
                        p.getPaymentId(),
                        admissionId,
                        p.getAmount(),
                        p.getPaymentType(),
                        modeCode,
                        modeLabel,
                        p.getStatus(),
                        p.getPaidOn(),
                        p.getCreatedAt(),
                        p.getPaymentGroupId()
                );
            });
        }

        List<BranchCollectionPaymentItemDto> items = groupCollectionsForDisplay(collections);

        return BranchCollectionDashboardDto.builder()
                .summary(BranchCashbookDayDto.builder()
                        .branchId(branch.getId())
                        .branchName(branch.getName())
                        .businessDate(date.toString())
                        .openingBalance(opening)
                        .cashCollected(cashCollected)
                        .chequeCollected(chequeCollected)
                        .totalCollected(collectionInHandAfterPetty)
                        .grossStudentFeesCollected(grossStudentFeesCollected)
                        .expensesAmount(expenses)
                        .pettyCashAmount(petty)
                        .sentToHoAmount(sent)
                        .pendingToSendAmount(pendingToSend)
                        .closingBalance(closing)
                        .sentToHoBy(cashbook != null ? cashbook.getSentToHoBy() : null)
                        .sentToHoAt(cashbook != null ? cashbook.getSentToHoAt() : null)
                        .notes(cashbook != null ? cashbook.getNotes() : null)
                        .build())
                .collections(items)
                .expenses(expensesList.stream()
                        .filter(ex -> !"PETTY_TOPUP".equalsIgnoreCase(ex.getSourceType()))
                        .map(this::toExpenseDto)
                        .toList())
                .build();
    }

    @Transactional
    public BranchCashbookDayDto upsertDaily(BranchCashbookDayUpsertRequest request) {
        if (request == null || request.getBranchId() == null) {
            throw new IllegalArgumentException("Branch is required.");
        }
        LocalDate date = request.getBusinessDate() != null ? request.getBusinessDate() : LocalDate.now();
        BranchMaster branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));

        BranchCashbookDay day = cashbookDayRepository.findByBranch_IdAndBusinessDate(branch.getId(), date)
                .orElseGet(() -> {
                    BranchCashbookDay created = new BranchCashbookDay();
                    created.setBranch(branch);
                    created.setBusinessDate(date);
                    created.setOpeningBalance(BigDecimal.ZERO);
                    created.setExpensesAmount(BigDecimal.ZERO);
                    created.setPettyCashAmount(BigDecimal.ZERO);
                    created.setSentToHoAmount(BigDecimal.ZERO);
                    return created;
                });

        BigDecimal petty = amountOrZero(request.getPettyCashAmount());
        if (petty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Petty cash cannot be negative.");
        }

        // New flow: remittance is driven by the explicit list of selected payment
        // IDs. The amount sent = sum of those payments' amounts.
        List<Long> paymentIds = request.getPaymentIds() == null
                ? List.of()
                : request.getPaymentIds().stream().filter(java.util.Objects::nonNull).distinct().toList();

        BigDecimal sent = BigDecimal.ZERO;
        List<FeeInstallmentPayment> selectedPayments = List.of();
        if (!paymentIds.isEmpty()) {
            selectedPayments = paymentRepository.findAllById(paymentIds);
            if (selectedPayments.size() != paymentIds.size()) {
                throw new IllegalArgumentException("One or more selected payments could not be found.");
            }
            for (FeeInstallmentPayment p : selectedPayments) {
                if (p.getRemittanceId() != null) {
                    throw new IllegalArgumentException("One or more selected payments are already remitted.");
                }
                Long ownerBranchId = p.getInstallment() != null
                        && p.getInstallment().getAdmission() != null
                        && p.getInstallment().getAdmission().getAdmissionBranch() != null
                        ? p.getInstallment().getAdmission().getAdmissionBranch().getId()
                        : null;
                if (ownerBranchId == null || !ownerBranchId.equals(branch.getId())) {
                    throw new IllegalArgumentException("One or more selected payments do not belong to this branch.");
                }
                // Remit only what's still available on each fee: original
                // amount minus what's already been allocated to expenses/petty.
                sent = sent.add(remainingOf(p));
            }
        }

        if (sent.compareTo(BigDecimal.ZERO) > 0 && (request.getSentToHoBy() == null || request.getSentToHoBy().isBlank())) {
            throw new IllegalArgumentException("Please enter who is carrying the cash/cheque to HO.");
        }

        BranchCollectionDashboardDto current = getDaily(branch.getId(), date);
        day.setExpensesAmount(current.getSummary().getExpensesAmount());
        day.setPettyCashAmount(petty);
        day.setSentToHoAmount(sent);
        day.setSentToHoBy(trimToNull(request.getSentToHoBy()));
        OffsetDateTime remittedAt = sent.compareTo(BigDecimal.ZERO) > 0 ? java.time.OffsetDateTime.now() : null;
        day.setSentToHoAt(remittedAt);
        day.setNotes(trimToNull(request.getNotes()));
        cashbookDayRepository.save(day);

        if (sent.compareTo(BigDecimal.ZERO) > 0) {
            BranchCashbookRemittance remittance = new BranchCashbookRemittance();
            remittance.setBranch(branch);
            remittance.setBusinessDate(date);
            remittance.setSentAmount(sent);
            remittance.setSentBy(trimToNull(request.getSentToHoBy()));
            remittance.setSentAt(remittedAt != null ? remittedAt : OffsetDateTime.now());
            remittance.setNotes(trimToNull(request.getNotes()));
            remittance = remittanceRepository.save(remittance);

            // Stamp every selected payment with the new remittance id so the
            // dashboard hides them from future selection and the history detail
            // view can reconstruct exactly which payments were sent.
            // Stamp remittanceId AND mark the fee as fully consumed on each
            // selected payment in a single entity write per payment. We can't
            // split this into a bulk @Modifying UPDATE (for remittanceId) plus
            // a separate save() (for consumedAmount): Hibernate's default full
            // UPDATE on the managed entity would write a stale null back over
            // the just-stamped remittanceId.
            for (FeeInstallmentPayment p : selectedPayments) {
                if (p.getRemittanceId() != null) {
                    throw new IllegalArgumentException("One or more selected payments were remitted by another action. Please refresh.");
                }
                p.setRemittanceId(remittance.getId());
                p.setConsumedAmount(amountOrZero(p.getAmount()));
                paymentRepository.save(p);
            }
        }

        return getDaily(branch.getId(), date).getSummary();
    }

    @Transactional(readOnly = true)
    public PagedResponse<BranchRemittanceHistoryDto> getRemittanceHistory(Long branchId, int page, int size) {
        if (branchId == null) {
            return PagedResponse.<BranchRemittanceHistoryDto>builder()
                    .content(List.of())
                    .number(0)
                    .size(size)
                    .totalPages(0)
                    .totalElements(0L)
                    .first(true)
                    .last(true)
                    .numberOfElements(0)
                    .build();
        }
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        Page<BranchCashbookRemittance> result = remittanceRepository
                .findByBranch_IdOrderBySentAtDescIdDesc(branchId, PageRequest.of(safePage, safeSize));
        List<BranchRemittanceHistoryDto> content = result.getContent().stream()
                .map(remit -> BranchRemittanceHistoryDto.builder()
                        .id(remit.getId())
                        .businessDate(remit.getBusinessDate() != null ? remit.getBusinessDate().toString() : null)
                        .sentAmount(amountOrZero(remit.getSentAmount()))
                        .sentBy(remit.getSentBy())
                        .sentAt(remit.getSentAt())
                        .notes(remit.getNotes())
                        .build())
                .toList();
        return PagedResponse.<BranchRemittanceHistoryDto>builder()
                .content(content)
                .number(result.getNumber())
                .size(result.getSize())
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .first(result.isFirst())
                .last(result.isLast())
                .numberOfElements(result.getNumberOfElements())
                .build();
    }

    @Transactional(readOnly = true)
    public List<BranchRemittancesGroupDto> getHoRemittances(List<Long> branchIds, int perBranchLimit) {
        if (branchIds == null || branchIds.isEmpty()) {
            return List.of();
        }
        int safeLimit = perBranchLimit <= 0 ? 200 : Math.min(perBranchLimit, 1000);
        List<BranchRemittancesGroupDto> groups = new java.util.ArrayList<>(branchIds.size());
        for (Long branchId : branchIds) {
            if (branchId == null) {
                continue;
            }
            BranchMaster branch = branchRepository.findById(branchId).orElse(null);
            if (branch == null) {
                continue;
            }
            Page<BranchCashbookRemittance> page = remittanceRepository
                    .findByBranch_IdOrderBySentAtDescIdDesc(branch.getId(), PageRequest.of(0, safeLimit));
            List<BranchCashbookRemittance> rows = page.getContent();
            BigDecimal total = rows.stream()
                    .map(r -> amountOrZero(r.getSentAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            OffsetDateTime lastAt = rows.stream()
                    .map(BranchCashbookRemittance::getSentAt)
                    .filter(java.util.Objects::nonNull)
                    .max(OffsetDateTime::compareTo)
                    .orElse(null);
            List<BranchRemittanceHistoryDto> history = rows.stream()
                    .map(r -> BranchRemittanceHistoryDto.builder()
                            .id(r.getId())
                            .businessDate(r.getBusinessDate() != null ? r.getBusinessDate().toString() : null)
                            .sentAmount(amountOrZero(r.getSentAmount()))
                            .sentBy(r.getSentBy())
                            .sentAt(r.getSentAt())
                            .notes(r.getNotes())
                            .build())
                    .toList();
            groups.add(BranchRemittancesGroupDto.builder()
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .totalCount(page.getTotalElements())
                    .totalSentAmount(total)
                    .lastSentAt(lastAt)
                    .remittances(history)
                    .build());
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public BranchRemittanceDetailDto getRemittanceDetail(Long branchId, Long remittanceId) {
        if (branchId == null || remittanceId == null) {
            throw new IllegalArgumentException("Branch and remittance are required.");
        }
        BranchCashbookRemittance remittance = remittanceRepository.findById(remittanceId)
                .orElseThrow(() -> new IllegalArgumentException("Remittance not found."));
        if (remittance.getBranch() == null || !branchId.equals(remittance.getBranch().getId())) {
            throw new IllegalArgumentException("Remittance does not belong to this branch.");
        }

        OffsetDateTime upperCutoff = remittance.getSentAt();
        BranchCashbookRemittance previous = upperCutoff == null ? null
                : remittanceRepository
                        .findFirstByBranch_IdAndSentAtLessThanOrderBySentAtDescIdDesc(branchId, upperCutoff)
                        .orElse(null);

        LocalDate cycleEnd = remittance.getBusinessDate();
        LocalDate cycleStart;
        OffsetDateTime lowerCutoff;
        if (previous == null) {
            cycleStart = LocalDate.of(2000, 1, 1);
            lowerCutoff = null;
        } else if (previous.getBusinessDate() != null
                && previous.getBusinessDate().equals(cycleEnd)) {
            cycleStart = cycleEnd;
            lowerCutoff = previous.getSentAt();
        } else {
            cycleStart = previous.getBusinessDate() != null
                    ? previous.getBusinessDate().plusDays(1)
                    : LocalDate.of(2000, 1, 1);
            lowerCutoff = null;
        }

        final OffsetDateTime lower = lowerCutoff;
        final OffsetDateTime upper = upperCutoff;
        final LocalDate startDate = cycleStart;
        final LocalDate endDate = cycleEnd;

        // Prefer the explicit selection tagged with remittance_id (new flow).
        // Fall back to cycle-window math for pre-migration remittances where
        // no payments were stamped.
        List<FeeInstallmentPayment> taggedPayments =
                paymentRepository.findByRemittanceIdOrderByCreatedAtAscPaymentIdAsc(remittance.getId());
        List<FeeInstallmentPayment> collections;
        if (!taggedPayments.isEmpty()) {
            collections = taggedPayments;
        } else {
            collections = paymentRepository.findBranchCollectionCandidates(branchId).stream()
                    .filter(p -> {
                        LocalDate businessDate = resolvePaymentBusinessDate(p);
                        if (businessDate == null
                                || businessDate.isBefore(startDate)
                                || businessDate.isAfter(endDate)) {
                            return false;
                        }
                        if (p.getCreatedAt() != null) {
                            if (lower != null && p.getCreatedAt().isBefore(lower)) {
                                return false;
                            }
                            if (upper != null && !p.getCreatedAt().isBefore(upper)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .toList();
        }

        List<BranchCashbookDay> days = cashbookDayRepository
                .findByBranch_IdAndBusinessDateBetweenOrderByBusinessDateAsc(branchId, cycleStart, cycleEnd);
        List<BranchCashbookExpense> expenseEntries = days.stream()
                .filter(d -> d.getId() != null)
                .flatMap(d -> expenseRepository.findByCashbookDay_IdOrderByCreatedAtAsc(d.getId()).stream())
                .filter(ex -> {
                    if (ex.getCreatedAt() == null) {
                        return true;
                    }
                    if (lower != null && ex.getCreatedAt().isBefore(lower)) {
                        return false;
                    }
                    if (upper != null && !ex.getCreatedAt().isBefore(upper)) {
                        return false;
                    }
                    return true;
                })
                .toList();

        BigDecimal cashCollected = collections.stream()
                .filter(p -> "Cash".equalsIgnoreCase(p.getPaymentType()))
                .map(FeeInstallmentPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal chequeCollected = collections.stream()
                .filter(p -> "Cheque".equalsIgnoreCase(p.getPaymentType())
                        || "Check".equalsIgnoreCase(p.getPaymentType()))
                .map(FeeInstallmentPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossCollected = cashCollected.add(chequeCollected);

        BigDecimal collectionExpenseTotal = expenseEntries.stream()
                .filter(ex -> "COLLECTION".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pettyExpenseTotal = expenseEntries.stream()
                .filter(ex -> "PETTY".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pettyTopupTotal = expenseEntries.stream()
                .filter(ex -> "PETTY_TOPUP".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pettyReturnTotal = expenseEntries.stream()
                .filter(ex -> "PETTY_RETURN".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBranchExpenses = collectionExpenseTotal.add(pettyExpenseTotal);
        BigDecimal totalInHand = grossCollected
                .subtract(pettyTopupTotal)
                .add(pettyReturnTotal)
                .subtract(collectionExpenseTotal);
        if (totalInHand.compareTo(BigDecimal.ZERO) < 0) {
            totalInHand = BigDecimal.ZERO;
        }

        return BranchRemittanceDetailDto.builder()
                .remittanceId(remittance.getId())
                .businessDate(cycleEnd != null ? cycleEnd.toString() : null)
                .cycleStart(cycleStart.toString())
                .cycleEnd(cycleEnd != null ? cycleEnd.toString() : null)
                .totalStudentFeeCollected(grossCollected)
                .cashCollected(cashCollected)
                .chequeCollected(chequeCollected)
                .collectionExpenses(collectionExpenseTotal)
                .pettyExpenses(pettyExpenseTotal)
                .totalBranchExpenses(totalBranchExpenses)
                .totalInHandCollection(totalInHand)
                .pettyTopupTotal(pettyTopupTotal)
                .pettyReturnTotal(pettyReturnTotal)
                .sentAmount(amountOrZero(remittance.getSentAmount()))
                .sentBy(remittance.getSentBy())
                .sentAt(remittance.getSentAt())
                .notes(remittance.getNotes())
                .collections(groupCollectionsForDisplay(collections))
                .expenses(expenseEntries.stream()
                        .filter(ex -> "COLLECTION".equalsIgnoreCase(ex.getSourceType())
                                || "PETTY".equalsIgnoreCase(ex.getSourceType()))
                        .map(this::toExpenseDto)
                        .toList())
                .pettyTransactions(expenseEntries.stream()
                        .filter(ex -> "PETTY_TOPUP".equalsIgnoreCase(ex.getSourceType())
                                || "PETTY_RETURN".equalsIgnoreCase(ex.getSourceType()))
                        .map(this::toExpenseDto)
                        .toList())
                .build();
    }

    // -------- Fee allocation engine ----------------------------------------

    /**
     * Loads the user-picked cash fees, validates ownership/eligibility, and
     * FIFOs the {@code amountToAllocate} across them in selection order:
     * first fee fully consumed (up to its remaining), then partial from next,
     * until the amount is fully allocated. Throws if no fees were picked, if
     * any fee isn't cash / not unremitted / wrong branch, or if the total
     * remaining is less than the amount needed.
     *
     * Updates each touched payment's {@code consumed_amount} cache.
     * Returns the saved allocation rows (one per touched fee).
     */
    private List<com.bothash.admissionservice.entity.FeePaymentAllocation> allocateAcrossFees(
            Long branchId,
            BigDecimal amountToAllocate,
            List<Long> feePaymentIds,
            BranchCashbookExpense expense) {
        if (feePaymentIds == null || feePaymentIds.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one student fee to draw this amount from.");
        }
        // Preserve user-picked order while de-duping.
        List<Long> ordered = feePaymentIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<FeeInstallmentPayment> fees = paymentRepository.findAllById(ordered);
        if (fees.size() != ordered.size()) {
            throw new IllegalArgumentException("One or more selected fees could not be found.");
        }
        // Sort the loaded list back into the user's pick order so FIFO is deterministic.
        java.util.Map<Long, FeeInstallmentPayment> byId = new java.util.HashMap<>();
        fees.forEach(p -> byId.put(p.getPaymentId(), p));
        List<FeeInstallmentPayment> orderedFees = ordered.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (FeeInstallmentPayment p : orderedFees) {
            Long ownerBranchId = p.getInstallment() != null
                    && p.getInstallment().getAdmission() != null
                    && p.getInstallment().getAdmission().getAdmissionBranch() != null
                    ? p.getInstallment().getAdmission().getAdmissionBranch().getId() : null;
            if (ownerBranchId == null || !ownerBranchId.equals(branchId)) {
                throw new IllegalArgumentException("Selected fee does not belong to this branch.");
            }
            if (p.getRemittanceId() != null) {
                throw new IllegalArgumentException("Selected fee has already been remitted.");
            }
            String type = p.getPaymentType() == null ? "" : p.getPaymentType().trim().toUpperCase();
            if (!"CASH".equals(type)) {
                throw new IllegalArgumentException("Only cash fees can be used for expenses or petty topups.");
            }
            BigDecimal remaining = remainingOf(p);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                remaining = BigDecimal.ZERO;
            }
            totalRemaining = totalRemaining.add(remaining);
        }
        if (totalRemaining.compareTo(amountToAllocate) < 0) {
            throw new IllegalArgumentException("Selected fees only cover Rs " + totalRemaining
                    + " — not enough for amount Rs " + amountToAllocate + ".");
        }

        List<com.bothash.admissionservice.entity.FeePaymentAllocation> created = new java.util.ArrayList<>();
        BigDecimal toAllocate = amountToAllocate;
        for (FeeInstallmentPayment p : orderedFees) {
            if (toAllocate.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal remaining = remainingOf(p);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal slice = remaining.min(toAllocate);
            com.bothash.admissionservice.entity.FeePaymentAllocation alloc =
                    com.bothash.admissionservice.entity.FeePaymentAllocation.builder()
                            .payment(p)
                            .expense(expense)
                            .amount(slice)
                            .build();
            alloc = allocationRepository.save(alloc);
            created.add(alloc);
            p.setConsumedAmount(amountOrZero(p.getConsumedAmount()).add(slice));
            paymentRepository.save(p);
            toAllocate = toAllocate.subtract(slice);
        }
        return created;
    }

    /**
     * Reverses every allocation tied to {@code expense} by creating negative
     * allocation rows and decreasing the corresponding payments'
     * {@code consumed_amount}. Used by expense edit/delete and by petty
     * return (paired with FIFO selection of which topups to reverse).
     */
    private void reverseAllocationsForExpense(BranchCashbookExpense expense) {
        if (expense == null || expense.getId() == null) {
            return;
        }
        List<com.bothash.admissionservice.entity.FeePaymentAllocation> existing =
                allocationRepository.findByExpense_Id(expense.getId());
        for (com.bothash.admissionservice.entity.FeePaymentAllocation a : existing) {
            FeeInstallmentPayment p = a.getPayment();
            if (p == null) continue;
            p.setConsumedAmount(amountOrZero(p.getConsumedAmount()).subtract(amountOrZero(a.getAmount())));
            paymentRepository.save(p);
        }
        allocationRepository.deleteAll(existing);
    }

    /**
     * FIFO-restores cash to the fees that fed earlier petty topups, up to
     * {@code amountToRestore}. Used by petty return. Creates negative
     * allocation rows linked to {@code returnExpense}.
     */
    private void restorePettyAllocationsFifo(Long branchId, BigDecimal amountToRestore, BranchCashbookExpense returnExpense) {
        BigDecimal remaining = amountToRestore;
        if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // Compute net positive allocations per source petty topup, FIFO.
        List<com.bothash.admissionservice.entity.FeePaymentAllocation> active =
                allocationRepository.findPettyTopupAllocationsForBranch(branchId);
        // Group by payment_id and net the amounts (positive topup minus prior returns).
        // We walk in createdAt order and reverse from the oldest still-positive slices.
        for (com.bothash.admissionservice.entity.FeePaymentAllocation source : active) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            FeeInstallmentPayment p = source.getPayment();
            if (p == null) continue;
            // The slice's remaining (still-consumed) portion is its original
            // amount minus any prior returns already applied to this payment
            // via OTHER allocations. The cleanest proxy: how much of this
            // source allocation is still effectively consumed on this payment.
            // We use min(source.amount, payment.consumedAmount) since the
            // payment can't be more-restored than it currently has consumed.
            BigDecimal sourceAmt = amountOrZero(source.getAmount());
            BigDecimal payConsumed = amountOrZero(p.getConsumedAmount());
            BigDecimal restorable = sourceAmt.min(payConsumed);
            if (restorable.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal slice = restorable.min(remaining);
            com.bothash.admissionservice.entity.FeePaymentAllocation neg =
                    com.bothash.admissionservice.entity.FeePaymentAllocation.builder()
                            .payment(p)
                            .expense(returnExpense)
                            .amount(slice.negate())
                            .build();
            allocationRepository.save(neg);
            p.setConsumedAmount(payConsumed.subtract(slice));
            paymentRepository.save(p);
            remaining = remaining.subtract(slice);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // Should be impossible if upstream petty balance check passed.
            throw new IllegalArgumentException("Could not match petty return back to source fees (Rs " + remaining + " unmatched).");
        }
    }

    /** Convenience: {@code amount - consumedAmount}, floored at 0. */
    private BigDecimal remainingOf(FeeInstallmentPayment p) {
        BigDecimal r = amountOrZero(p.getAmount()).subtract(amountOrZero(p.getConsumedAmount()));
        return r.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : r;
    }

    // -----------------------------------------------------------------------

    @Transactional
    public BranchCashbookExpenseDto addExpense(BranchCashbookExpenseRequest request) {
        if (request == null || request.getBranchId() == null) {
            throw new IllegalArgumentException("Branch is required.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }
        if (!org.springframework.util.StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("Expense title is required.");
        }
        LocalDate date = request.getBusinessDate() != null ? request.getBusinessDate() : LocalDate.now();
        BranchMaster branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
        BranchCashbookDay day = cashbookDayRepository.findByBranch_IdAndBusinessDate(branch.getId(), date)
                .orElseGet(() -> {
                    BranchCashbookDay created = new BranchCashbookDay();
                    created.setBranch(branch);
                    created.setBusinessDate(date);
                    created.setOpeningBalance(BigDecimal.ZERO);
                    created.setExpensesAmount(BigDecimal.ZERO);
                    created.setPettyCashAmount(BigDecimal.ZERO);
                    created.setSentToHoAmount(BigDecimal.ZERO);
                    return cashbookDayRepository.save(created);
                });
        BranchCashbookExpense expense = new BranchCashbookExpense();
        expense.setCashbookDay(day);
        expense.setTitle(request.getTitle().trim());
        expense.setNote(trimToNull(request.getNote()));
        expense.setSourceType(normalizeSourceType(request.getSourceType()));
        if ("PETTY".equals(expense.getSourceType())) {
            BigDecimal availablePetty = resolveGlobalPettyBalance(request.getBranchId(), date);
            if (request.getAmount().compareTo(availablePetty) > 0) {
                throw new IllegalArgumentException("Expense exceeds available petty cash.");
            }
        }
        expense.setAmount(request.getAmount());
        expense = expenseRepository.save(expense);

        // COLLECTION expense must be drawn from picked cash fees (FIFO allocation).
        if ("COLLECTION".equals(expense.getSourceType())) {
            allocateAcrossFees(request.getBranchId(), request.getAmount(), request.getFeePaymentIds(), expense);
        }

        BigDecimal totalExpense = expenseRepository.findByCashbookDay_IdOrderByCreatedAtAsc(day.getId()).stream()
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        day.setExpensesAmount(totalExpense);
        cashbookDayRepository.save(day);
        return toExpenseDto(expense);
    }

    @Transactional
    public BranchCashbookExpenseDto addPettyCash(BranchPettyCashAddRequest request) {
        if (request == null || request.getBranchId() == null) {
            throw new IllegalArgumentException("Branch is required.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Petty cash amount must be greater than zero.");
        }
        LocalDate date = request.getBusinessDate() != null ? request.getBusinessDate() : LocalDate.now();
        BigDecimal availableCash = resolveAvailableCashInHand(request.getBranchId(), date);
        if (request.getAmount().compareTo(availableCash) > 0) {
            throw new IllegalArgumentException("Petty top-up cannot exceed available cash in hand.");
        }
        BranchMaster branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
        BranchCashbookDay day = cashbookDayRepository.findByBranch_IdAndBusinessDate(branch.getId(), date)
                .orElseGet(() -> {
                    BranchCashbookDay created = new BranchCashbookDay();
                    created.setBranch(branch);
                    created.setBusinessDate(date);
                    created.setOpeningBalance(BigDecimal.ZERO);
                    created.setExpensesAmount(BigDecimal.ZERO);
                    created.setPettyCashAmount(BigDecimal.ZERO);
                    created.setSentToHoAmount(BigDecimal.ZERO);
                    return cashbookDayRepository.save(created);
                });
        BranchCashbookExpense topup = new BranchCashbookExpense();
        topup.setCashbookDay(day);
        topup.setTitle("Petty Cash Top-up");
        topup.setNote(trimToNull(request.getNote()));
        topup.setSourceType("PETTY_TOPUP");
        topup.setAmount(request.getAmount());
        topup = expenseRepository.save(topup);

        // Petty topup must be drawn from picked cash fees (FIFO allocation).
        allocateAcrossFees(request.getBranchId(), request.getAmount(), request.getFeePaymentIds(), topup);

        day.setPettyCashAmount(resolvePettyBalance(request.getBranchId(), date));
        cashbookDayRepository.save(day);
        return toExpenseDto(topup);
    }

    @Transactional
    public BranchCashbookExpenseDto returnPettyCashToCollection(BranchPettyCashReturnRequest request) {
        if (request == null || request.getBranchId() == null) {
            throw new IllegalArgumentException("Branch is required.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Return amount must be greater than zero.");
        }
        LocalDate date = request.getBusinessDate() != null ? request.getBusinessDate() : LocalDate.now();
        BigDecimal availablePetty = resolveGlobalPettyBalance(request.getBranchId(), date);
        if (request.getAmount().compareTo(availablePetty) > 0) {
            throw new IllegalArgumentException("Return amount exceeds available petty cash.");
        }
        BranchMaster branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
        BranchCashbookDay day = cashbookDayRepository.findByBranch_IdAndBusinessDate(branch.getId(), date)
                .orElseGet(() -> {
                    BranchCashbookDay created = new BranchCashbookDay();
                    created.setBranch(branch);
                    created.setBusinessDate(date);
                    created.setOpeningBalance(BigDecimal.ZERO);
                    created.setExpensesAmount(BigDecimal.ZERO);
                    created.setPettyCashAmount(BigDecimal.ZERO);
                    created.setSentToHoAmount(BigDecimal.ZERO);
                    return cashbookDayRepository.save(created);
                });
        BranchCashbookExpense transfer = new BranchCashbookExpense();
        transfer.setCashbookDay(day);
        transfer.setTitle("Petty Cash Returned To Collection");
        transfer.setNote(trimToNull(request.getNote()));
        transfer.setSourceType("PETTY_RETURN");
        transfer.setAmount(request.getAmount());
        transfer = expenseRepository.save(transfer);

        // FIFO-restore the cash to the fees that fed earlier petty topups.
        restorePettyAllocationsFifo(request.getBranchId(), request.getAmount(), transfer);

        day.setPettyCashAmount(resolveGlobalPettyBalance(request.getBranchId(), date));
        cashbookDayRepository.save(day);
        return toExpenseDto(transfer);
    }

    @Transactional
    public BranchCashbookExpenseDto updateExpense(Long expenseId, BranchCashbookExpenseUpdateRequest request) {
        if (expenseId == null) {
            throw new IllegalArgumentException("Expense id is required.");
        }
        if (request == null || request.getBranchId() == null) {
            throw new IllegalArgumentException("Branch is required.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }
        if (!org.springframework.util.StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("Expense title is required.");
        }
        BranchCashbookExpense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found."));
        BranchCashbookDay day = expense.getCashbookDay();
        if (day == null || day.getBranch() == null
                || !request.getBranchId().equals(day.getBranch().getId())) {
            throw new IllegalArgumentException("Expense does not belong to this branch.");
        }
        String oldSourceType = expense.getSourceType();
        if (!"COLLECTION".equalsIgnoreCase(oldSourceType) && !"PETTY".equalsIgnoreCase(oldSourceType)) {
            throw new IllegalArgumentException("Use the petty cash forms to modify movement entries.");
        }
        String newSourceType = normalizeEditableSourceType(request.getSourceType());
        ensureExpenseInOpenCycle(expense);

        BigDecimal oldAmount = amountOrZero(expense.getAmount());
        BigDecimal newAmount = request.getAmount();
        Long branchId = day.getBranch().getId();
        LocalDate today = LocalDate.now();

        // Petty balance check (global, not cycle-bounded — petty does not reset on remittance)
        BigDecimal globalPetty = resolveGlobalPettyBalance(branchId, today);
        BigDecimal projectedPetty = globalPetty;
        if ("PETTY".equals(oldSourceType)) {
            projectedPetty = projectedPetty.add(oldAmount);
        }
        if ("PETTY".equals(newSourceType)) {
            projectedPetty = projectedPetty.subtract(newAmount);
        }
        if (projectedPetty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Expense exceeds available petty cash.");
        }

        // Cash-in-hand check: unremitted cash sum minus cycle's petty topup
        // plus cycle's petty return minus the projected new collection-expense
        // total. Cheques are excluded — they aren't liquid.
        CycleContext cycle = loadCycleContext(branchId, today);
        BigDecimal newCollExpenseTotal = cycle.collectionExpenseTotal;
        if ("COLLECTION".equals(oldSourceType)) {
            newCollExpenseTotal = newCollExpenseTotal.subtract(oldAmount);
        }
        if ("COLLECTION".equals(newSourceType)) {
            newCollExpenseTotal = newCollExpenseTotal.add(newAmount);
        }
        BigDecimal cashCollected = computeCashCollected(
                paymentRepository.findUnremittedBranchCollectionCandidates(branchId));
        BigDecimal projectedCashInHand = cashCollected
                .subtract(cycle.pettyTopupTotal)
                .add(cycle.pettyReturnToCollectionTotal)
                .subtract(newCollExpenseTotal);
        if (projectedCashInHand.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Expense exceeds available cash in hand.");
        }

        expense.setTitle(request.getTitle().trim());
        expense.setNote(trimToNull(request.getNote()));
        expense.setSourceType(newSourceType);
        expense.setAmount(newAmount);
        expense = expenseRepository.save(expense);

        // Re-allocate fee sources whenever the new sourceType is COLLECTION.
        // First wipe the existing allocations (refunding the fees), then
        // FIFO-allocate the new amount against the freshly-picked fees.
        reverseAllocationsForExpense(expense);
        if ("COLLECTION".equals(newSourceType)) {
            allocateAcrossFees(branchId, newAmount, request.getFeePaymentIds(), expense);
        }

        recomputeDayExpensesAmount(day);
        return toExpenseDto(expense);
    }

    @Transactional
    public void deleteExpense(Long expenseId, Long branchId) {
        if (expenseId == null) {
            throw new IllegalArgumentException("Expense id is required.");
        }
        if (branchId == null) {
            throw new IllegalArgumentException("Branch is required.");
        }
        BranchCashbookExpense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found."));
        BranchCashbookDay day = expense.getCashbookDay();
        if (day == null || day.getBranch() == null
                || !branchId.equals(day.getBranch().getId())) {
            throw new IllegalArgumentException("Expense does not belong to this branch.");
        }
        String sourceType = expense.getSourceType();
        if (!"COLLECTION".equalsIgnoreCase(sourceType) && !"PETTY".equalsIgnoreCase(sourceType)) {
            throw new IllegalArgumentException("Use the petty cash forms to modify movement entries.");
        }
        ensureExpenseInOpenCycle(expense);

        reverseAllocationsForExpense(expense);
        expenseRepository.delete(expense);
        recomputeDayExpensesAmount(day);
    }

    private void recomputeDayExpensesAmount(BranchCashbookDay day) {
        BigDecimal totalExpense = expenseRepository.findByCashbookDay_IdOrderByCreatedAtAsc(day.getId()).stream()
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        day.setExpensesAmount(totalExpense);
        cashbookDayRepository.save(day);
    }

    private String normalizeEditableSourceType(String sourceType) {
        String raw = sourceType == null ? "" : sourceType.trim().toUpperCase();
        if ("COLLECTION".equals(raw)) {
            return "COLLECTION";
        }
        if ("PETTY".equals(raw)) {
            return "PETTY";
        }
        throw new IllegalArgumentException("Source must be COLLECTION or PETTY.");
    }

    private void ensureExpenseInOpenCycle(BranchCashbookExpense expense) {
        if (expense == null || expense.getCashbookDay() == null
                || expense.getCashbookDay().getBranch() == null) {
            return;
        }
        Long branchId = expense.getCashbookDay().getBranch().getId();
        BranchCashbookDay latestRemit = cashbookDayRepository
                .findFirstByBranch_IdAndBusinessDateLessThanEqualAndSentToHoAmountGreaterThanOrderByBusinessDateDesc(
                        branchId, LocalDate.now(), BigDecimal.ZERO)
                .orElse(null);
        if (latestRemit == null || latestRemit.getSentToHoAt() == null) {
            return;
        }
        OffsetDateTime expCreated = expense.getCreatedAt();
        if (expCreated != null && expCreated.isBefore(latestRemit.getSentToHoAt())) {
            throw new IllegalArgumentException("Cannot modify expenses from a closed remittance cycle.");
        }
    }

    private BranchCollectionPaymentItemDto toCollectionItem(FeeInstallmentPayment payment) {
        Admission2 admission = payment.getInstallment() != null ? payment.getInstallment().getAdmission() : null;
        String studentName = admission != null && admission.getStudent() != null
                ? admission.getStudent().getFullName()
                : null;
        String courseName = admission != null && admission.getCourse() != null
                ? admission.getCourse().getName()
                : null;
        return BranchCollectionPaymentItemDto.builder()
                .paymentId(payment.getPaymentId())
                .admissionId(admission != null ? admission.getAdmissionId() : null)
                .studentName(studentName)
                .courseName(courseName)
                .paymentType(payment.getPaymentType())
                .amount(amountOrZero(payment.getAmount()))
                .txnRef(payment.getTxnRef())
                .receivedBy(payment.getReceivedBy())
                .build();
    }

    /**
     * Collapses installment-level payments into one row per physical payment
     * event. Payments sharing a non-blank {@code paymentGroupId} are summed
     * together; payments without a group id stand alone (each is its own
     * row). Order is preserved from the input list, which is already sorted
     * by createdAt asc, paymentId asc by the repository query.
     */
    private List<BranchCollectionPaymentItemDto> groupCollectionsForDisplay(List<FeeInstallmentPayment> payments) {
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }
        Map<String, List<FeeInstallmentPayment>> groups = new LinkedHashMap<>();
        for (FeeInstallmentPayment p : payments) {
            String groupId = p.getPaymentGroupId();
            String key = (groupId != null && !groupId.isBlank())
                    ? "G:" + groupId
                    : "P:" + p.getPaymentId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        List<BranchCollectionPaymentItemDto> items = new ArrayList<>(groups.size());
        for (List<FeeInstallmentPayment> group : groups.values()) {
            FeeInstallmentPayment first = group.get(0);
            BigDecimal total = group.stream()
                    .map(FeeInstallmentPayment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Admission2 admission = first.getInstallment() != null ? first.getInstallment().getAdmission() : null;
            String studentName = admission != null && admission.getStudent() != null
                    ? admission.getStudent().getFullName()
                    : null;
            String courseName = admission != null && admission.getCourse() != null
                    ? admission.getCourse().getName()
                    : null;
            List<Long> ids = group.stream()
                    .map(FeeInstallmentPayment::getPaymentId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            // A group is remitted iff any of its underlying payments carries
            // a remittance_id. Stamping is all-or-nothing per group, so the
            // first payment's value represents the whole group.
            Long groupRemittanceId = group.stream()
                    .map(FeeInstallmentPayment::getRemittanceId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            BigDecimal consumed = group.stream()
                    .map(p -> amountOrZero(p.getConsumedAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remaining = amountOrZero(total).subtract(consumed);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                remaining = BigDecimal.ZERO;
            }
            items.add(BranchCollectionPaymentItemDto.builder()
                    .paymentId(first.getPaymentId())
                    .paymentIds(ids)
                    .remittanceId(groupRemittanceId)
                    .admissionId(admission != null ? admission.getAdmissionId() : null)
                    .studentName(studentName)
                    .courseName(courseName)
                    .paymentType(first.getPaymentType())
                    .amount(amountOrZero(total))
                    .consumedAmount(consumed)
                    .remainingAmount(remaining)
                    .txnRef(first.getTxnRef())
                    .receivedBy(first.getReceivedBy())
                    .build());
        }
        return items;
    }

    private BigDecimal resolveOpeningBalance(Long branchId, LocalDate date) {
        return cashbookDayRepository
                .findFirstByBranch_IdAndBusinessDateLessThanOrderByBusinessDateDesc(branchId, date)
                .map(prev -> {
                    BigDecimal opening = amountOrZero(prev.getOpeningBalance());
                    BigDecimal expenses = prev.getId() != null
                            ? expenseRepository.findByCashbookDay_IdOrderByCreatedAtAsc(prev.getId()).stream()
                                    .map(BranchCashbookExpense::getAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                            : BigDecimal.ZERO;
                    BigDecimal petty = amountOrZero(prev.getPettyCashAmount());
                    BigDecimal sent = amountOrZero(prev.getSentToHoAmount());
                    LocalDate previousDate = prev.getBusinessDate();
                    BigDecimal previousCollections = paymentRepository.findBranchCollectionsForDate(branchId, previousDate)
                            .stream()
                            .map(FeeInstallmentPayment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal closing = opening.add(previousCollections).subtract(expenses).subtract(sent);
                    if (petty.compareTo(BigDecimal.ZERO) > 0) {
                        closing = petty.max(closing);
                    }
                    return closing.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : closing;
                })
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal computeCashCollected(List<FeeInstallmentPayment> collections) {
        if (collections == null) {
            return BigDecimal.ZERO;
        }
        return collections.stream()
                .filter(p -> "Cash".equalsIgnoreCase(p.getPaymentType()))
                .map(FeeInstallmentPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Available cash physically sitting in the branch's collection box for
     * the current cycle. Cheques are excluded — they aren't liquid and
     * cannot be spent or moved to petty until they clear at HO.
     */
    private BigDecimal resolveAvailableCashInHand(Long branchId, LocalDate date) {
        CycleContext cycle = loadCycleContext(branchId, date);
        // Use unremitted cash (not cycle-bounded) so carry-over from prior
        // partial remittances is still counted as physical cash in the box.
        BigDecimal cashCollected = computeCashCollected(
                paymentRepository.findUnremittedBranchCollectionCandidates(branchId));
        BigDecimal available = cashCollected
                .subtract(cycle.pettyTopupTotal)
                .add(cycle.pettyReturnToCollectionTotal)
                .subtract(cycle.collectionExpenseTotal);
        return available.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : available;
    }

    private BigDecimal resolvePettyBalance(Long branchId, LocalDate date) {
        return resolveGlobalPettyBalance(branchId, date);
    }

    private BigDecimal resolveGlobalPettyBalance(Long branchId, LocalDate date) {
        List<BranchCashbookDay> days = cashbookDayRepository.findByBranch_IdAndBusinessDateBetweenOrderByBusinessDateAsc(
                branchId,
                LocalDate.of(2000, 1, 1),
                date
        );
        List<BranchCashbookExpense> entries = days.stream()
                .filter(d -> d.getId() != null)
                .flatMap(d -> expenseRepository.findByCashbookDay_IdOrderByCreatedAtAsc(d.getId()).stream())
                .toList();
        BigDecimal topup = entries.stream()
                .filter(ex -> "PETTY_TOPUP".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal used = entries.stream()
                .filter(ex -> "PETTY".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = entries.stream()
                .filter(ex -> "PETTY_RETURN".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = topup.subtract(used).subtract(returned);
        return balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance;
    }

    private BigDecimal resolveCollectionExpense(Long branchId, LocalDate date) {
        return loadCycleContext(branchId, date).collectionExpenseTotal;
    }

    private BigDecimal resolvePettyTopup(Long branchId, LocalDate date) {
        return loadCycleContext(branchId, date).pettyTopupTotal;
    }

    private BigDecimal amountOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSourceType(String sourceType) {
        String raw = sourceType == null ? "" : sourceType.trim().toUpperCase();
        if ("PETTY".equals(raw)) {
            return "PETTY";
        }
        if ("PETTY_TOPUP".equals(raw)) {
            return "PETTY_TOPUP";
        }
        if ("PETTY_RETURN".equals(raw)) {
            return "PETTY_RETURN";
        }
        return "COLLECTION";
    }

    private String exclusionReasonForBranchCollection(FeeInstallmentPayment payment) {
        if (payment == null) {
            return "NULL_PAYMENT";
        }
        String status = payment.getStatus() != null ? payment.getStatus().trim().toUpperCase() : "";
        if ("REJECTED".equals(status) || "CANCELLED".equals(status)) {
            return "STATUS_" + status;
        }
        String paymentType = payment.getPaymentType() != null ? payment.getPaymentType().trim().toUpperCase() : "";
        boolean paymentTypeEligible = "CASH".equals(paymentType) || "CHEQUE".equals(paymentType) || "CHECK".equals(paymentType);
        if (!paymentTypeEligible) {
            return "NON_PHYSICAL_PAYMENT_TYPE";
        }
        return "FILTER_MISMATCH_OR_DATE_BOUNDARY";
    }

    private LocalDate resolvePaymentBusinessDate(FeeInstallmentPayment payment) {
        if (payment == null) {
            return null;
        }
        if (payment.getPaidOn() != null) {
            return payment.getPaidOn();
        }
        if (payment.getCreatedAt() != null) {
            return payment.getCreatedAt().atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
        }
        return null;
    }

    private BranchCashbookExpenseDto toExpenseDto(BranchCashbookExpense expense) {
        return BranchCashbookExpenseDto.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .note(expense.getNote())
                .sourceType(expense.getSourceType())
                .amount(amountOrZero(expense.getAmount()))
                .build();
    }

    private CycleContext loadCycleContext(Long branchId, LocalDate date) {
        BranchCashbookDay lastRemittanceDay = cashbookDayRepository
                .findFirstByBranch_IdAndBusinessDateLessThanEqualAndSentToHoAmountGreaterThanOrderByBusinessDateDesc(
                        branchId,
                        date,
                        BigDecimal.ZERO
                )
                .orElse(null);
        LocalDate cycleStart;
        OffsetDateTime remittanceCutoff = null;
        if (lastRemittanceDay == null) {
            cycleStart = LocalDate.of(2000, 1, 1);
        } else if (date.equals(lastRemittanceDay.getBusinessDate())) {
            // Same-day remittance: include payments created after remittance time.
            cycleStart = date;
            remittanceCutoff = lastRemittanceDay.getSentToHoAt();
        } else {
            // Remittance was on prior day: next day starts new cycle.
            cycleStart = lastRemittanceDay.getBusinessDate().plusDays(1);
        }
        if (cycleStart.isAfter(date)) {
            return new CycleContext(List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<FeeInstallmentPayment> rawPayments = paymentRepository.findAllPositiveByBranchAndCreatedDateRange(branchId, cycleStart, date);
        List<FeeInstallmentPayment> rawDateWindowPayments = paymentRepository.findAllPositiveByBranchAndCreatedDateRange(branchId, LocalDate.of(2000, 1, 1), date).stream()
                .filter(p -> {
                    LocalDate businessDate = resolvePaymentBusinessDate(p);
                    return businessDate != null
                            && !businessDate.isBefore(cycleStart)
                            && !businessDate.isAfter(date);
                })
                .toList();
        List<FeeInstallmentPayment> collections = paymentRepository.findBranchCollectionCandidates(branchId).stream()
                .filter(p -> {
                    LocalDate businessDate = resolvePaymentBusinessDate(p);
                    return businessDate != null
                            && !businessDate.isBefore(cycleStart)
                            && !businessDate.isAfter(date);
                })
                .toList();
        final OffsetDateTime cutoff = remittanceCutoff;
        if (cutoff != null) {
            collections = collections.stream()
                    .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(cutoff))
                    .toList();
            rawDateWindowPayments = rawDateWindowPayments.stream()
                    .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(cutoff))
                    .toList();
        }
        rawPayments = rawDateWindowPayments;
        List<BranchCashbookDay> days = cashbookDayRepository.findByBranch_IdAndBusinessDateBetweenOrderByBusinessDateAsc(branchId, cycleStart, date);
        List<BranchCashbookExpense> expenseEntries = days.stream()
                .filter(d -> d.getId() != null)
                .flatMap(d -> expenseRepository.findByCashbookDay_IdOrderByCreatedAtAsc(d.getId()).stream())
                .toList();
        if (cutoff != null) {
            expenseEntries = expenseEntries.stream()
                    .filter(ex -> ex.getCreatedAt() != null && !ex.getCreatedAt().isBefore(cutoff))
                    .toList();
        }
        BigDecimal totalCollection = collections.stream().map(FeeInstallmentPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collectionExpenseTotal = expenseEntries.stream()
                .filter(ex -> "COLLECTION".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pettyExpenseTotal = expenseEntries.stream()
                .filter(ex -> "PETTY".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pettyTopupTotal = expenseEntries.stream()
                .filter(ex -> "PETTY_TOPUP".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pettyReturnToCollectionTotal = expenseEntries.stream()
                .filter(ex -> "PETTY_RETURN".equalsIgnoreCase(ex.getSourceType()))
                .map(BranchCashbookExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cycleExpenseTotal = collectionExpenseTotal.add(pettyExpenseTotal);
        BigDecimal cyclePettyBalance = pettyTopupTotal.subtract(pettyExpenseTotal).subtract(pettyReturnToCollectionTotal);
        if (cyclePettyBalance.compareTo(BigDecimal.ZERO) < 0) {
            cyclePettyBalance = BigDecimal.ZERO;
        }
        if (log.isInfoEnabled()) {
            log.info(
                    "BranchCollection cycle branchId={} date={} cycleStart={} remittanceCutoff={} lastRemittanceDate={} lastRemittanceAmount={} filteredCount={} rawCount={} totalCollection={} collectionExpense={} pettyTopup={} pettyReturn={} cycleExpense={}",
                    branchId,
                    date,
                    cycleStart,
                    remittanceCutoff,
                    lastRemittanceDay != null ? lastRemittanceDay.getBusinessDate() : null,
                    lastRemittanceDay != null ? lastRemittanceDay.getSentToHoAmount() : null,
                    collections.size(),
                    rawPayments.size(),
                    totalCollection,
                    collectionExpenseTotal,
                    pettyTopupTotal,
                    pettyReturnToCollectionTotal,
                    cycleExpenseTotal
            );
        }
        if (log.isDebugEnabled()) {
            Set<Long> includedIds = collections.stream()
                    .map(FeeInstallmentPayment::getPaymentId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            rawPayments.forEach(p -> {
                String modeCode = p.getPaymentMode() != null ? p.getPaymentMode().getCode() : null;
                String modeLabel = p.getPaymentMode() != null ? p.getPaymentMode().getLabel() : null;
                Long admissionId = p.getInstallment() != null && p.getInstallment().getAdmission() != null
                        ? p.getInstallment().getAdmission().getAdmissionId()
                        : null;
                log.debug(
                        "BranchCollection raw paymentId={} admissionId={} amount={} paymentType={} modeCode={} modeLabel={} status={} paidOn={} createdAt={} groupId={}",
                        p.getPaymentId(),
                        admissionId,
                        p.getAmount(),
                        p.getPaymentType(),
                        modeCode,
                        modeLabel,
                        p.getStatus(),
                        p.getPaidOn(),
                        p.getCreatedAt(),
                        p.getPaymentGroupId()
                );
                if (!includedIds.contains(p.getPaymentId())) {
                    log.debug(
                            "BranchCollection excluded paymentId={} reason={} createdDateLocal={} createdDateSystem={} status={} paymentType={} modeCode={} modeLabel={}",
                            p.getPaymentId(),
                            exclusionReasonForBranchCollection(p),
                            p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null,
                            p.getCreatedAt() != null ? p.getCreatedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() : null,
                            p.getStatus(),
                            p.getPaymentType(),
                            modeCode,
                            modeLabel
                    );
                }
            });
        }
        return new CycleContext(
                collections,
                expenseEntries,
                totalCollection,
                collectionExpenseTotal,
                pettyTopupTotal,
                pettyReturnToCollectionTotal,
                cycleExpenseTotal,
                cyclePettyBalance
        );
    }

    private record CycleContext(
            List<FeeInstallmentPayment> collections,
            List<BranchCashbookExpense> expenseEntries,
            BigDecimal totalCollection,
            BigDecimal collectionExpenseTotal,
            BigDecimal pettyTopupTotal,
            BigDecimal pettyReturnToCollectionTotal,
            BigDecimal cycleExpenseTotal,
            BigDecimal cyclePettyBalance
    ) {
        private CycleContext(List<FeeInstallmentPayment> collections,
                             List<BranchCashbookExpense> expenseEntries,
                             BigDecimal totalCollection,
                             BigDecimal collectionExpenseTotal,
                             BigDecimal pettyTopupTotal,
                             BigDecimal cycleExpenseTotal) {
            this(collections, expenseEntries, totalCollection, collectionExpenseTotal, pettyTopupTotal, BigDecimal.ZERO, cycleExpenseTotal, BigDecimal.ZERO);
        }
    }
}
