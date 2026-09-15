package com.bothash.admissionservice.service.impl;

import java.util.List;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.dto.MiscPaymentDto;
import com.bothash.admissionservice.dto.MiscPaymentPageResponse;
import com.bothash.admissionservice.dto.MiscPaymentRequest;
import com.bothash.admissionservice.dto.UploadRequest;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.MiscPayment;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.MiscPaymentRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MiscPaymentService {

    private final MiscPaymentRepository miscPaymentRepository;
    private final CourseRepository courseRepository;
    private final PaymentModeService paymentModeService;
    private final InvoiceServiceImpl invoiceService;
    private final R2InvoiceStorageService r2InvoiceStorageService;

    @Transactional
    public MiscPaymentDto create(MiscPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        MiscPayment parent = null;
        if (request.getParentPaymentId() != null) {
            parent = miscPaymentRepository.findById(request.getParentPaymentId())
                    .orElseThrow(() -> new IllegalArgumentException("Original payment record no longer exists. Please refresh the page."));
            if (parent.getParentPaymentId() != null) {
                parent = miscPaymentRepository.findById(parent.getParentPaymentId())
                        .orElseThrow(() -> new IllegalArgumentException("Original payment record no longer exists. Please refresh the page."));
            }
            request.setStudentName(parent.getStudentName());
            request.setContactNumber(parent.getContactNumber());
            request.setBatch(parent.getBatch());
            request.setCourseId(parent.getCourseId());
            request.setCollegeName(parent.getCollegeName());
            request.setFeeType(parent.getFeeType());
        }
        validate(request, parent != null);

        Course course = null;
        if (request.getCourseId() != null) {
            course = courseRepository.findById(request.getCourseId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected course does not exist."));
        }

        if (paymentModeService.findByCode(request.getPaymentMode()).isEmpty()) {
            throw new IllegalArgumentException("Selected payment mode does not exist.");
        }

        MiscPayment payment = MiscPayment.builder()
                .parentPaymentId(parent != null ? parent.getPaymentId() : null)
                .studentName(trimToNull(request.getStudentName()))
                .contactNumber(normalizeOptionalLegacyText(request.getContactNumber()))
                .batch(normalizeOptionalLegacyText(request.getBatch()))
                .courseId(course != null ? course.getCourseId() : null)
                .courseName(parent != null ? parent.getCourseName() : normalizeOptionalCourseName(course))
                .collegeName(normalizeOptionalLegacyText(request.getCollegeName()))
                .feeType(trimToNull(request.getFeeType()))
                .amount(request.getAmount())
                .paymentMode(trimToNull(request.getPaymentMode()))
                .paymentType(normalizePaymentType(request.getPaymentType()))
                .paymentDate(request.getPaymentDate())
                .remark(trimToNull(request.getRemark()))
                .createdBy(trimToNull(request.getCreatedBy()))
                .build();
        applyReceipt(payment, request.getReceipt());

        payment = miscPaymentRepository.save(payment);
        payment = miscPaymentRepository.save(invoiceService.generateInvoiceForMiscPayment(payment));
        return toDto(payment);
    }

    @Transactional
    public MiscPaymentDto update(Long paymentId, MiscPaymentRequest request) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment id is required.");
        }
        MiscPayment existing = miscPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Miscellaneous payment not found."));
        validate(request, existing.getParentPaymentId() != null);

        Course course = null;
        if (request.getCourseId() != null) {
            course = courseRepository.findById(request.getCourseId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected course does not exist."));
        }

        if (paymentModeService.findByCode(request.getPaymentMode()).isEmpty()) {
            throw new IllegalArgumentException("Selected payment mode does not exist.");
        }

        existing.setStudentName(trimToNull(request.getStudentName()));
        existing.setContactNumber(normalizeOptionalLegacyText(request.getContactNumber()));
        existing.setBatch(normalizeOptionalLegacyText(request.getBatch()));
        existing.setCourseId(course != null ? course.getCourseId() : null);
        existing.setCourseName(normalizeOptionalCourseName(course));
        existing.setCollegeName(normalizeOptionalLegacyText(request.getCollegeName()));
        existing.setFeeType(trimToNull(request.getFeeType()));
        existing.setAmount(request.getAmount());
        existing.setPaymentMode(trimToNull(request.getPaymentMode()));
        existing.setPaymentType(normalizePaymentType(request.getPaymentType()));
        existing.setPaymentDate(request.getPaymentDate());
        existing.setRemark(trimToNull(request.getRemark()));
        existing.setCreatedBy(trimToNull(request.getCreatedBy()));
        applyReceipt(existing, request.getReceipt());

        existing = miscPaymentRepository.save(existing);
        existing = miscPaymentRepository.save(invoiceService.generateInvoiceForMiscPayment(existing));
        return toDto(existing);
    }

    @Transactional
    public void delete(Long paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment id is required.");
        }
        MiscPayment existing = miscPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Miscellaneous payment not found."));
        if (miscPaymentRepository.existsByParentPaymentId(paymentId)) {
            throw new IllegalArgumentException("Cannot delete this record while additional payments are linked to it. Delete the additional payments first.");
        }
        safeDeleteInvoice(existing.getInvoiceFilePath());
        miscPaymentRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public List<MiscPaymentDto> listRecent() {
        return miscPaymentRepository.findTop20ByOrderByPaymentDateDescCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public MiscPaymentPageResponse search(
            String q,
            Long courseId,
            String batch,
            String feeType,
            String paymentMode,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "paymentDate", "paymentId"));

        Specification<MiscPayment> spec = Specification.where(keywordLike(q))
                .and(courseIdEquals(courseId))
                .and(batchEquals(batch))
                .and(feeTypeEquals(feeType))
                .and(paymentModeEquals(paymentMode))
                .and(paymentDateGte(startDate))
                .and(paymentDateLte(endDate));

        Page<MiscPayment> result = miscPaymentRepository.findAll(spec, pageable);
        return MiscPaymentPageResponse.builder()
                .content(result.getContent().stream().map(this::toDto).toList())
                .number(result.getNumber())
                .size(result.getSize())
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .numberOfElements(result.getNumberOfElements())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public MiscPaymentPageResponse searchRecords(String q, Long courseId, String batch,
            String feeType, String otherFeeType, String paymentMode, LocalDate startDate,
            LocalDate endDate, int page, int size) {
        Specification<MiscPayment> filters = Specification.where(keywordLike(q))
                .and(courseIdEquals(courseId)).and(batchEquals(batch))
                .and(recordFeeType(feeType, otherFeeType)).and(paymentModeEquals(paymentMode))
                .and(paymentDateGte(startDate)).and(paymentDateLte(endDate));
        Specification<MiscPayment> records = (root, query, cb) -> {
            var matching = query.subquery(Long.class);
            var payment = matching.from(MiscPayment.class);
            var belongsToRecord = cb.or(cb.equal(payment.get("paymentId"), root.get("paymentId")),
                    cb.equal(payment.get("parentPaymentId"), root.get("paymentId")));
            var filter = filters.toPredicate(payment, query, cb);
            matching.select(payment.get("paymentId")).where(filter == null
                    ? belongsToRecord : cb.and(belongsToRecord, filter));
            return cb.and(cb.isNull(root.get("parentPaymentId")), cb.exists(matching));
        };
        Page<MiscPayment> result = miscPaymentRepository.findAll(records, paymentPage(page, size));
        var totals = result.isEmpty() ? java.util.Map.<Long, MiscPaymentRepository.RecordTotal>of()
                : miscPaymentRepository.summarizeRecords(result.getContent().stream()
                        .map(MiscPayment::getPaymentId).toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(MiscPaymentRepository.RecordTotal::getRecordId,
                                java.util.function.Function.identity()));
        var content = result.getContent().stream().map(payment -> {
            var dto = toDto(payment);
            var total = totals.get(payment.getPaymentId());
            dto.setTotalAmount(total == null ? payment.getAmount() : total.getTotalAmount());
            dto.setPaymentCount(total == null ? 1L : total.getPaymentCount());
            return dto;
        }).toList();
        return pageResponse(result, content);
    }

    @Transactional(readOnly = true)
    public MiscPaymentPageResponse history(Long recordId, int page, int size) {
        var record = miscPaymentRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Payment record no longer exists. Please refresh the page."));
        Long rootId = record.getParentPaymentId() == null ? record.getPaymentId() : record.getParentPaymentId();
        Specification<MiscPayment> linked = (root, query, cb) -> cb.or(
                cb.equal(root.get("paymentId"), rootId), cb.equal(root.get("parentPaymentId"), rootId));
        Page<MiscPayment> result = miscPaymentRepository.findAll(linked, paymentPage(page, size));
        return pageResponse(result, result.getContent().stream().map(this::toDto).toList());
    }

    private Pageable paymentPage(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "paymentDate", "paymentId"));
    }

    private MiscPaymentPageResponse pageResponse(Page<MiscPayment> result, List<MiscPaymentDto> content) {
        return MiscPaymentPageResponse.builder().content(content).number(result.getNumber())
                .size(result.getSize()).totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .numberOfElements(result.getNumberOfElements()).first(result.isFirst()).last(result.isLast()).build();
    }

    private Specification<MiscPayment> recordFeeType(String feeType, String otherFeeType) {
        if (!"other".equals(canonicalizeInput(feeType, true))) return feeTypeEquals(feeType);
        Specification<MiscPayment> custom = (root, query, cb) -> {
            var standard = List.of("Exam Fees", "Book Fees", "Library Fees", "Practical Fees", "Form Fees", "Miscellaneous")
                    .stream().flatMap(value -> java.util.stream.Stream.of(
                            canonicalizeInput(value, false), canonicalizeInput(value, true))).distinct().toList();
            var value = canonicalizeField(cb, root.get("feeType"));
            return cb.and(cb.notEqual(value, ""), cb.not(value.in(standard)));
        };
        return custom.and(feeTypeEquals(otherFeeType));
    }

    private MiscPaymentDto toDto(MiscPayment payment) {
        return MiscPaymentDto.builder()
                .paymentId(payment.getPaymentId())
                .parentPaymentId(payment.getParentPaymentId())
                .studentName(payment.getStudentName())
                .contactNumber(payment.getContactNumber())
                .batch(payment.getBatch())
                .courseId(payment.getCourseId())
                .courseName(blankToNull(payment.getCourseName()))
                .collegeName(payment.getCollegeName())
                .feeType(payment.getFeeType())
                .amount(payment.getAmount())
                .paymentMode(payment.getPaymentMode())
                .paymentType(payment.getPaymentType())
                .paymentDate(payment.getPaymentDate())
                .receiptName(payment.getReceiptName())
                .receiptUrl(payment.getReceiptStorageUrl())
                .invoiceNumber(payment.getInvoiceNumber())
                .invoiceUrl(payment.getInvoiceDownloadUrl())
                .remark(payment.getRemark())
                .createdBy(payment.getCreatedBy())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private void applyReceipt(MiscPayment payment, UploadRequest receipt) {
        if (payment == null || receipt == null || !StringUtils.hasText(receipt.getStorageUrl())) {
            return;
        }
        payment.setReceiptName(trimToNull(receipt.getFilename()));
        payment.setReceiptMimeType(trimToNull(receipt.getMimeType()));
        payment.setReceiptSizeBytes(receipt.getSizeBytes());
        payment.setReceiptStorageUrl(trimToNull(receipt.getStorageUrl()));
        payment.setReceiptSha256(trimToNull(receipt.getSha256()));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeOptionalCourseName(Course course) {
        return course != null && StringUtils.hasText(course.getName()) ? course.getName().trim() : "";
    }

    private String normalizeOptionalLegacyText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private void validate(MiscPaymentRequest request, boolean allowNegative) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (!StringUtils.hasText(request.getStudentName())) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (StringUtils.hasText(request.getContactNumber()) && !request.getContactNumber().trim().matches("^[6-9][0-9]{9}$")) {
            throw new IllegalArgumentException("Contact number must be a valid 10 digit mobile number.");
        }
        if (!StringUtils.hasText(request.getFeeType())) {
            throw new IllegalArgumentException("Fees type is required.");
        }
        if (request.getAmount() == null || request.getAmount().signum() == 0
                || (!allowNegative && request.getAmount().signum() < 0)) {
            throw new IllegalArgumentException(allowNegative
                    ? "Payment amount must be non-zero. Use a negative amount for a refund."
                    : "Fees amount must be greater than zero.");
        }
        if (request.getAmount().stripTrailingZeros().scale() > 2
                || request.getAmount().abs().compareTo(new java.math.BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Enter a valid amount with at most two decimal places.");
        }
        if (!StringUtils.hasText(request.getPaymentMode())) {
            throw new IllegalArgumentException("Payment mode is required.");
        }
        if (!StringUtils.hasText(request.getPaymentType())) {
            throw new IllegalArgumentException("Payment type is required.");
        }
        if (request.getPaymentDate() == null) {
            throw new IllegalArgumentException("Payment date is required.");
        }
    }

    private String normalizePaymentType(String paymentType) {
        if (!StringUtils.hasText(paymentType)) {
            throw new IllegalArgumentException("Payment type is required.");
        }
        String trimmed = paymentType.trim();
        String normalized = trimmed.toLowerCase(Locale.ENGLISH);
        return switch (normalized) {
            case "cash" -> "Cash";
            case "cheque", "check" -> "Cheque";
            case "online" -> "Online";
            // Preserve free-text values from the "Others" payment-type option.
            default -> trimmed;
        };
    }

    private Specification<MiscPayment> keywordLike(String q) {
        if (!StringUtils.hasText(q)) {
            return null;
        }
        String like = "%" + canonicalizeInput(q, false) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(canonicalizeField(cb, root.get("studentName")), like),
                cb.like(canonicalizeField(cb, root.get("contactNumber")), like),
                cb.like(canonicalizeField(cb, root.get("courseName")), like),
                cb.like(canonicalizeField(cb, root.get("collegeName")), like),
                cb.like(canonicalizeField(cb, root.get("feeType")), like),
                cb.like(canonicalizeField(cb, root.get("paymentMode")), like),
                cb.like(canonicalizeField(cb, root.get("batch")), like)
        );
    }

    private Specification<MiscPayment> courseIdEquals(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("courseId"), courseId);
    }

    private Specification<MiscPayment> batchEquals(String batch) {
        if (!StringUtils.hasText(batch)) {
            return null;
        }
        String normalizedBatch = canonicalizeInput(batch, false);
        return (root, query, cb) -> cb.equal(canonicalizeField(cb, root.get("batch")), normalizedBatch);
    }

    private Specification<MiscPayment> feeTypeEquals(String feeType) {
        if (!StringUtils.hasText(feeType)) {
            return null;
        }
        String normalizedFeeType = canonicalizeInput(feeType, true);
        return (root, query, cb) -> cb.like(canonicalizeField(cb, root.get("feeType")), "%" + normalizedFeeType + "%");
    }

    private Specification<MiscPayment> paymentModeEquals(String paymentMode) {
        if (!StringUtils.hasText(paymentMode)) {
            return null;
        }
        String normalizedPaymentMode = canonicalizeInput(paymentMode, false);
        return (root, query, cb) -> cb.equal(canonicalizeField(cb, root.get("paymentMode")), normalizedPaymentMode);
    }

    private Specification<MiscPayment> paymentDateGte(LocalDate startDate) {
        if (startDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("paymentDate"), startDate);
    }

    private Specification<MiscPayment> paymentDateLte(LocalDate endDate) {
        if (endDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("paymentDate"), endDate);
    }

    private String canonicalizeInput(String value, boolean singularize) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .replace("/", "");
        if (singularize && normalized.endsWith("s")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Expression<String> canonicalizeField(CriteriaBuilder cb, Expression<String> expression) {
        Expression<String> value = cb.lower(cb.trim(cb.coalesce(expression, "")));
        value = cb.function("replace", String.class, value, cb.literal(" "), cb.literal(""));
        value = cb.function("replace", String.class, value, cb.literal("-"), cb.literal(""));
        value = cb.function("replace", String.class, value, cb.literal("_"), cb.literal(""));
        value = cb.function("replace", String.class, value, cb.literal("/"), cb.literal(""));
        return value;
    }

    private void safeDeleteInvoice(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            if (r2InvoiceStorageService.isEnabled()) {
                String r2Key = r2InvoiceStorageService.extractKey(filePath);
                if (StringUtils.hasText(r2Key)) {
                    r2InvoiceStorageService.delete(r2Key);
                    return;
                }
            }
            Files.deleteIfExists(Path.of(filePath));
        } catch (Exception ignored) {
        }
    }
}
