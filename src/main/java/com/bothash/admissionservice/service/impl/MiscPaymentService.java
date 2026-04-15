package com.bothash.admissionservice.service.impl;

import java.util.List;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;

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
        validate(request);

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Selected course does not exist."));

        if (paymentModeService.findByCode(request.getPaymentMode()).isEmpty()) {
            throw new IllegalArgumentException("Selected payment mode does not exist.");
        }

        MiscPayment payment = MiscPayment.builder()
                .studentName(trimToNull(request.getStudentName()))
                .contactNumber(trimToNull(request.getContactNumber()))
                .batch(trimToNull(request.getBatch()))
                .courseId(course.getCourseId())
                .courseName(course.getName())
                .collegeName(trimToNull(request.getCollegeName()))
                .feeType(trimToNull(request.getFeeType()))
                .amount(request.getAmount())
                .paymentMode(trimToNull(request.getPaymentMode()))
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
        validate(request);

        MiscPayment existing = miscPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Miscellaneous payment not found."));

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Selected course does not exist."));

        if (paymentModeService.findByCode(request.getPaymentMode()).isEmpty()) {
            throw new IllegalArgumentException("Selected payment mode does not exist.");
        }

        existing.setStudentName(trimToNull(request.getStudentName()));
        existing.setContactNumber(trimToNull(request.getContactNumber()));
        existing.setBatch(trimToNull(request.getBatch()));
        existing.setCourseId(course.getCourseId());
        existing.setCourseName(course.getName());
        existing.setCollegeName(trimToNull(request.getCollegeName()));
        existing.setFeeType(trimToNull(request.getFeeType()));
        existing.setAmount(request.getAmount());
        existing.setPaymentMode(trimToNull(request.getPaymentMode()));
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

    private MiscPaymentDto toDto(MiscPayment payment) {
        return MiscPaymentDto.builder()
                .paymentId(payment.getPaymentId())
                .studentName(payment.getStudentName())
                .contactNumber(payment.getContactNumber())
                .batch(payment.getBatch())
                .courseId(payment.getCourseId())
                .courseName(payment.getCourseName())
                .collegeName(payment.getCollegeName())
                .feeType(payment.getFeeType())
                .amount(payment.getAmount())
                .paymentMode(payment.getPaymentMode())
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

    private void validate(MiscPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (!StringUtils.hasText(request.getStudentName())) {
            throw new IllegalArgumentException("Student name is required.");
        }
        if (!StringUtils.hasText(request.getContactNumber()) || !request.getContactNumber().trim().matches("^[6-9][0-9]{9}$")) {
            throw new IllegalArgumentException("Contact number must be a valid 10 digit mobile number.");
        }
        if (!StringUtils.hasText(request.getBatch())) {
            throw new IllegalArgumentException("Batch is required.");
        }
        if (request.getCourseId() == null) {
            throw new IllegalArgumentException("Course is required.");
        }
        if (!StringUtils.hasText(request.getFeeType())) {
            throw new IllegalArgumentException("Fees type is required.");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Fees amount must be greater than zero.");
        }
        if (!StringUtils.hasText(request.getPaymentMode())) {
            throw new IllegalArgumentException("Payment mode is required.");
        }
        if (request.getPaymentDate() == null) {
            throw new IllegalArgumentException("Payment date is required.");
        }
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
