package com.bothash.admissionservice.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.bothash.admissionservice.dto.ExamMasterDto;
import com.bothash.admissionservice.dto.ExamMasterRequest;
import com.bothash.admissionservice.dto.ExamSubjectDetailDto;
import com.bothash.admissionservice.entity.BatchMaster;
import com.bothash.admissionservice.entity.BranchMaster;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.ExamMaster;
import com.bothash.admissionservice.entity.ExamSubjectDetail;
import com.bothash.admissionservice.entity.SubjectMaster;
import com.bothash.admissionservice.repository.BatchMasterRepository;
import com.bothash.admissionservice.repository.BranchRepository;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.ExamMasterRepository;
import com.bothash.admissionservice.repository.SubjectMasterRepository;
import com.bothash.admissionservice.service.ExamMasterService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamMasterServiceImpl implements ExamMasterService {

    private final ExamMasterRepository examRepository;
    private final CourseRepository courseRepository;
    private final BranchRepository branchRepository;
    private final BatchMasterRepository batchRepository;
    private final SubjectMasterRepository subjectRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ExamMasterDto> listExams(Long courseId) {
        List<ExamMaster> exams;
        if (courseId == null) {
            exams = examRepository.findAll();
        } else {
            exams = examRepository.findByCourseCourseIdOrderByExamNameAsc(courseId);
        }
        return exams.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ExamMasterDto getExam(Long examId) {
        ExamMaster exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        return toDto(exam);
    }

    @Override
    @Transactional
    public ExamMasterDto createExam(ExamMasterRequest request) {
        ExamMaster exam = new ExamMaster();
        applyRequest(exam, request);
        ExamMaster saved = examRepository.save(exam);
        return toDto(saved);
    }

    @Override
    @Transactional
    public ExamMasterDto updateExam(Long examId, ExamMasterRequest request) {
        ExamMaster exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found: " + examId));
        applyRequest(exam, request);
        return toDto(examRepository.save(exam));
    }

    @Override
    @Transactional
    public void deleteExam(Long examId) {
        examRepository.deleteById(examId);
    }

    private void applyRequest(ExamMaster exam, ExamMasterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Exam request is required");
        }
        if (!StringUtils.hasText(request.getExamName())) {
            throw new IllegalArgumentException("Exam name is required");
        }
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + request.getCourseId()));
        exam.setExamName(request.getExamName().trim());
        exam.setCourse(course);
        exam.setMaxFailedSubjects(request.getMaxFailedSubjects());

        exam.getBranches().clear();
        exam.getBatches().clear();

        if (!CollectionUtils.isEmpty(request.getBranchIds())) {
            List<BranchMaster> branches = branchRepository.findAllById(request.getBranchIds());
            exam.getBranches().addAll(branches);
        }
        if (!CollectionUtils.isEmpty(request.getBatchIds())) {
            List<BatchMaster> batches = batchRepository.findAllById(request.getBatchIds());
            exam.getBatches().addAll(batches);
        }

        exam.getSubjectDetails().clear();
        List<ExamSubjectDetailDto> subjects = Optional.ofNullable(request.getSubjects())
                .orElseGet(Collections::emptyList);
        for (ExamSubjectDetailDto subjectReq : subjects) {
            if (subjectReq == null || subjectReq.getSubjectId() == null) {
                continue;
            }
            SubjectMaster subject = subjectRepository.findById(subjectReq.getSubjectId())
                    .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectReq.getSubjectId()));
            if (subject.getCourse() == null || subject.getCourse().getCourseId() == null
                    || !Objects.equals(subject.getCourse().getCourseId(), course.getCourseId())) {
                throw new IllegalArgumentException("Subject not mapped to selected course: " + subjectReq.getSubjectId());
            }
            ExamSubjectDetail detail = new ExamSubjectDetail();
            detail.setExam(exam);
            detail.setSubject(subject);
            detail.setTotalMarks(subjectReq.getTotalMarks() != null ? subjectReq.getTotalMarks() : 0);
            detail.setPassingMarks(subjectReq.getPassingMarks() != null ? subjectReq.getPassingMarks() : 0);
            detail.setExamDate(subjectReq.getExamDate());
            exam.getSubjectDetails().add(detail);
        }
    }


    private ExamMasterDto toDto(ExamMaster exam) {
        ExamMasterDto dto = new ExamMasterDto();
        dto.setExamId(exam.getExamId());
        dto.setExamName(exam.getExamName());
        if (exam.getCourse() != null) {
            dto.setCourseId(exam.getCourse().getCourseId());
            dto.setCourseName(exam.getCourse().getName());
        }
        dto.setMaxFailedSubjects(exam.getMaxFailedSubjects());

        Set<BranchMaster> branches = exam.getBranches();
        if (branches != null) {
            dto.setBranchIds(branches.stream()
                    .map(BranchMaster::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        Set<BatchMaster> batches = exam.getBatches();
        if (batches != null) {
            dto.setBatchIds(batches.stream()
                    .map(BatchMaster::getBatchId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        List<ExamSubjectDetail> details = exam.getSubjectDetails();
        if (details != null) {
            List<ExamSubjectDetailDto> subjects = new ArrayList<>();
            for (ExamSubjectDetail detail : details) {
                ExamSubjectDetailDto d = new ExamSubjectDetailDto();
                if (detail.getSubject() != null) {
                    d.setSubjectId(detail.getSubject().getSubjectId());
                    d.setSubjectName(detail.getSubject().getName());
                }
                d.setTotalMarks(detail.getTotalMarks());
                d.setPassingMarks(detail.getPassingMarks());
                d.setExamDate(detail.getExamDate());
                subjects.add(d);
            }
            dto.setSubjects(subjects);
        }
        return dto;
    }
}
