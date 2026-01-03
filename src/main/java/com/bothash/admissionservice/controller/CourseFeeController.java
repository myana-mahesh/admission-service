package com.bothash.admissionservice.controller;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.CourseFeeRequestDto;
import com.bothash.admissionservice.service.impl.CourseServiceImpl;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseFeeController {

    private final CourseServiceImpl courseService;

    @PostMapping("/with-fee")
    public CourseFeeRequestDto createCourseWithFee(@RequestBody CourseFeeRequestDto dto) {
        return courseService.createOrUpdateCourseWithFee(dto);
    }

    @PutMapping("/{courseId}/with-fee")
    public CourseFeeRequestDto updateCourseWithFee(
            @PathVariable Long courseId,
            @RequestBody CourseFeeRequestDto dto
    ) {
        dto.setCourseId(courseId);
        return courseService.createOrUpdateCourseWithFee(dto);
    }

    @GetMapping("/{courseId}/with-fee")
    public CourseFeeRequestDto getCourseWithFee(@PathVariable Long courseId) {
        return courseService.getCourseWithFee(courseId);
    }
    
    @GetMapping("/with-fee")
    public List<CourseFeeRequestDto> getAllCoursesWithFee() {
        return courseService.getAllCoursesWithFee();
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<String> deleteCourse(@PathVariable Long courseId) {
        try {
            courseService.deleteCourse(courseId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot delete this course because admissions already exist for it.");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to delete the course.");
        }
    }
}
