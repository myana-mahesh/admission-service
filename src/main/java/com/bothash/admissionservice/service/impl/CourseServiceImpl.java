package com.bothash.admissionservice.service.impl;

import org.springframework.stereotype.Service;



import com.bothash.admissionservice.dto.CourseFeeRequestDto;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.entity.CourseFeeTemplate;
import com.bothash.admissionservice.entity.CourseFeeTemplateInstallment;
import com.bothash.admissionservice.repository.CourseFeeTemplateRepository;
import com.bothash.admissionservice.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl {

    private final CourseRepository courseRepository;
    private final CourseFeeTemplateRepository courseFeeTemplateRepository;

    // ----------------------------------------
    // CREATE / UPDATE COURSE + DEFAULT FEES
    // ----------------------------------------
    @Transactional
    public CourseFeeRequestDto createOrUpdateCourseWithFee(CourseFeeRequestDto dto) {
        Course course;

        if (dto.getCourseId() != null) {
            // UPDATE
            course = courseRepository.findById(dto.getCourseId())
                    .orElseThrow(() -> new IllegalArgumentException("Course not found: " + dto.getCourseId()));
        } else {
            // CREATE
            course = new Course();
        }

        // Basic course fields
        course.setCode(dto.getCode());
        course.setName(dto.getName());
        course.setYears(dto.getYears());

        // Handle fee template
        CourseFeeRequestDto.FeeTemplateDto feeTemplateDto = dto.getFeeTemplate();

        if (feeTemplateDto == null || CollectionUtils.isEmpty(feeTemplateDto.getInstallments())) {
            // No template provided -> clear existing
            course.setCourseFeeTemplate(null);
        } else {
            CourseFeeTemplate template = course.getCourseFeeTemplate();

            if (template == null) {
                template = new CourseFeeTemplate();
                template.setInstallments(new ArrayList<>());
            }

            template.setName(feeTemplateDto.getName());
            template.setTotalAmount(feeTemplateDto.getTotalAmount());

            // Reset installments and rebuild from DTO
            template.getInstallments().clear();

            int i = 0;
            for (CourseFeeRequestDto.InstallmentDto instDto : feeTemplateDto.getInstallments()) {
                CourseFeeTemplateInstallment inst = new CourseFeeTemplateInstallment();
                inst.setTemplate(template);
                inst.setSequence(instDto.getSequence() != null ? instDto.getSequence() : (i + 1));
                inst.setAmount(instDto.getAmount());
                inst.setDueDaysFromAdmission(instDto.getDueDaysFromAdmission());

                Integer yearNumber = instDto.getYearNumber();
                if (yearNumber == null || yearNumber <= 0) {
                    yearNumber = 1;
                }
                inst.setYearNumber(yearNumber);

                template.getInstallments().add(inst);
                i++;
            }


            // Attach template to course
            template=this.courseFeeTemplateRepository.save(template);
            course.setCourseFeeTemplate(template);
        }

        // Save course (with cascade to template + installments)
        Course saved = courseRepository.save(course);

        // Make sure template id is persisted for DTO response
        if (saved.getCourseFeeTemplate() != null &&
            saved.getCourseFeeTemplate().getCourseFeeTemplateId() == null) {
            courseFeeTemplateRepository.save(saved.getCourseFeeTemplate());
        }

        return mapToDto(saved);
    }

    // ----------------------------------------
    // GET COURSE + DEFAULT FEES
    // ----------------------------------------
    @Transactional(readOnly = true)
    public CourseFeeRequestDto getCourseWithFee(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        return mapToDto(course);
    }

    // ----------------------------------------
    // MAPPERS
    // ----------------------------------------

    private CourseFeeRequestDto mapToDto(Course course) {
        CourseFeeRequestDto.CourseFeeRequestDtoBuilder builder = CourseFeeRequestDto.builder()
                .courseId(course.getCourseId())
                .code(course.getCode())
                .name(course.getName())
                .years(course.getYears());

        CourseFeeTemplate template = course.getCourseFeeTemplate();
        if (template != null) {
            CourseFeeRequestDto.FeeTemplateDto feeTemplateDto =
                    CourseFeeRequestDto.FeeTemplateDto.builder()
                            .id(template.getCourseFeeTemplateId())
                            .name(template.getName())
                            .totalAmount(template.getTotalAmount())
                            .installments(mapInstallmentsToDto(template.getInstallments()))
                            .build();

            builder.feeTemplate(feeTemplateDto);
        }

        return builder.build();
    }

    private List<CourseFeeRequestDto.InstallmentDto> mapInstallmentsToDto(List<CourseFeeTemplateInstallment> installments) {
        if (installments == null) return List.of();

        return installments.stream()
                .sorted(Comparator.comparingInt(CourseFeeTemplateInstallment::getSequence))
                .map(inst -> CourseFeeRequestDto.InstallmentDto.builder()
                        .id(inst.getCourseFeeTemplateInstallmentId())
                        .sequence(inst.getSequence())
                        .amount(inst.getAmount())
                        .dueDaysFromAdmission(inst.getDueDaysFromAdmission())
                        .yearNumber(inst.getYearNumber())   
                        .build())
                .toList();
    }

    public List<CourseFeeRequestDto> getAllCoursesWithFee() {
        return courseRepository.findAll().stream()
                .map(course -> mapToDto(course)) // 🔥 reuse existing logic
                .collect(Collectors.toList());
    }
}

