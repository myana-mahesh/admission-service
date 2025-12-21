package com.bothash.admissionservice.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.bothash.admissionservice.dto.CollegeDto;
import com.bothash.admissionservice.entity.College;
import com.bothash.admissionservice.entity.CollegeCourse;
import com.bothash.admissionservice.entity.Course;
import com.bothash.admissionservice.repository.CollegeRepository;
import com.bothash.admissionservice.repository.CourseRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollegeService {
    private final CollegeRepository collegeRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public List<CollegeDto> listAll() {
        return collegeRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public CollegeDto getById(Long collegeId) {
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new IllegalArgumentException("College not found: " + collegeId));
        return mapToDto(college);
    }

    @Transactional
    public CollegeDto save(CollegeDto dto) {
        College college;
        if (dto.getCollegeId() == null) {
            if (collegeRepository.existsByCode(dto.getCode())) {
                throw new IllegalArgumentException("College code already exists: " + dto.getCode());
            }
            college = new College();
        } else {
            if (collegeRepository.existsByCodeAndCollegeIdNot(dto.getCode(), dto.getCollegeId())) {
                throw new IllegalArgumentException("College code already exists: " + dto.getCode());
            }
            college = collegeRepository.findById(dto.getCollegeId())
                    .orElseThrow(() -> new IllegalArgumentException("College not found: " + dto.getCollegeId()));
        }

        college.setCode(dto.getCode());
        college.setName(dto.getName());

        List<CollegeCourse> existing = college.getCourses() == null ? List.of() : college.getCourses();
        Map<Long, CollegeCourse> existingByCourseId = new HashMap<>();
        for (CollegeCourse cc : existing) {
            if (cc.getCourse() != null && cc.getCourse().getCourseId() != null) {
                existingByCourseId.put(cc.getCourse().getCourseId(), cc);
            }
        }

        List<CollegeCourse> updated = new ArrayList<>();
        Set<Long> seenCourseIds = new HashSet<>();
        if (dto.getCourses() != null) {
            for (CollegeDto.CollegeCourseDto courseDto : dto.getCourses()) {
                if (courseDto == null || courseDto.getCourseId() == null) {
                    continue;
                }
                if (!seenCourseIds.add(courseDto.getCourseId())) {
                    continue;
                }
                Course course = courseRepository.findById(courseDto.getCourseId())
                        .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseDto.getCourseId()));

                CollegeCourse cc = existingByCourseId.getOrDefault(course.getCourseId(), new CollegeCourse());
                cc.setCollege(college);
                cc.setCourse(course);
                cc.setTotalSeats(courseDto.getTotalSeats() == null ? 0 : courseDto.getTotalSeats());
                updated.add(cc);
            }
        }

        college.setCourses(updated);
        College saved = collegeRepository.save(college);
        return mapToDto(saved);
    }

    public void delete(Long collegeId) {
        collegeRepository.deleteById(collegeId);
    }

    private CollegeDto mapToDto(College college) {
        List<CollegeDto.CollegeCourseDto> courses = college.getCourses() == null ? List.of()
                : college.getCourses().stream()
                .map(cc -> CollegeDto.CollegeCourseDto.builder()
                        .courseId(cc.getCourse() != null ? cc.getCourse().getCourseId() : null)
                        .courseCode(cc.getCourse() != null ? cc.getCourse().getCode() : null)
                        .courseName(cc.getCourse() != null ? cc.getCourse().getName() : null)
                        .totalSeats(cc.getTotalSeats())
                        .build())
                .toList();

        return CollegeDto.builder()
                .collegeId(college.getCollegeId())
                .code(college.getCode())
                .name(college.getName())
                .courses(courses)
                .build();
    }
}
