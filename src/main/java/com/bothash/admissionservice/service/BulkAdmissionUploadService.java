
package com.bothash.admissionservice.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.bothash.admissionservice.dto.BulkUploadResponse;
import com.bothash.admissionservice.dto.BulkUploadHistoryItem;
import com.bothash.admissionservice.dto.CreateAdmissionRequest;
import com.bothash.admissionservice.dto.CreateStudentFeeCommentRequest;
import com.bothash.admissionservice.dto.CreateStudentFeeScheduleRequest;
import com.bothash.admissionservice.dto.OfficeUpdateRequest;
import com.bothash.admissionservice.dto.OtherPaymentFieldValueRequest;
import com.bothash.admissionservice.dto.OtherPaymentValueEntryDto;
import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.AdmissionDocument;
import com.bothash.admissionservice.entity.Address;
import com.bothash.admissionservice.entity.BatchMaster;
import com.bothash.admissionservice.entity.BranchMaster;
import com.bothash.admissionservice.entity.BulkUploadJob;
import com.bothash.admissionservice.entity.College;
import com.bothash.admissionservice.entity.CollegeCourse;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.CourseFeeTemplate;
import com.bothash.admissionservice.entity.CourseFeeTemplateInstallment;
import com.bothash.admissionservice.entity.DocumentType;
import com.bothash.admissionservice.entity.FeeInstallment;
import com.bothash.admissionservice.entity.FeeInstallmentPayment;
import com.bothash.admissionservice.entity.Guardian;
import com.bothash.admissionservice.entity.HscDetails;
import com.bothash.admissionservice.entity.OtherPaymentField;
import com.bothash.admissionservice.entity.SscDetails;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.entity.StudentAddress;
import com.bothash.admissionservice.enumpackage.AdmissionStatus;
import com.bothash.admissionservice.enumpackage.Gender;
import com.bothash.admissionservice.enumpackage.GuardianRelation;
import com.bothash.admissionservice.repository.Admission2Repository;
import com.bothash.admissionservice.repository.AdmissionDocumentRepository;
import com.bothash.admissionservice.repository.BulkUploadJobRepository;
import com.bothash.admissionservice.repository.BatchMasterRepository;
import com.bothash.admissionservice.repository.BranchRepository;
import com.bothash.admissionservice.repository.CollegeRepository;
import com.bothash.admissionservice.repository.CollegeCourseRepository;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.CourseFeeTemplateRepository;
import com.bothash.admissionservice.repository.DocumentTypeRepository;
import com.bothash.admissionservice.repository.FeeInvoiceRepository;
import com.bothash.admissionservice.repository.FeeInstallmentPaymentRepository;
import com.bothash.admissionservice.repository.FeeInstallmentRepository;
import com.bothash.admissionservice.repository.OtherPaymentFieldRepository;
import com.bothash.admissionservice.repository.OtherPaymentFieldOptionRepository;
import com.bothash.admissionservice.repository.StudentRepository;
import com.bothash.admissionservice.service.impl.InvoiceServiceImpl;
import com.bothash.admissionservice.service.impl.PaymentModeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BulkAdmissionUploadService {
    private static final String SHEET_NAME = "Sheet1";
    private static final String JOB_TYPE = "ADMISSIONS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_PROCESSING = "PROCESSING";

    private static final int COL_ADMISSION_DATE = 0;
    private static final int COL_STUDENT_STATUS = 1;
    private static final int COL_ABS_ID = 2;
    private static final int COL_REG_NUMBER = 3;
    private static final int COL_MONTH = 4;
    private static final int COL_BATCH = 5;
    private static final int COL_STUDENT_NAME = 6;
    private static final int COL_GENDER = 7;
    private static final int COL_DOB = 8;
    private static final int COL_BLOOD_GROUP = 9;
    private static final int COL_FATHER_NAME = 10;
    private static final int COL_FATHER_MOBILE = 11;
    private static final int COL_MOTHER_NAME = 12;
    private static final int COL_MOTHER_MOBILE = 13;
    private static final int COL_STUDENT_CONTACT = 14;
    private static final int COL_EMAIL = 15;
    private static final int COL_AADHAAR = 16;
    private static final int COL_ADDRESS = 17;
    private static final int COL_AREA = 18;
    private static final int COL_PINCODE = 19;
    private static final int COL_SSC_BOARD = 20;
    private static final int COL_SSC_REG_NO = 21;
    private static final int COL_SSC_PERCENT = 22;
    private static final int COL_SSC_YEAR = 23;
    private static final int COL_HSC_COLLEGE = 24;
    private static final int COL_HSC_REG_NO = 25;
    private static final int COL_HSC_YEAR = 26;
    private static final int COL_HSC_BOARD = 27;
    private static final int COL_HSC_SUBJECTS = 28;
    private static final int COL_HSC_PERCENT = 29;
    private static final int COL_REFERENCE = 30;
    private static final int COL_BRANCH = 31;
    private static final int COL_LECTURE_BRANCH = 32;
    private static final int COL_COURSE = 33;
    private static final int COL_COURSE_DURATION = 34;
    private static final int COL_COLLEGE_FEES = 35;
    private static final int COL_COLLEGE = 36;
    private static final int COL_DOCUMENT_HANDOVER = 37;
    private static final int COL_PROVISIONAL_LETTER = 38;
    private static final int COL_CONFIRMATION_LETTER = 39;
    private static final int COL_PWD_SCHOLARSHIP = 40;
    private static final int COL_TOTAL_FEES = 41;
    private static final int COL_PAY_MODE = 42;
    private static final int COL_INSTALLMENT1_AMT = 43;
    private static final int COL_INSTALLMENT1_YEAR = 44;
    private static final int COL_INSTALLMENT1_DUE = 45;
    private static final int COL_INSTALLMENT2_AMT = 46;
    private static final int COL_INSTALLMENT2_YEAR = 47;
    private static final int COL_INSTALLMENT2_DUE = 48;
    private static final int COL_INSTALLMENT3_AMT = 49;
    private static final int COL_INSTALLMENT3_YEAR = 50;
    private static final int COL_INSTALLMENT3_DUE = 51;
    private static final int COL_INSTALLMENT4_AMT = 52;
    private static final int COL_INSTALLMENT4_YEAR = 53;
    private static final int COL_INSTALLMENT4_DUE = 54;
    private static final int COL_TOTAL_FEE_RECEIVED = 55;
    private static final int COL_PENDING_FEE = 56;
    private static final int COL_DOC_10TH = 57;
    private static final int COL_DOC_10TH_CERT = 58;
    private static final int COL_DOC_12TH = 59;
    private static final int COL_DOC_LC_TC = 60;
    private static final int COL_DOC_AADHAR = 61;
    private static final int COL_DOC_PHOTO = 62;
    private static final int COL_DOC_MIGRATION = 63;
    private static final int COL_DOC_DIPLOMA = 64;
    private static final int COL_DOC_PART1 = 65;
    private static final int COL_DOC_PART2 = 66;
    private static final int COL_HSC_PCB_PERCENT = 67;
    private static final int COL_OTHER_PAYMENTS_START = 68;
    private static final int COL_OTHER_PAYMENTS_END = 89;
    private static final int COL_PAYMENT_HISTORY_START = 90;
    private static final int COL_PAYMENT_HISTORY_COUNT = 40;
    private static final int COL_LABEL = 210;
    private static final int COL_SCHEDULE = 211;
    private static final int COL_COMMENT = 212;

    private static final String[] TEMPLATE_HEADERS = new String[] {
        "Admission Date",
        "STUDENT STATUS",
        "ABS ID",
        "Registration number",
        "Month",
        "Batch",
        "STUDENT NAME",
        "M/F",
        "DOB",
        "Blood Group",
        "Father's Name",
        "FATHER CONTACT NUMBER",
        "Mother's Name",
        "MOTHER CONTACT NUMBER",
        "STUDENT Contact",
        "Email ID",
        "ADHAR NO",
        "ADDRESS",
        "Address Area",
        "pincode",
        "10th board",
        "10th Reg no",
        "10th %",
        "10th year",
        "12th coll. Name",
        "12th reg no",
        "12th year",
        "12th board",
        "12th sub",
        "12th %",
        "Referance",
        "Branch",
        "LECTURE BRANCH",
        "Course",
        "Course Duration (Years)",
        "college fees",
        "College",
        "Document handover to",
        "Provisional Letter",
        "Confirmation Letter",
        "PWD-Scolarship",
        "TotalFees",
        "Pay Mode",
        "Installments 1ST Amt",
        "Installment 1 Year",
        "Due by",
        "2nd - Amt",
        "Installment 2 Year",
        "Due by",
        "3rd - Amt",
        "Installment 3 Year",
        "Due by",
        "4th-amt",
        "Installment 4 Year",
        "Due by",
        "Total Fee Received",
        "Pending Fee",
        "10th",
        "10th certificate",
        "12th",
        "LC/TC",
        "Aadhar",
        "Photo",
        "Migr.",
        "Diploma certificate",
        "PART1",
        "PART2",
        "12th % PCB",
        "Form Fee",
        "1st year book",
        "2nd year book",
        "Book & App",
        "Bag",
        "1st year Exam Fee",
        "1st year form fill",
        "Pass/Fail",
        "KT Status",
        "2nd year Exam Fee",
        "2nd year form fill",
        "Pass/Fail",
        "KT Status",
        "Apron",
        "Practical Book 1st year",
        "Practical Book 2nd year",
        "Travel 1st year",
        "Travel 2nd year",
        "sessional 1",
        "sessional 2",
        "sessional 3",
        "Top 20",
        "Installment 1 Date",
        "Amount",
        "Paid by",
        "Installment 2 Date",
        "Amount",
        "Paid by",
        "Installment 3 Date",
        "Amount",
        "Paid by",
        "Installment 4 Date",
        "Amount",
        "Paid by",
        "Installment 5 Date",
        "Amount",
        "Paid by",
        "Installment 6 Date",
        "Amount",
        "Paid by",
        "Installment 7 Date",
        "Amount",
        "Paid by",
        "Installment 8 Date",
        "Amount",
        "Paid by",
        "Installment 9 Date",
        "Amount",
        "Paid by",
        "Installment 10 Date",
        "Amount",
        "Paid by",
        "Installment 11 Date",
        "Amount",
        "Paid by",
        "Installment 12 Date",
        "Amount",
        "Paid by",
        "Installment 13 Date",
        "Amount",
        "Paid by",
        "Installment 14 Date",
        "Amount",
        "Paid by",
        "Installment 15 Date",
        "Amount",
        "Paid by",
        "Installment 16 Date",
        "Amount",
        "Paid by",
        "Installment 17 Date",
        "Amount",
        "Paid by",
        "Installment 18 Date",
        "Amount",
        "Paid by",
        "Installment 19 Date",
        "Amount",
        "Paid by",
        "Installment 20 Date",
        "Amount",
        "Paid by",
        "Installment 21 Date",
        "Amount",
        "Paid by",
        "Installment 22 Date",
        "Amount",
        "Paid by",
        "Installment 23 Date",
        "Amount",
        "Paid by",
        "Installment 24 Date",
        "Amount",
        "Paid by",
        "Installment 25 Date",
        "Amount",
        "Paid by",
        "Installment 26 Date",
        "Amount",
        "Paid by",
        "Installment 27 Date",
        "Amount",
        "Paid by",
        "Installment 28 Date",
        "Amount",
        "Paid by",
        "Installment 29 Date",
        "Amount",
        "Paid by",
        "Installment 30 Date",
        "Amount",
        "Paid by",
        "Installment 31 Date",
        "Amount",
        "Paid by",
        "Installment 32 Date",
        "Amount",
        "Paid by",
        "Installment 33 Date",
        "Amount",
        "Paid by",
        "Installment 34 Date",
        "Amount",
        "Paid by",
        "Installment 35 Date",
        "Amount",
        "Paid by",
        "Installment 36 Date",
        "Amount",
        "Paid by",
        "Installment 37 Date",
        "Amount",
        "Paid by",
        "Installment 38 Date",
        "Amount",
        "Paid by",
        "Installment 39 Date",
        "Amount",
        "Paid by",
        "Installment 40 Date",
        "Amount",
        "Paid by",
        "Label",
        "Schedule",
        "Comment"
    };

    private final BulkUploadJobRepository jobRepository;
    private final BatchMasterRepository batchMasterRepository;
    private final StudentRepository studentRepository;
    private final Admission2Repository admissionRepository;
    private final CourseRepository courseRepository;
    private final CourseFeeTemplateRepository courseFeeTemplateRepository;
    private final BranchRepository branchRepository;
    private final CollegeRepository collegeRepository;
    private final CollegeCourseRepository collegeCourseRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final AdmissionDocumentRepository admissionDocumentRepository;
    private final FeeInstallmentRepository feeInstallmentRepository;
    private final FeeInstallmentPaymentRepository feeInstallmentPaymentRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final InvoiceServiceImpl invoiceService;
    private final PaymentModeService paymentModeService;
    private final StudentOtherPaymentValueService studentOtherPaymentValueService;
    private final OtherPaymentFieldRepository otherPaymentFieldRepository;
    private final OtherPaymentFieldOptionRepository otherPaymentFieldOptionRepository;
    private final SscDetailsService sscDetailsService;
    private final HscDetailsService hscDetailsService;
    private final StudentFeeScheduleService studentFeeScheduleService;
    private final StudentFeeCommentService studentFeeCommentService;
    private final Admission2Service admissionService;
    private final PlatformTransactionManager transactionManager;

    public BulkUploadResponse processUpload(MultipartFile file, String uploadedBy, String academicYearLabel) {
        UUID uploadId = UUID.randomUUID();
        BulkUploadJob job = BulkUploadJob.builder()
                .id(uploadId)
                .type(JOB_TYPE)
                .fileName(file != null ? file.getOriginalFilename() : null)
                .uploadedBy(uploadedBy)
                .uploadedAt(LocalDateTime.now())
                .status(STATUS_PROCESSING)
                .totalRows(0)
                .successRows(0)
                .failedRows(0)
                .build();
        jobRepository.save(job);

        List<BulkErrorRow> errorRows = new ArrayList<>();
        int totalRows = 0;
        int successRows = 0;
        int failedRows = 0;

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null && workbook.getNumberOfSheets() > 0) {
                sheet = workbook.getSheetAt(0);
            }
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet '" + SHEET_NAME + "' not found.");
            }
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            Map<String, OtherPaymentField> otherPaymentByLabel = loadOtherPaymentFields();
            Map<Long, List<com.bothash.admissionservice.entity.OtherPaymentFieldOption>> otherPaymentOptionsByField =
                    loadOtherPaymentOptions();
            ColumnMap columnMap = buildColumnMap(sheet, formatter);
            List<String> headerValues = readHeaderValues(sheet, formatter);

            int firstRow = Math.max(sheet.getFirstRowNum() + 1, 1);
            int lastRow = sheet.getLastRowNum();
            for (int rowIdx = firstRow; rowIdx <= lastRow; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isHeaderRow(row, formatter) || isRowBlank(row, formatter)) {
                    continue;
                }
                totalRows++;
                int excelRowNumber = rowIdx + 1;
                try {
                    txTemplate.execute(status -> {
                        processRow(row, formatter, otherPaymentByLabel, otherPaymentOptionsByField, uploadedBy,
                                academicYearLabel, columnMap, headerValues);
                        return null;
                    });
                    successRows++;
                } catch (Exception ex) {
                    failedRows++;
                    String reason = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                    errorRows.add(new BulkErrorRow(excelRowNumber, reason, readRowValues(row, formatter)));
                }
            }
        } catch (Exception ex) {
            failedRows = totalRows > 0 ? failedRows : totalRows;
            String reason = ex.getMessage() != null ? ex.getMessage() : "Upload failed";
            errorRows.add(new BulkErrorRow(0, reason, List.of()));
        }

        String errorReportPath = null;
        if (!errorRows.isEmpty()) {
            errorReportPath = writeErrorReport(uploadId, errorRows);
        }

        job.setTotalRows(totalRows);
        job.setSuccessRows(successRows);
        job.setFailedRows(failedRows);
        job.setStatus(STATUS_COMPLETED);
        job.setErrorReportPath(errorReportPath);
        jobRepository.save(job);

        return BulkUploadResponse.builder()
                .uploadId(uploadId)
                .fileName(job.getFileName())
                .totalRows(totalRows)
                .successRows(successRows)
                .failedRows(failedRows)
                .status(job.getStatus())
                .errorReportAvailable(errorReportPath != null)
                .build();
    }

    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                header.createCell(i).setCellValue(TEMPLATE_HEADERS[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate template", e);
        }
    }

    public List<BulkUploadHistoryItem> listHistory() {
        List<BulkUploadJob> jobs = jobRepository.findTop50ByTypeOrderByUploadedAtDesc(JOB_TYPE);
        List<BulkUploadHistoryItem> items = new ArrayList<>(jobs.size());
        for (BulkUploadJob job : jobs) {
            items.add(BulkUploadHistoryItem.builder()
                    .uploadId(job.getId())
                    .fileName(job.getFileName())
                    .uploadedBy(job.getUploadedBy())
                    .uploadedAt(job.getUploadedAt())
                    .totalRows(job.getTotalRows() != null ? job.getTotalRows() : 0)
                    .successRows(job.getSuccessRows() != null ? job.getSuccessRows() : 0)
                    .failedRows(job.getFailedRows() != null ? job.getFailedRows() : 0)
                    .status(job.getStatus())
                    .errorReportAvailable(StringUtils.hasText(job.getErrorReportPath()))
                    .build());
        }
        return items;
    }

    public Path resolveErrorReport(UUID uploadId) {
        BulkUploadJob job = jobRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));
        if (!StringUtils.hasText(job.getErrorReportPath())) {
            throw new IllegalArgumentException("Error report not available for upload: " + uploadId);
        }
        return Paths.get(job.getErrorReportPath());
    }

    private void processRow(Row row, DataFormatter formatter, Map<String, OtherPaymentField> otherPaymentByLabel,
                            Map<Long, List<com.bothash.admissionservice.entity.OtherPaymentFieldOption>> otherPaymentOptionsByField,
                            String uploadedBy, String academicYearLabel, ColumnMap columnMap,
                            List<String> headerValues) {
        LocalDate admissionDate = parseDate(row, formatter, COL_ADMISSION_DATE);
        if (admissionDate == null) {
            admissionDate = LocalDate.now();
        }
        String studentName = cellValue(row, formatter, COL_STUDENT_NAME);
        if (!StringUtils.hasText(studentName)) {
            throw new IllegalArgumentException("Student name is required");
        }
        String studentMobile = normalizePhone(cellValue(row, formatter, COL_STUDENT_CONTACT));
        if (!StringUtils.hasText(studentMobile)) {
            throw new IllegalArgumentException("Student contact is required");
        }
        Gender gender = parseGender(cellValue(row, formatter, COL_GENDER));
        LocalDate dob = parseDate(row, formatter, COL_DOB);

        String courseRaw = cellValue(row, formatter, COL_COURSE);
        Course course = resolveCourse(courseRaw);
        if (course == null) {
            throw new IllegalArgumentException("Course not found: " + courseRaw);
        }
        int resolvedCourseYears = resolveCourseYears(course);

        String branchRaw = cellValue(row, formatter, COL_BRANCH);
        BranchMaster admissionBranch = resolveOrCreateBranch(branchRaw);
        if (admissionBranch == null) {
            throw new IllegalArgumentException("Branch not found: " + branchRaw);
        }

        String lectureBranchRaw = cellValue(row, formatter, COL_LECTURE_BRANCH);
        BranchMaster lectureBranch = StringUtils.hasText(lectureBranchRaw)
                ? resolveOrCreateBranch(lectureBranchRaw)
                : admissionBranch;
        if (lectureBranch == null) {
            throw new IllegalArgumentException("Lecture branch not found: " + lectureBranchRaw);
        }

        String collegeRaw = cellValue(row, formatter, COL_COLLEGE);
        College college = null;
        if (StringUtils.hasText(collegeRaw)) {
            college = resolveOrCreateCollege(collegeRaw);
            if (college == null) {
                throw new IllegalArgumentException("College not found: " + collegeRaw);
            }
            ensureCollegeCourseMapping(college, course);
        }

        String absId = null;

        String registrationNumber = cellValue(row, formatter, COL_REG_NUMBER);
        if (StringUtils.hasText(registrationNumber)
                && studentRepository.findByRegistrationNumber(registrationNumber.trim()).isPresent()) {
            throw new IllegalArgumentException("Registration number already exists: " + registrationNumber);
        }

        Student student = upsertStudent(row, formatter, studentName, studentMobile, gender, dob, course, absId,
                registrationNumber, headerValues);

        CreateAdmissionRequest admissionRequest = buildAdmissionRequest(row, formatter, student, course, college,
                admissionBranch, lectureBranch, admissionDate, academicYearLabel);
        Admission2 admission = admissionService.createAdmission(admissionRequest);

        String statusRaw = cellValue(row, formatter, COL_STUDENT_STATUS);
        AdmissionStatus status = parseAdmissionStatus(statusRaw);
        if (status != null) {
            admission.setStatus(status);
            admissionRepository.save(admission);
        }

        applyDocumentChecklist(admission, row, formatter);
        applyOtherPayments(student, row, formatter, otherPaymentByLabel, otherPaymentOptionsByField, headerValues);
        applyInstallmentPlanFromCourse(admission, row, formatter, course, admissionDate);
        applyPaymentHistory(admission, row, formatter, uploadedBy, columnMap);
        applyFeeScheduleAndComment(student, row, formatter, uploadedBy);
    }

    private Student upsertStudent(Row row, DataFormatter formatter, String studentName, String studentMobile,
                                  Gender gender, LocalDate dob, Course course, String absId,
                                  String registrationNumber, List<String> headerValues) {
        Student existing = studentRepository.findByMobile(studentMobile.trim());
        String bloodGroup = cellValue(row, formatter, COL_BLOOD_GROUP);
        String email = cellValue(row, formatter, COL_EMAIL);
        String aadhaar = cellValue(row, formatter, COL_AADHAAR);
        String batch = resolveBatchCode(cellValue(row, formatter, COL_BATCH));
        String fatherName = cellValue(row, formatter, COL_FATHER_NAME);
        String fatherMobile = normalizePhone(cellValue(row, formatter, COL_FATHER_MOBILE));
        String motherName = cellValue(row, formatter, COL_MOTHER_NAME);
        String motherMobile = normalizePhone(cellValue(row, formatter, COL_MOTHER_MOBILE));
        String addressLine = cellValue(row, formatter, COL_ADDRESS);
        String area = cellValue(row, formatter, COL_AREA);
        String pincode = cellValue(row, formatter, COL_PINCODE);

        Student student = existing != null ? existing : new Student();
        student.setFullName(studentName);
        student.setMobile(studentMobile.trim());
        student.setGender(gender);
        student.setDob(dob);
        student.setEmail(email);
        student.setAadhaar(aadhaar);
        student.setBloodGroup(bloodGroup);
        student.setBatch(batch);
        student.setRegistrationNumber(StringUtils.hasText(registrationNumber) ? registrationNumber.trim() : null);
        student.setCourse(course);
        // leave absId empty so Admission2ServiceImpl can auto-generate it

        if (StringUtils.hasText(addressLine)) {
            Address addr = Address.builder()
                    .line1(addressLine)
                    .area(area)
                    .pincode(pincode)
                    .build();
            Optional<StudentAddress> currentOpt = student.getAddresses().stream()
                    .filter(a -> "current".equalsIgnoreCase(a.getType()))
                    .findFirst();
            if (currentOpt.isPresent()) {
                currentOpt.get().setAddress(addr);
            } else {
                StudentAddress sa = StudentAddress.builder()
                        .student(student)
                        .address(addr)
                        .type("current")
                        .build();
                student.getAddresses().add(sa);
            }
        }

        Map<GuardianRelation, Guardian> incoming = new HashMap<>();
        if (StringUtils.hasText(fatherName)) {
            incoming.put(GuardianRelation.Father, Guardian.builder()
                    .relation(GuardianRelation.Father)
                    .fullName(fatherName)
                    .mobile(fatherMobile)
                    .build());
        }
        if (StringUtils.hasText(motherName)) {
            incoming.put(GuardianRelation.Mother, Guardian.builder()
                    .relation(GuardianRelation.Mother)
                    .fullName(motherName)
                    .mobile(motherMobile)
                    .build());
        }

        student.getGuardians().removeIf(g -> incoming.get(g.getRelation()) == null);
        for (Map.Entry<GuardianRelation, Guardian> entry : incoming.entrySet()) {
            Guardian existingGuardian = student.getGuardians().stream()
                    .filter(g -> g.getRelation() == entry.getKey())
                    .findFirst()
                    .orElse(null);
            if (existingGuardian != null) {
                Guardian incomingGuardian = entry.getValue();
                existingGuardian.setFullName(incomingGuardian.getFullName());
                existingGuardian.setMobile(incomingGuardian.getMobile());
            } else {
                Guardian newGuardian = entry.getValue();
                newGuardian.setStudent(student);
                student.getGuardians().add(newGuardian);
            }
        }

        student = studentRepository.save(student);
        upsertSscDetails(student, row, formatter);
        upsertHscDetails(student, row, formatter, headerValues);
        return student;
    }

    private void upsertSscDetails(Student student, Row row, DataFormatter formatter) {
        String board = cellValue(row, formatter, COL_SSC_BOARD);
        Integer year = parseInteger(cellValue(row, formatter, COL_SSC_YEAR));
        Double percent = parseDouble(cellValue(row, formatter, COL_SSC_PERCENT));
        if (!StringUtils.hasText(board) || year == null || percent == null) {
            return;
        }
        SscDetails ssc = new SscDetails();
        ssc.setBoard(board);
        ssc.setPassingYear(year);
        ssc.setPercentage(percent);
        ssc.setRegistrationNumber(cellValue(row, formatter, COL_SSC_REG_NO));
        sscDetailsService.saveOrUpdateByStudent(student.getStudentId(), ssc);
    }

    private void upsertHscDetails(Student student, Row row, DataFormatter formatter, List<String> headerValues) {
        String collegeName = cellValueByHeader(row, formatter, headerValues, TEMPLATE_HEADERS[COL_HSC_COLLEGE],
                COL_HSC_COLLEGE);
        Integer year = parseInteger(cellValueByHeader(row, formatter, headerValues, TEMPLATE_HEADERS[COL_HSC_YEAR],
                COL_HSC_YEAR));
        if (!StringUtils.hasText(collegeName) || year == null) {
            return;
        }
        String subjectsRaw = cellValueByHeader(row, formatter, headerValues, TEMPLATE_HEADERS[COL_HSC_SUBJECTS],
                COL_HSC_SUBJECTS);
        HscSubjectMarks parsed = parseHscSubjects(subjectsRaw);
        HscDetails hsc = new HscDetails();
        hsc.setCollegeName(collegeName);
        hsc.setPassingYear(year);
        hsc.setRegistrationNumber(cellValueByHeader(row, formatter, headerValues, TEMPLATE_HEADERS[COL_HSC_REG_NO],
                COL_HSC_REG_NO));
        hsc.setSubjects(parsed != null && StringUtils.hasText(parsed.subjects())
                ? parsed.subjects()
                : subjectsRaw);
        hsc.setPercentage(parseDouble(cellValueByHeader(row, formatter, headerValues, TEMPLATE_HEADERS[COL_HSC_PERCENT],
                COL_HSC_PERCENT)));
        if (parsed != null) {
            hsc.setPhysicsMarks(parsed.physics());
            hsc.setChemistryMarks(parsed.chemistry());
            hsc.setBiologyMarks(parsed.biology());
        }
        Double pcb = parseDouble(cellValueByHeader(row, formatter, headerValues,
                TEMPLATE_HEADERS[COL_HSC_PCB_PERCENT], COL_HSC_PCB_PERCENT));
        if (pcb == null && parsed != null && parsed.physics() != null && parsed.chemistry() != null
                && parsed.biology() != null) {
            pcb = (parsed.physics() + parsed.chemistry() + parsed.biology()) / 3.0;
        }
        hsc.setPcbPercentage(pcb);
        hscDetailsService.saveOrUpdateByStudent(student.getStudentId(), hsc);
    }

    private CreateAdmissionRequest buildAdmissionRequest(Row row, DataFormatter formatter, Student student, Course course,
                                                         College college, BranchMaster admissionBranch,
                                                         BranchMaster lectureBranch, LocalDate admissionDate,
                                                         String academicYearLabel) {
        BigDecimal totalFees = parseBigDecimal(cellValue(row, formatter, COL_TOTAL_FEES));
        if (totalFees == null && course != null && course.getCourseFeeTemplate() != null) {
            totalFees = course.getCourseFeeTemplate().getTotalAmount();
        }
        Double totalFeesValue = totalFees != null ? totalFees.doubleValue() : null;
        String batch = resolveBatchCode(cellValue(row, formatter, COL_BATCH));
        String reference = cellValue(row, formatter, COL_REFERENCE);
        String registrationNumber = cellValue(row, formatter, COL_REG_NUMBER);

        OfficeUpdateRequest office = OfficeUpdateRequest.builder()
                .batch(batch)
                .registrationNumber(StringUtils.hasText(registrationNumber) ? registrationNumber.trim() : null)
                .referenceName(reference)
                .dateOfAdmission(admissionDate)
                .build();

        int installments = countInstallmentsFromCourse(course);

        return CreateAdmissionRequest.builder()
                .studentId(student.getStudentId())
                .academicYearLabel(academicYearLabel)
                .courseCode(course.getCourseId())
                .collegeId(college != null ? college.getCollegeId() : null)
                .totalFees(totalFeesValue)
                .discount(0.0)
                .noOfInstallments(installments)
                .formDate(admissionDate)
                .formNo(StringUtils.hasText(registrationNumber) ? registrationNumber.trim() : null)
                .admissionBranchId(admissionBranch.getId())
                .lectureBranchId(lectureBranch.getId())
                .officeUpdateRequest(office)
                .build();
    }

    private void applyDocumentChecklist(Admission2 admission, Row row, DataFormatter formatter) {
        Map<Integer, String> docColumns = Map.of(
                COL_DOC_10TH, "10th",
                COL_DOC_10TH_CERT, "10th certificate",
                COL_DOC_12TH, "12th",
                COL_DOC_LC_TC, "LC/TC",
                COL_DOC_AADHAR, "Aadhar",
                COL_DOC_PHOTO, "Photo",
                COL_DOC_MIGRATION, "Migr.",
                COL_DOC_DIPLOMA, "Diploma certificate",
                COL_DOC_PART1, "PART1",
                COL_DOC_PART2, "PART2"
        );

        for (Map.Entry<Integer, String> entry : docColumns.entrySet()) {
            String raw = cellValue(row, formatter, entry.getKey());
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            boolean received = raw.trim().toUpperCase(Locale.ENGLISH).startsWith("Y");
            DocumentType docType = resolveDocumentType(entry.getValue());
            if (docType == null) {
                continue;
            }
            AdmissionDocument doc = admissionDocumentRepository
                    .findByAdmissionAdmissionIdAndDocTypeDocTypeId(admission.getAdmissionId(), docType.getDocTypeId())
                    .orElseGet(() -> {
                        AdmissionDocument d = new AdmissionDocument();
                        d.setAdmission(admission);
                        d.setDocType(docType);
                        return d;
                    });
            doc.setReceived(received);
            admissionDocumentRepository.save(doc);
        }
    }

    private void applyOtherPayments(Student student, Row row, DataFormatter formatter,
                                    Map<String, OtherPaymentField> otherPaymentByLabel,
                                    Map<Long, List<com.bothash.admissionservice.entity.OtherPaymentFieldOption>> otherPaymentOptionsByField,
                                    List<String> headerValues) {
        if (otherPaymentByLabel.isEmpty()) {
            return;
        }
        List<String> headers = headerValues != null ? headerValues : List.of();
        String ignoreAbsId = normalize(TEMPLATE_HEADERS[COL_ABS_ID]);
        String ignoreMonth = normalize(TEMPLATE_HEADERS[COL_MONTH]);
        String ignoreCollegeFees = normalize(TEMPLATE_HEADERS[COL_COLLEGE_FEES]);
        Map<Long, List<OtherPaymentValueEntryDto>> entriesByField = new HashMap<>();
        int totalCols = Math.max(TEMPLATE_HEADERS.length, headers.size());
        for (int col = 0; col < totalCols; col++) {
            String header = col < headers.size() ? headers.get(col) : null;
            if (!StringUtils.hasText(header) && col < TEMPLATE_HEADERS.length) {
                header = TEMPLATE_HEADERS[col];
            }
            if (!StringUtils.hasText(header)) {
                continue;
            }
            String key = normalize(header);
            if (ignoreAbsId.equals(key) || ignoreMonth.equals(key) || ignoreCollegeFees.equals(key)) {
                continue;
            }
            OtherPaymentField field = otherPaymentByLabel.get(key);
            if (field == null) {
                continue;
            }
            String value = cellValue(row, formatter, col);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            OtherPaymentValueEntryDto entry = resolveOtherPaymentEntry(field, value.trim(), otherPaymentOptionsByField);
            if (entry == null) {
                continue;
            }
            entriesByField.computeIfAbsent(field.getId(), id -> new ArrayList<>()).add(entry);
        }
        List<OtherPaymentFieldValueRequest> requests = new ArrayList<>();
        for (Map.Entry<Long, List<OtherPaymentValueEntryDto>> entry : entriesByField.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            requests.add(OtherPaymentFieldValueRequest.builder()
                    .fieldId(entry.getKey())
                    .entries(entry.getValue())
                    .build());
        }
        if (!requests.isEmpty()) {
            studentOtherPaymentValueService.saveValues(student.getStudentId(), requests);
        }
    }

    private OtherPaymentValueEntryDto resolveOtherPaymentEntry(OtherPaymentField field, String rawValue,
            Map<Long, List<com.bothash.admissionservice.entity.OtherPaymentFieldOption>> otherPaymentOptionsByField) {
        if (field == null || !StringUtils.hasText(rawValue)) {
            return null;
        }
        String trimmed = rawValue.trim();
        List<com.bothash.admissionservice.entity.OtherPaymentFieldOption> options = otherPaymentOptionsByField
                .getOrDefault(field.getId(), List.of());
        if (!options.isEmpty()) {
            com.bothash.admissionservice.entity.OtherPaymentFieldOption match = matchOtherPaymentOption(options,
                    trimmed);
            if (match != null) {
                return OtherPaymentValueEntryDto.builder()
                        .optionId(match.getId())
                        .value(match.getValue())
                        .build();
            }
        }
        return OtherPaymentValueEntryDto.builder()
                .value(trimmed)
                .build();
    }

    private com.bothash.admissionservice.entity.OtherPaymentFieldOption matchOtherPaymentOption(
            List<com.bothash.admissionservice.entity.OtherPaymentFieldOption> options, String rawValue) {
        String normalized = normalizeOptionValue(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String normalizedAlt = "YES".equals(normalized) ? "YS" : ("YS".equals(normalized) ? "YES" : normalized);
        for (com.bothash.admissionservice.entity.OtherPaymentFieldOption option : options) {
            if (option == null) {
                continue;
            }
            String optValue = normalizeOptionValue(option.getValue());
            String optLabel = normalizeOptionValue(option.getLabel());
            if (normalized.equals(optValue) || normalized.equals(optLabel)
                    || normalizedAlt.equals(optValue) || normalizedAlt.equals(optLabel)) {
                return option;
            }
        }
        return null;
    }

    private String normalizeOptionValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.trim().toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9]+", "");
        if ("Y".equals(cleaned)) {
            return "YES";
        }
        if ("N".equals(cleaned) || "NO".equals(cleaned)) {
            return "NO";
        }
        return cleaned;
    }

    private List<String> readHeaderValues(Sheet sheet, DataFormatter formatter) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        int lastCellNum = header != null ? header.getLastCellNum() : 0;
        int size = Math.max(TEMPLATE_HEADERS.length, Math.max(0, lastCellNum));
        List<String> headers = new ArrayList<>(size);
        for (int col = 0; col < size; col++) {
            String value = header != null ? cellValue(header, formatter, col) : null;
            if (!StringUtils.hasText(value) && col < TEMPLATE_HEADERS.length) {
                value = TEMPLATE_HEADERS[col];
            }
            headers.add(StringUtils.hasText(value) ? value.trim() : null);
        }
        return headers;
    }

    private String cellValueByHeader(Row row, DataFormatter formatter, List<String> headerValues, String headerLabel,
                                     int fallbackCol) {
        Integer col = findHeaderIndex(headerValues, headerLabel);
        int resolved = col != null ? col : fallbackCol;
        return cellValue(row, formatter, resolved);
    }

    private Integer findHeaderIndex(List<String> headerValues, String headerLabel) {
        if (headerValues == null || headerValues.isEmpty() || !StringUtils.hasText(headerLabel)) {
            return null;
        }
        String target = normalize(headerLabel);
        if (!StringUtils.hasText(target)) {
            return null;
        }
        for (int i = 0; i < headerValues.size(); i++) {
            String value = headerValues.get(i);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (target.equals(normalize(value))) {
                return i;
            }
        }
        return null;
    }

    private Map<Long, List<com.bothash.admissionservice.entity.OtherPaymentFieldOption>> loadOtherPaymentOptions() {
        List<com.bothash.admissionservice.entity.OtherPaymentFieldOption> options = otherPaymentFieldOptionRepository
                .findAll();
        Map<Long, List<com.bothash.admissionservice.entity.OtherPaymentFieldOption>> byField = new HashMap<>();
        for (com.bothash.admissionservice.entity.OtherPaymentFieldOption option : options) {
            if (option == null || option.getField() == null || option.getField().getId() == null) {
                continue;
            }
            byField.computeIfAbsent(option.getField().getId(), id -> new ArrayList<>()).add(option);
        }
        return byField;
    }

    private void applyInstallmentPlanFromCourse(Admission2 admission, Row row, DataFormatter formatter, Course course,
                                                LocalDate admissionDate) {
        String mode = cellValue(row, formatter, COL_PAY_MODE);
        List<CourseFeeTemplateInstallment> templates = getCourseTemplateInstallments(course);
        int courseYears = resolveCourseYears(course);
        Map<Integer, Integer> installmentNoByYear = new HashMap<>();

        if (templates.isEmpty()) {
            admissionService.upsertInstallment(
                    admission.getAdmissionId(),
                    1,
                    1,
                    BigDecimal.ZERO,
                    null,
                    StringUtils.hasText(mode) ? mode.trim() : null,
                    "bulk-upload",
                    "Un Paid"
            );
            return;
        }

        for (CourseFeeTemplateInstallment template : templates) {
            Integer templateYear = normalizeTemplateYear(template.getYearNumber(), courseYears);
            if (templateYear == null) {
                for (int year = 1; year <= courseYears; year++) {
                    addInstallmentFromTemplate(admission, template, year, admissionDate, mode, installmentNoByYear);
                }
                continue;
            }
            addInstallmentFromTemplate(admission, template, templateYear, admissionDate, mode, installmentNoByYear);
        }
    }

    private void applyPaymentHistory(Admission2 admission, Row row, DataFormatter formatter, String uploadedBy,
                                     ColumnMap columnMap) {
        List<FeeInstallment> installments = feeInstallmentRepository
                .findByAdmissionAdmissionIdOrderByStudyYearAscInstallmentNoAsc(admission.getAdmissionId());
        for (int i = 0; i < COL_PAYMENT_HISTORY_COUNT; i++) {
            int dateCol = columnMap.paymentHistoryStart + (i * 3);
            int amountCol = dateCol + 1;
            int paidByCol = dateCol + 2;

            LocalDate paymentDate = parseDate(row, formatter, dateCol);
            BigDecimal amount = parseBigDecimal(cellValue(row, formatter, amountCol));
            String paidBy = cellValue(row, formatter, paidByCol);

            if (paymentDate == null && amount == null) {
                continue;
            }
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            final BigDecimal finalAmount = amount;
            final LocalDate finalPaymentDate = paymentDate;

            if (installments.isEmpty()) {
                FeeInstallment f = new FeeInstallment();
                f.setAdmission(admission);
                f.setStudyYear(1);
                f.setInstallmentNo(1);
                f.setAmountDue(finalAmount);
                f.setDueDate(finalPaymentDate);
                f.setStatus("Paid");
                f.setIsVerified(true);
                installments.add(feeInstallmentRepository.save(f));
            }

            applySequentialPayment(installments, finalAmount, finalPaymentDate, paidBy, uploadedBy);
        }
    }

    private void applySequentialPayment(List<FeeInstallment> installments, BigDecimal amount,
                                        LocalDate paymentDate, String paidBy, String uploadedBy) {
        BigDecimal remaining = amount != null ? amount : BigDecimal.ZERO;
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal totalPending = BigDecimal.ZERO;
        for (FeeInstallment installment : installments) {
            BigDecimal amountDue = installment.getAmountDue() != null ? installment.getAmountDue() : BigDecimal.ZERO;
            BigDecimal amountPaid = installment.getAmountPaid() != null ? installment.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal pending = amountDue.subtract(amountPaid);
            if (pending.compareTo(BigDecimal.ZERO) > 0) {
                totalPending = totalPending.add(pending);
            }
        }
        if (totalPending.compareTo(BigDecimal.ZERO) <= 0) {
            applyPaymentWithoutPlan(installments, remaining, paymentDate, paidBy, uploadedBy);
            return;
        }
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            applySequentialReversal(installments, remaining, paymentDate, paidBy, uploadedBy);
            return;
        }
        for (FeeInstallment installment : installments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal amountDue = installment.getAmountDue() != null ? installment.getAmountDue() : BigDecimal.ZERO;
            BigDecimal amountPaid = installment.getAmountPaid() != null ? installment.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal pending = amountDue.subtract(amountPaid);
            if (pending.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal applied = remaining.min(pending);
            BigDecimal newPaid = amountPaid.add(applied);
            installment.setAmountPaid(newPaid);
            if (newPaid.compareTo(amountDue) >= 0) {
                installment.setStatus("Paid");
                if (installment.getPaidOn() == null) {
                    installment.setPaidOn(paymentDate);
                }
            } else {
                installment.setStatus("Partial Received");
            }
            installment.setIsVerified(true);
            feeInstallmentRepository.save(installment);

            FeeInstallmentPayment payment = FeeInstallmentPayment.builder()
                    .installment(installment)
                    .amount(applied)
                    .paymentMode(resolvePaymentMode(paidBy))
                    .txnRef(StringUtils.hasText(paidBy) ? paidBy.trim() : null)
                    .receivedBy(uploadedBy)
                    .status("Paid")
                    .isVerified(true)
                    .verifiedBy(uploadedBy)
                    .verifiedAt(LocalDateTime.now())
                    .paidOn(paymentDate)
                    .build();
            feeInstallmentPaymentRepository.save(payment);
            createInvoiceForPayment(installment.getAdmission(), installment, payment);

            remaining = remaining.subtract(applied);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            applyPaymentWithoutPlan(installments, remaining, paymentDate, paidBy, uploadedBy);
        }
    }

    private void applyPaymentWithoutPlan(List<FeeInstallment> installments, BigDecimal amount,
                                         LocalDate paymentDate, String paidBy, String uploadedBy) {
        if (installments.isEmpty()) {
            throw new IllegalArgumentException("No installments available for payment allocation.");
        }
        FeeInstallment target = installments.get(installments.size() - 1);
        BigDecimal amountDue = target.getAmountDue() != null ? target.getAmountDue() : BigDecimal.ZERO;
        BigDecimal amountPaid = target.getAmountPaid() != null ? target.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal newDue = amountDue.add(amount);
        BigDecimal newPaid = amountPaid.add(amount);
        target.setAmountDue(newDue);
        target.setAmountPaid(newPaid);
        target.setStatus("Paid");
        target.setIsVerified(true);
        if (target.getPaidOn() == null) {
            target.setPaidOn(paymentDate);
        }
        feeInstallmentRepository.save(target);

        FeeInstallmentPayment payment = FeeInstallmentPayment.builder()
                .installment(target)
                .amount(amount)
                .paymentMode(resolvePaymentMode(paidBy))
                .txnRef(StringUtils.hasText(paidBy) ? paidBy.trim() : null)
                .receivedBy(uploadedBy)
                .status("Paid")
                .isVerified(true)
                .verifiedBy(uploadedBy)
                .verifiedAt(LocalDateTime.now())
                .paidOn(paymentDate)
                .build();
        feeInstallmentPaymentRepository.save(payment);
        createInvoiceForPayment(target.getAdmission(), target, payment);
    }

    private void applySequentialReversal(List<FeeInstallment> installments, BigDecimal amount,
                                         LocalDate paymentDate, String paidBy, String uploadedBy) {
        BigDecimal remaining = amount.abs();
        for (FeeInstallment installment : installments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal amountPaid = installment.getAmountPaid() != null ? installment.getAmountPaid() : BigDecimal.ZERO;
            if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal applied = remaining.min(amountPaid);
            BigDecimal newPaid = amountPaid.subtract(applied);
            installment.setAmountPaid(newPaid);
            BigDecimal amountDue = installment.getAmountDue() != null ? installment.getAmountDue() : BigDecimal.ZERO;
            if (newPaid.compareTo(BigDecimal.ZERO) == 0) {
                installment.setStatus("Un Paid");
                installment.setPaidOn(null);
            } else if (newPaid.compareTo(amountDue) >= 0) {
                installment.setStatus("Paid");
            } else {
                installment.setStatus("Partial Received");
            }
            installment.setIsVerified(true);
            feeInstallmentRepository.save(installment);

            FeeInstallmentPayment payment = FeeInstallmentPayment.builder()
                    .installment(installment)
                    .amount(applied.negate())
                    .paymentMode(resolvePaymentMode(paidBy))
                    .txnRef(StringUtils.hasText(paidBy) ? paidBy.trim() : null)
                    .receivedBy(uploadedBy)
                    .status("Paid")
                    .isVerified(true)
                    .verifiedBy(uploadedBy)
                    .verifiedAt(LocalDateTime.now())
                    .paidOn(paymentDate)
                    .build();
            feeInstallmentPaymentRepository.save(payment);

            remaining = remaining.subtract(applied);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Reversal exceeds paid installment totals.");
        }
    }

    private void applyFeeScheduleAndComment(Student student, Row row, DataFormatter formatter, String uploadedBy) {
        LocalDate scheduleDate = parseDate(row, formatter, COL_SCHEDULE);
        String label = cellValue(row, formatter, COL_LABEL);
        String comment = cellValue(row, formatter, COL_COMMENT);
        BigDecimal pendingFee = parseBigDecimal(cellValue(row, formatter, COL_PENDING_FEE));

        if (scheduleDate != null) {
            CreateStudentFeeScheduleRequest req = CreateStudentFeeScheduleRequest.builder()
                    .studentId(student.getStudentId())
                    .scheduledDate(scheduleDate)
                    .expectedAmount(pendingFee)
                    .scheduleType(StringUtils.hasText(label) ? label.trim() : null)
                    .notes(StringUtils.hasText(comment) ? comment.trim() : null)
                    .createdByUser(StringUtils.hasText(uploadedBy) ? uploadedBy : "bulk-upload")
                    .build();
            studentFeeScheduleService.createSchedule(req);
        }

        if (StringUtils.hasText(comment)) {
            CreateStudentFeeCommentRequest req = CreateStudentFeeCommentRequest.builder()
                    .studentId(student.getStudentId())
                    .comment(comment.trim())
                    .commentedBy(StringUtils.hasText(uploadedBy) ? uploadedBy : "bulk-upload")
                    .commentType("BULK_UPLOAD")
                    .build();
            studentFeeCommentService.createComment(req);
        }
    }

    private DocumentType resolveDocumentType(String label) {
        if (!StringUtils.hasText(label)) {
            return null;
        }
        String trimmed = label.trim();
        DocumentType byName = documentTypeRepository.findByNameIgnoreCase(trimmed).orElse(null);
        if (byName != null) {
            return byName;
        }
        String code = switch (trimmed.toLowerCase(Locale.ENGLISH)) {
            case "10th" -> "SSC";
            case "10th certificate" -> "SSC_CERT";
            case "12th" -> "HSC";
            case "lc/tc" -> "LC_TC";
            case "aadhar" -> "AADHAAR";
            case "photo" -> "PHOTO";
            case "migr." -> "MIG";
            case "diploma certificate" -> "DIPLOMA";
            case "part1" -> "PART1";
            case "part2" -> "PART2";
            default -> null;
        };
        if (code == null) {
            return null;
        }
        return documentTypeRepository.findByCode(code).orElse(null);
    }

    private Course resolveCourse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        return courseRepository.findByCode(trimmed)
                .or(() -> courseRepository.findByNameIgnoreCase(trimmed))
                .orElse(null);
    }

    private Course resolveOrCreateCourse(String raw, Integer years) {
        Course existing = resolveCourse(raw);
        if (existing != null) {
            if (years != null && years > 0 && (existing.getYears() == null || existing.getYears() <= 0)) {
                existing.setYears(years);
                return courseRepository.save(existing);
            }
            return existing;
        }
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        String code = trimmed.length() <= 32 && courseRepository.findByCode(trimmed).isEmpty()
                ? trimmed
                : generateUniqueCourseCode(trimmed);

        CourseFeeTemplate template = new CourseFeeTemplate();
        template.setName(trimmed + " Auto Template");
        template.setTotalAmount(BigDecimal.ZERO);
        template = courseFeeTemplateRepository.save(template);

        Course created = new Course();
        created.setCode(code);
        created.setName(trimmed);
        created.setYears(years != null && years > 0 ? years : 1);
        created.setCourseFeeTemplate(template);
        return courseRepository.save(created);
    }

    private int resolveCourseYears(Course course) {
        return course != null && course.getYears() != null && course.getYears() > 0 ? course.getYears() : 1;
    }

    private List<CourseFeeTemplateInstallment> getCourseTemplateInstallments(Course course) {
        if (course == null || course.getCourseFeeTemplate() == null) {
            return List.of();
        }
        List<CourseFeeTemplateInstallment> installments = course.getCourseFeeTemplate().getInstallments();
        if (installments == null || installments.isEmpty()) {
            return List.of();
        }
        return installments.stream()
                .filter(inst -> inst != null)
                .sorted((a, b) -> {
                    int yearA = a.getYearNumber() != null ? a.getYearNumber() : 0;
                    int yearB = b.getYearNumber() != null ? b.getYearNumber() : 0;
                    int cmp = Integer.compare(yearA, yearB);
                    if (cmp != 0) {
                        return cmp;
                    }
                    int seqA = a.getSequence() != null ? a.getSequence() : 0;
                    int seqB = b.getSequence() != null ? b.getSequence() : 0;
                    return Integer.compare(seqA, seqB);
                })
                .toList();
    }

    private Integer normalizeTemplateYear(Integer yearNumber, int courseYears) {
        if (yearNumber == null || yearNumber <= 0) {
            return null;
        }
        if (yearNumber > courseYears) {
            return courseYears;
        }
        return yearNumber;
    }

    private void addInstallmentFromTemplate(Admission2 admission, CourseFeeTemplateInstallment template, int studyYear,
                                            LocalDate admissionDate, String mode,
                                            Map<Integer, Integer> installmentNoByYear) {
        int installmentNo = installmentNoByYear.getOrDefault(studyYear, 0) + 1;
        installmentNoByYear.put(studyYear, installmentNo);
        BigDecimal amount = template.getAmount() != null ? template.getAmount() : BigDecimal.ZERO;
        LocalDate dueDate = resolveTemplateDueDate(template, admissionDate, studyYear);
        admissionService.upsertInstallment(
                admission.getAdmissionId(),
                studyYear,
                installmentNo,
                amount,
                dueDate,
                StringUtils.hasText(mode) ? mode.trim() : null,
                "bulk-upload",
                "Un Paid"
        );
    }

    private LocalDate resolveTemplateDueDate(CourseFeeTemplateInstallment template, LocalDate admissionDate,
                                             int studyYear) {
        if (template == null || admissionDate == null) {
            return null;
        }
        Integer dueMonth = template.getDueMonth();
        Integer dueDay = template.getDueDayOfMonth();
        if (dueMonth == null || dueDay == null) {
            return null;
        }
        int year = admissionDate.getYear() + Math.max(0, studyYear - 1);
        try {
            java.time.YearMonth ym = java.time.YearMonth.of(year, dueMonth);
            int day = Math.min(dueDay, ym.lengthOfMonth());
            return java.time.LocalDate.of(year, dueMonth, day);
        } catch (Exception ex) {
            return null;
        }
    }

    private int countInstallmentsFromCourse(Course course) {
        int courseYears = resolveCourseYears(course);
        List<CourseFeeTemplateInstallment> templates = getCourseTemplateInstallments(course);
        if (templates.isEmpty()) {
            return 1;
        }
        int count = 0;
        for (CourseFeeTemplateInstallment template : templates) {
            Integer templateYear = normalizeTemplateYear(template.getYearNumber(), courseYears);
            if (templateYear == null) {
                count += courseYears;
            } else {
                count += 1;
            }
        }
        return Math.max(1, count);
    }

    private BranchMaster resolveOrCreateBranch(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        BranchMaster existing = branchRepository.findByCodeIgnoreCase(trimmed)
                .or(() -> branchRepository.findByNameIgnoreCase(trimmed))
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        BranchMaster created = new BranchMaster();
        created.setName(trimmed);
        created.setCode(generateUniqueCode(trimmed, code -> branchRepository.findByCodeIgnoreCase(code).isPresent()));
        return branchRepository.save(created);
    }

    private College resolveOrCreateCollege(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        College existing = collegeRepository.findByCodeIgnoreCase(trimmed)
                .or(() -> collegeRepository.findByNameIgnoreCase(trimmed))
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        College created = new College();
        created.setName(trimmed);
        created.setCode(generateUniqueCode(trimmed, code -> collegeRepository.findByCodeIgnoreCase(code).isPresent()));
        return collegeRepository.save(created);
    }

    private void ensureCollegeCourseMapping(College college, Course course) {
        if (college == null || course == null) {
            return;
        }
        boolean exists = collegeCourseRepository
                .findByCollegeCollegeIdAndCourseCourseId(college.getCollegeId(), course.getCourseId())
                .isPresent();
        if (exists) {
            return;
        }
        CollegeCourse mapping = CollegeCourse.builder()
                .college(college)
                .course(course)
                .totalSeats(0)
                .onHoldSeats(0)
                .allocatedSeats(0)
                .build();
        collegeCourseRepository.save(mapping);
    }

    private String resolveBatchCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        BatchMaster existing = batchMasterRepository.findByCodeIgnoreCase(trimmed)
                .or(() -> batchMasterRepository.findByLabelIgnoreCase(trimmed))
                .orElse(null);
        if (existing != null) {
            return existing.getCode();
        }
        BatchMaster created = new BatchMaster();
        created.setLabel(trimmed);
        created.setCode(generateUniqueCode(trimmed, code -> batchMasterRepository.findByCodeIgnoreCase(code).isPresent()));
        created.setActive(true);
        return batchMasterRepository.save(created).getCode();
    }

    private String generateUniqueCode(String source, java.util.function.Predicate<String> exists) {
        String base = source == null ? "" : source.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ENGLISH);
        if (base.isBlank()) {
            base = "CODE";
        }
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }
        String candidate = base;
        int i = 1;
        while (exists.test(candidate)) {
            String suffix = String.valueOf(i++);
            int maxLen = Math.min(20, base.length());
            String trimmedBase = base.substring(0, Math.min(maxLen, Math.max(1, 20 - suffix.length())));
            candidate = trimmedBase + suffix;
        }
        return candidate;
    }

    private String generateUniqueCourseCode(String source) {
        String base = source == null ? "" : source.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ENGLISH);
        if (base.isBlank()) {
            base = "COURSE";
        }
        if (base.length() > 32) {
            base = base.substring(0, 32);
        }
        String candidate = base;
        int i = 1;
        while (courseRepository.findByCode(candidate).isPresent()) {
            String suffix = String.valueOf(i++);
            int maxLen = Math.min(32, base.length());
            String trimmedBase = base.substring(0, Math.min(maxLen, Math.max(1, 32 - suffix.length())));
            candidate = trimmedBase + suffix;
        }
        return candidate;
    }

    private BigDecimal parseBigDecimal(String raw) {
        String cleaned = normalizeNumberString(raw);
        if (cleaned == null) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseDouble(String raw) {
        String cleaned = normalizeNumberString(raw);
        if (cleaned == null) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeNumberString(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.replace(",", "");
        cleaned = cleaned.replaceAll("[\\s\\u00A0]", "");
        if (!StringUtils.hasText(cleaned) || "-".equals(cleaned)) {
            return null;
        }
        return cleaned;
    }

    private Integer parseInteger(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDate(Row row, DataFormatter formatter, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = formatter.formatCellValue(cell);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        for (DateTimeFormatter fmt : dateFormats()) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ex) {
                // try next
            }
        }
        return null;
    }

    private List<DateTimeFormatter> dateFormats() {
        return List.of(
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd.MM.yy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yy"),
                DateTimeFormatter.ISO_LOCAL_DATE
        );
    }

    private String cellValue(Row row, DataFormatter formatter, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isRowBlank(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        if (row.getPhysicalNumberOfCells() == 0) {
            return true;
        }
        for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
            if (StringUtils.hasText(cellValue(row, formatter, i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isHeaderRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return false;
        }
        String first = cellValue(row, formatter, 0);
        String second = cellValue(row, formatter, 1);
        String third = cellValue(row, formatter, 2);
        return "Admission Date".equalsIgnoreCase(first)
                && "STUDENT STATUS".equalsIgnoreCase(second)
                && "ABS ID".equalsIgnoreCase(third);
    }

    private List<String> readRowValues(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>(TEMPLATE_HEADERS.length);
        for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
            values.add(cellValue(row, formatter, i));
        }
        return values;
    }

    private String writeErrorReport(UUID uploadId, List<BulkErrorRow> errorRows) {
        Path dir = Paths.get("uploads", "error-reports");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create error report directory", e);
        }
        Path outPath = dir.resolve(uploadId + ".xlsx");
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Errors");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("row_number");
            header.createCell(1).setCellValue("error_reason");
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                header.createCell(i + 2).setCellValue(TEMPLATE_HEADERS[i]);
            }
            int rowIndex = 1;
            for (BulkErrorRow errorRow : errorRows) {
                Row outRow = sheet.createRow(rowIndex++);
                outRow.createCell(0).setCellValue(errorRow.rowNumber());
                outRow.createCell(1).setCellValue(errorRow.reason());
                List<String> values = errorRow.values();
                for (int i = 0; i < values.size(); i++) {
                    String value = values.get(i);
                    if (value != null) {
                        outRow.createCell(i + 2).setCellValue(value);
                    }
                }
            }
            workbook.write(out);
            Files.write(outPath, out.toByteArray());
            workbook.dispose();
            return outPath.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write error report", e);
        }
    }

    private AdmissionStatus parseAdmissionStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ENGLISH).replace(' ', '_');
        try {
            return AdmissionStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Gender parseGender(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ENGLISH);
        if ("M".equals(normalized) || "MALE".equals(normalized)) {
            return Gender.Male;
        }
        if ("F".equals(normalized) || "FEMALE".equals(normalized)) {
            return Gender.Female;
        }
        return null;
    }

    private int countInstallments(Row row, DataFormatter formatter) {
        int count = 0;
        if (StringUtils.hasText(cellValue(row, formatter, COL_INSTALLMENT1_AMT))
                || parseDate(row, formatter, COL_INSTALLMENT1_DUE) != null) {
            count++;
        }
        if (StringUtils.hasText(cellValue(row, formatter, COL_INSTALLMENT2_AMT))
                || parseDate(row, formatter, COL_INSTALLMENT2_DUE) != null) {
            count++;
        }
        if (StringUtils.hasText(cellValue(row, formatter, COL_INSTALLMENT3_AMT))
                || parseDate(row, formatter, COL_INSTALLMENT3_DUE) != null) {
            count++;
        }
        if (StringUtils.hasText(cellValue(row, formatter, COL_INSTALLMENT4_AMT))
                || parseDate(row, formatter, COL_INSTALLMENT4_DUE) != null) {
            count++;
        }
        return count == 0 ? 1 : count;
    }

    private Map<String, OtherPaymentField> loadOtherPaymentFields() {
        List<OtherPaymentField> fields = otherPaymentFieldRepository.findAllByOrderBySortOrderAscLabelAsc();
        Map<String, OtherPaymentField> map = new HashMap<>();
        for (OtherPaymentField field : fields) {
            if (field != null && StringUtils.hasText(field.getLabel())) {
                map.put(normalize(field.getLabel()), field);
            }
        }
        return map;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "");
    }

    private String normalizePhone(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        int slashIdx = trimmed.indexOf('/');
        if (slashIdx >= 0) {
            trimmed = trimmed.substring(0, slashIdx).trim();
        }
        return trimmed;
    }

    private ColumnMap buildColumnMap(Sheet sheet, DataFormatter formatter) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            return ColumnMap.defaults();
        }
        Map<String, List<Integer>> byName = new HashMap<>();
        short lastCellNum = header.getLastCellNum();
        for (int col = 0; col < lastCellNum; col++) {
            Cell cell = header.getCell(col);
            String value = cell != null ? formatter.formatCellValue(cell) : null;
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String key = normalize(value);
            byName.computeIfAbsent(key, k -> new ArrayList<>()).add(col);
        }

        Integer inst1Amt = firstIndex(byName, "installments1stamt");
        Integer inst2Amt = firstIndex(byName, "2ndamt");
        Integer inst3Amt = firstIndex(byName, "3rdamt");
        Integer inst4Amt = firstIndex(byName, "4thamt");
        Integer inst1Year = firstIndex(byName, "installment1year");
        Integer inst2Year = firstIndex(byName, "installment2year");
        Integer inst3Year = firstIndex(byName, "installment3year");
        Integer inst4Year = firstIndex(byName, "installment4year");
        Integer paymentStart = firstIndex(byName, "installment1date");

        List<Integer> dueBy = byName.getOrDefault("dueby", List.of());
        Integer inst1Due = nextIndexAfter(dueBy, inst1Amt != null ? inst1Amt : COL_INSTALLMENT1_AMT);
        Integer inst2Due = nextIndexAfter(dueBy, inst2Amt != null ? inst2Amt : COL_INSTALLMENT2_AMT);
        Integer inst3Due = nextIndexAfter(dueBy, inst3Amt != null ? inst3Amt : COL_INSTALLMENT3_AMT);
        Integer inst4Due = nextIndexAfter(dueBy, inst4Amt != null ? inst4Amt : COL_INSTALLMENT4_AMT);

        return new ColumnMap(
                inst1Amt != null ? inst1Amt : COL_INSTALLMENT1_AMT,
                inst1Year != null ? inst1Year : COL_INSTALLMENT1_YEAR,
                inst1Due != null ? inst1Due : COL_INSTALLMENT1_DUE,
                inst2Amt != null ? inst2Amt : COL_INSTALLMENT2_AMT,
                inst2Year != null ? inst2Year : COL_INSTALLMENT2_YEAR,
                inst2Due != null ? inst2Due : COL_INSTALLMENT2_DUE,
                inst3Amt != null ? inst3Amt : COL_INSTALLMENT3_AMT,
                inst3Year != null ? inst3Year : COL_INSTALLMENT3_YEAR,
                inst3Due != null ? inst3Due : COL_INSTALLMENT3_DUE,
                inst4Amt != null ? inst4Amt : COL_INSTALLMENT4_AMT,
                inst4Year != null ? inst4Year : COL_INSTALLMENT4_YEAR,
                inst4Due != null ? inst4Due : COL_INSTALLMENT4_DUE,
                paymentStart != null ? paymentStart : COL_PAYMENT_HISTORY_START
        );
    }

    private Integer firstIndex(Map<String, List<Integer>> byName, String key) {
        List<Integer> values = byName.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private Integer nextIndexAfter(List<Integer> values, int after) {
        Integer next = null;
        for (Integer value : values) {
            if (value != null && value > after && (next == null || value < next)) {
                next = value;
            }
        }
        return next;
    }

    private record ColumnMap(
            int installment1Amt,
            int installment1Year,
            int installment1Due,
            int installment2Amt,
            int installment2Year,
            int installment2Due,
            int installment3Amt,
            int installment3Year,
            int installment3Due,
            int installment4Amt,
            int installment4Year,
            int installment4Due,
            int paymentHistoryStart
    ) {
        static ColumnMap defaults() {
            return new ColumnMap(
                    COL_INSTALLMENT1_AMT,
                    COL_INSTALLMENT1_YEAR,
                    COL_INSTALLMENT1_DUE,
                    COL_INSTALLMENT2_AMT,
                    COL_INSTALLMENT2_YEAR,
                    COL_INSTALLMENT2_DUE,
                    COL_INSTALLMENT3_AMT,
                    COL_INSTALLMENT3_YEAR,
                    COL_INSTALLMENT3_DUE,
                    COL_INSTALLMENT4_AMT,
                    COL_INSTALLMENT4_YEAR,
                    COL_INSTALLMENT4_DUE,
                    COL_PAYMENT_HISTORY_START
            );
        }
    }

    private com.bothash.admissionservice.entity.PaymentModeMaster resolvePaymentMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = normalizePaymentMode(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        com.bothash.admissionservice.entity.PaymentModeMaster existing = paymentModeService.getByMode(normalized);
        if (existing != null) {
            return existing;
        }
        return paymentModeService.findByCode(normalized)
                .map(mode -> {
                    mode.setActive(true);
                    if (!StringUtils.hasText(mode.getLabel())) {
                        mode.setLabel(normalized);
                    }
                    return paymentModeService.save(mode);
                })
                .orElseGet(() -> paymentModeService.save(com.bothash.admissionservice.entity.PaymentModeMaster.builder()
                        .code(normalized)
                        .label(buildPaymentModeLabel(raw))
                        .active(true)
                        .displayOrder(0)
                        .build()));
    }

    private void createInvoiceForPayment(Admission2 admission, FeeInstallment installment, FeeInstallmentPayment payment) {
        if (payment == null || payment.getPaymentId() == null) {
            return;
        }
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (feeInvoiceRepository.existsByPayment_PaymentId(payment.getPaymentId())) {
            return;
        }
        invoiceService.generateInvoiceForPayment(admission, installment, payment);
    }

    private String normalizePaymentMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32).trim();
        }
        return cleaned;
    }

    private String buildPaymentModeLabel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64).trim();
        }
        return cleaned;
    }

    private HscSubjectMarks parseHscSubjects(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        String[] parts = trimmed.split("-", 2);
        String subjectPart = parts[0].trim().toUpperCase(Locale.ENGLISH);
        String marksPart = parts.length > 1 ? parts[1].trim() : null;
        Integer physics = null;
        Integer chemistry = null;
        Integer biology = null;
        if (StringUtils.hasText(marksPart)) {
            String[] marks = marksPart.split("[/|,]");
            if (marks.length >= 3) {
                physics = parseInteger(marks[0].trim());
                chemistry = parseInteger(marks[1].trim());
                biology = parseInteger(marks[2].trim());
            }
        }
        if (!StringUtils.hasText(subjectPart)) {
            return null;
        }
        return new HscSubjectMarks(subjectPart, physics, chemistry, biology);
    }

    private record HscSubjectMarks(String subjects, Integer physics, Integer chemistry, Integer biology) {
    }

    private record PlanEntry(String amountRaw, LocalDate dueDate, Integer studyYear) {
    }

    private record BulkErrorRow(int rowNumber, String reason, List<String> values) {
    }
}
