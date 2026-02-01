package com.bothash.admissionservice.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bothash.admissionservice.dto.ExamMarksRequest;
import com.bothash.admissionservice.dto.ExamOverviewDto;
import com.bothash.admissionservice.dto.ExamStudentMarkDto;
import com.bothash.admissionservice.dto.ExamStudentRowDto;
import com.bothash.admissionservice.dto.ExamSubjectDetailDto;
import com.bothash.admissionservice.entity.ExamAssignment;
import com.bothash.admissionservice.entity.ExamMaster;
import com.bothash.admissionservice.entity.ExamStudentMark;
import com.bothash.admissionservice.entity.SubjectMaster;
import com.bothash.admissionservice.enumpackage.GuardianRelation;
import com.bothash.admissionservice.repository.Admission2Repository;
import com.bothash.admissionservice.repository.ExamAssignmentRepository;
import com.bothash.admissionservice.repository.ExamMasterRepository;
import com.bothash.admissionservice.repository.ExamStudentMarkRepository;
import com.bothash.admissionservice.repository.SubjectMasterRepository;
import com.bothash.admissionservice.service.ExamOverviewService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamOverviewServiceImpl implements ExamOverviewService {

    private final ExamMasterRepository examRepository;
    private final ExamAssignmentRepository assignmentRepository;
    private final ExamStudentMarkRepository markRepository;
    private final SubjectMasterRepository subjectRepository;
    private final Admission2Repository admissionRepository;

    @Override
    @Transactional(readOnly = true)
    public ExamOverviewDto getOverview(Long examId, Long collegeId, List<Long> branchIds, List<String> batchCodes,
                                       String query, String status, Boolean absentOnly, int page, int size) {
        ExamMaster exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));

        List<Long> examBranchIds = exam.getBranches().stream()
                .map(branch -> branch.getId())
                .filter(id -> id != null)
                .collect(Collectors.toList());
        List<String> examBatchCodes = exam.getBatches().stream()
                .map(batch -> batch.getCode())
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toList());

        List<Long> resolvedBranchIds = new ArrayList<>(examBranchIds);
        if (branchIds != null && !branchIds.isEmpty()) {
            resolvedBranchIds.retainAll(branchIds);
        }
        List<String> resolvedBatchCodes = new ArrayList<>(examBatchCodes);
        if (batchCodes != null && !batchCodes.isEmpty()) {
            resolvedBatchCodes.retainAll(batchCodes);
        }

        String resolvedQuery = query != null && !query.isBlank() ? query.trim() : null;
        String resolvedStatus = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = size > 0 ? size : 25;
        Page<com.bothash.admissionservice.entity.Admission2> admissionsPage =
                Page.empty(PageRequest.of(resolvedPage, resolvedSize));
        List<com.bothash.admissionservice.entity.Admission2> admissions = List.of();
        if (exam.getCourse() != null && exam.getCourse().getCourseId() != null
                && !resolvedBranchIds.isEmpty() && !resolvedBatchCodes.isEmpty()) {
            PageRequest pageRequest = PageRequest.of(resolvedPage, resolvedSize, Sort.by("admissionId").ascending());
            admissionsPage = admissionRepository.searchExamAdmissionsWithFilters(
                    exam.getExamId(),
                    exam.getCourse().getCourseId(),
                    resolvedBranchIds,
                    resolvedBatchCodes,
                    collegeId,
                    resolvedQuery,
                    resolvedStatus,
                    absentOnly,
                    pageRequest);
            admissions = admissionsPage.getContent();
        }

        List<ExamAssignment> assignments = new ArrayList<>();
        List<Long> admissionIds = admissions.stream()
                .filter(admission -> admission != null && admission.getAdmissionId() != null)
                .map(admission -> admission.getAdmissionId())
                .collect(Collectors.toList());
        if (!admissionIds.isEmpty()) {
            assignments = assignmentRepository.findByExamExamIdAndAdmissionAdmissionIdIn(examId, admissionIds);
        }
        List<Long> assignmentIds = assignments.stream()
                .map(ExamAssignment::getAssignmentId)
                .collect(Collectors.toList());
        Map<Long, List<ExamStudentMark>> marksByAssignment = new HashMap<>();
        if (!assignmentIds.isEmpty()) {
            for (ExamStudentMark mark : markRepository.findByAssignmentAssignmentIdIn(assignmentIds)) {
                if (mark.getAssignment() == null || mark.getAssignment().getAssignmentId() == null) {
                    continue;
                }
                marksByAssignment.computeIfAbsent(mark.getAssignment().getAssignmentId(), k -> new ArrayList<>())
                        .add(mark);
            }
        }

        List<ExamSubjectDetailDto> subjects = exam.getSubjectDetails().stream()
                .map(detail -> ExamSubjectDetailDto.builder()
                        .subjectId(detail.getSubject() != null ? detail.getSubject().getSubjectId() : null)
                        .subjectName(detail.getSubject() != null ? detail.getSubject().getName() : null)
                        .totalMarks(detail.getTotalMarks())
                        .passingMarks(detail.getPassingMarks())
                        .examDate(detail.getExamDate())
                        .build())
                .collect(Collectors.toList());

        List<ExamStudentRowDto> students = new ArrayList<>();
        Map<Long, ExamAssignment> assignmentByAdmission = assignments.stream()
                .filter(a -> a.getAdmission() != null && a.getAdmission().getAdmissionId() != null)
                .collect(Collectors.toMap(a -> a.getAdmission().getAdmissionId(), a -> a, (a, b) -> a));
        for (com.bothash.admissionservice.entity.Admission2 admission : admissions) {
            if (admission == null || admission.getAdmissionId() == null || admission.getStudent() == null) {
                continue;
            }
            ExamAssignment assignment = assignmentByAdmission.get(admission.getAdmissionId());
            List<ExamStudentMark> marks = assignment != null
                    ? marksByAssignment.getOrDefault(assignment.getAssignmentId(), List.of())
                    : List.of();
            Map<Long, Integer> marksBySubject = new HashMap<>();
            for (ExamStudentMark mark : marks) {
                if (mark.getSubject() != null && mark.getSubject().getSubjectId() != null) {
                    Integer obtained = mark.getMarksObtained();
                    if (Boolean.TRUE.equals(mark.getAbsent())) {
                        obtained = 0;
                    }
                    marksBySubject.put(mark.getSubject().getSubjectId(), obtained);
                }
            }
            List<ExamStudentMarkDto> markDtos = subjects.stream()
                    .map(subject -> ExamStudentMarkDto.builder()
                            .subjectId(subject.getSubjectId())
                            .marksObtained(subject.getSubjectId() != null
                                    ? marksBySubject.get(subject.getSubjectId())
                                    : null)
                            .absent(resolveAbsentForSubject(marks, subject.getSubjectId()))
                            .build())
                    .collect(Collectors.toList());

            String computedStatus = assignment != null && assignment.getExamStatus() != null
                    ? assignment.getExamStatus()
                    : "MARKS_NOT_ENTERED";
            students.add(ExamStudentRowDto.builder()
                    .admissionId(admission.getAdmissionId())
                    .studentId(admission.getStudent().getStudentId())
                    .studentName(admission.getStudent().getFullName())
                    .absId(admission.getStudent().getAbsId())
                    .studentMobile(admission.getStudent().getMobile())
                    .fatherMobile(resolveGuardianMobile(admission.getStudent().getGuardians(), GuardianRelation.Father))
                    .motherMobile(resolveGuardianMobile(admission.getStudent().getGuardians(), GuardianRelation.Mother))
                    .collegeName(admission.getCollege() != null ? admission.getCollege().getName() : null)
                    .marks(markDtos)
                    .status(computedStatus)
                    .build());
        }

        return ExamOverviewDto.builder()
                .examId(exam.getExamId())
                .examName(exam.getExamName())
                .courseId(exam.getCourse() != null ? exam.getCourse().getCourseId() : null)
                .courseName(exam.getCourse() != null ? exam.getCourse().getName() : null)
                .subjects(subjects)
                .students(students)
                .totalStudents(admissionsPage.getTotalElements())
                .page(resolvedPage)
                .size(resolvedSize)
                .maxFailedSubjects(exam.getMaxFailedSubjects())
                .build();
    }

    @Override
    @Transactional
    public void saveMarks(Long examId, ExamMarksRequest request) {
        if (request == null || request.getStudents() == null) {
            return;
        }
        ExamMaster exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        List<ExamSubjectDetailDto> subjects = exam.getSubjectDetails().stream()
                .map(detail -> ExamSubjectDetailDto.builder()
                        .subjectId(detail.getSubject() != null ? detail.getSubject().getSubjectId() : null)
                        .subjectName(detail.getSubject() != null ? detail.getSubject().getName() : null)
                        .totalMarks(detail.getTotalMarks())
                        .passingMarks(detail.getPassingMarks())
                        .examDate(detail.getExamDate())
                        .build())
                .collect(Collectors.toList());
        int maxFailedSubjects = exam.getMaxFailedSubjects() != null && exam.getMaxFailedSubjects() >= 0
                ? exam.getMaxFailedSubjects()
                : 0;

        for (ExamStudentRowDto studentRow : request.getStudents()) {
            if (studentRow == null || studentRow.getAdmissionId() == null) {
                continue;
            }
            ExamAssignment assignment = assignmentRepository
                    .findByExamExamIdAndAdmissionAdmissionId(examId, studentRow.getAdmissionId())
                    .orElseGet(() -> {
                        com.bothash.admissionservice.entity.Admission2 admission =
                                admissionRepository.findById(studentRow.getAdmissionId()).orElse(null);
                        if (admission == null) {
                            return null;
                        }
                        ExamAssignment created = new ExamAssignment();
                        created.setExam(exam);
                        created.setAdmission(admission);
                        return assignmentRepository.save(created);
                    });
            if (assignment == null) {
                continue;
            }
            if (studentRow.getMarks() == null) {
                continue;
            }
            List<ExamStudentMark> existingMarks = markRepository
                    .findByAssignmentAssignmentId(assignment.getAssignmentId());
            for (ExamStudentMarkDto markDto : studentRow.getMarks()) {
                if (markDto == null || markDto.getSubjectId() == null) {
                    continue;
                }
                SubjectMaster subject = subjectRepository.findById(markDto.getSubjectId())
                        .orElse(null);
                if (subject == null) {
                    continue;
                }
                ExamStudentMark mark = existingMarks.stream()
                        .filter(existing -> existing.getSubject() != null
                                && markDto.getSubjectId().equals(existing.getSubject().getSubjectId()))
                        .findFirst()
                        .orElseGet(() -> {
                            ExamStudentMark created = new ExamStudentMark();
                            created.setAssignment(assignment);
                            created.setSubject(subject);
                            return created;
                        });
                boolean isAbsent = Boolean.TRUE.equals(markDto.getAbsent());
                mark.setAbsent(isAbsent);
                mark.setMarksObtained(isAbsent ? 0 : markDto.getMarksObtained());
                markRepository.save(mark);
            }

            String computedStatus = computeStatus(
                    subjects,
                    studentRow.getMarks(),
                    maxFailedSubjects
            );
            assignment.setExamStatus(computedStatus);
            assignmentRepository.save(assignment);
        }
    }

    private Boolean resolveAbsentForSubject(List<ExamStudentMark> marks, Long subjectId) {
        if (marks == null || subjectId == null) {
            return null;
        }
        for (ExamStudentMark mark : marks) {
            if (mark != null && mark.getSubject() != null
                    && subjectId.equals(mark.getSubject().getSubjectId())) {
                return mark.getAbsent();
            }
        }
        return null;
    }

    private String resolveGuardianMobile(List<com.bothash.admissionservice.entity.Guardian> guardians,
                                         GuardianRelation relation) {
        if (guardians == null || relation == null) {
            return null;
        }
        for (com.bothash.admissionservice.entity.Guardian guardian : guardians) {
            if (guardian != null && guardian.getRelation() == relation) {
                return guardian.getMobile();
            }
        }
        return null;
    }

    private String computeStatus(List<ExamSubjectDetailDto> subjects,
                                 List<ExamStudentMarkDto> marks,
                                 int maxFailedSubjects) {
        if (subjects == null || subjects.isEmpty()) {
            return "MARKS_NOT_ENTERED";
        }
        Map<Long, Integer> marksBySubject = new HashMap<>();
        if (marks != null) {
            for (ExamStudentMarkDto mark : marks) {
                if (mark.getSubjectId() != null) {
                    marksBySubject.put(mark.getSubjectId(), mark.getMarksObtained());
                }
            }
        }
        int failed = 0;
        for (ExamSubjectDetailDto subject : subjects) {
            if (subject == null || subject.getSubjectId() == null) {
                continue;
            }
            Integer obtained = marksBySubject.get(subject.getSubjectId());
            if (obtained == null) {
                return "MARKS_NOT_ENTERED";
            }
            int passing = subject.getPassingMarks() != null ? subject.getPassingMarks() : 0;
            if (obtained < passing) {
                failed++;
            }
        }
        if (failed == 0) {
            return "PASSED";
        }
        return failed <= maxFailedSubjects ? "ELIGIBLE" : "FAILED";
    }
}
