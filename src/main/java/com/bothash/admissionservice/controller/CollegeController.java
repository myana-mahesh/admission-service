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

import com.bothash.admissionservice.dto.CollegeDto;
import com.bothash.admissionservice.dto.CollegeCourseSeatDto;
import com.bothash.admissionservice.service.impl.CollegeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/colleges")
@RequiredArgsConstructor
public class CollegeController {
    private final CollegeService collegeService;

    @GetMapping
    public List<CollegeDto> list() {
        return collegeService.listAll();
    }

    @GetMapping("/{collegeId}")
    public CollegeDto get(@PathVariable Long collegeId) {
        return collegeService.getById(collegeId);
    }

    @GetMapping("/{collegeId}/course-seats")
    public List<CollegeCourseSeatDto> getCourseSeats(@PathVariable Long collegeId) {
        return collegeService.getCourseSeatSummary(collegeId);
    }

    @PostMapping
    public CollegeDto create(@RequestBody CollegeDto dto) {
        dto.setCollegeId(null);
        return collegeService.save(dto);
    }

    @PutMapping("/{collegeId}")
    public CollegeDto update(@PathVariable Long collegeId, @RequestBody CollegeDto dto) {
        dto.setCollegeId(collegeId);
        return collegeService.save(dto);
    }

    @DeleteMapping("/{collegeId}")
    public ResponseEntity<String> delete(@PathVariable Long collegeId) {
        try {
            collegeService.delete(collegeId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot delete this college because admissions already exist for it.");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to delete the college.");
        }
    }
}
