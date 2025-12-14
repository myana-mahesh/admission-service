package com.bothash.admissionservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bothash.admissionservice.dto.StudentPerkDTO;
import com.bothash.admissionservice.service.StudentPerksService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentPerksController {

    private final StudentPerksService studentPerksService;

    /**
     * Get all perks assigned to a student
     */
    @GetMapping("/{studentId}/perks")
    public ResponseEntity<List<StudentPerkDTO>> getStudentPerks(
            @PathVariable Long studentId) {
        List<StudentPerkDTO> perks = studentPerksService.getPerksForStudent(studentId);
        return ResponseEntity.ok(perks);
    }

    /**
     * Assign a perk to a student
     */
    @PostMapping("/{studentId}/perks/{perkId}")
    public ResponseEntity<Void> assignPerk(
            @PathVariable Long studentId,
            @PathVariable Long perkId) {

        studentPerksService.assignPerkToStudent(studentId, perkId);
        return ResponseEntity.ok().build();
    }

    /**
     * Remove a perk from a student
     */
    @DeleteMapping("/{studentId}/perks/{perkId}")
    public ResponseEntity<Void> removePerk(
            @PathVariable Long studentId,
            @PathVariable Long perkId) {

        studentPerksService.removePerkFromStudent(studentId, perkId);
        return ResponseEntity.noContent().build();
    }

    /**
     * (Optional) Replace all perks for a student with a new list
     */
    @PutMapping("/{studentId}/perks")
    public ResponseEntity<Void> replacePerks(
            @PathVariable Long studentId,
            @RequestBody List<Long> perkIds) {

        // delete all existing mappings
        List<StudentPerkDTO> existing = studentPerksService.getPerksForStudent(studentId);
        for (StudentPerkDTO dto : existing) {
            studentPerksService.removePerkFromStudent(studentId, dto.perkId());
        }

        // add new ones
        if (perkIds != null) {
            for (Long perkId : perkIds) {
                studentPerksService.assignPerkToStudent(studentId, perkId);
            }
        }

        return ResponseEntity.ok().build();
    }
}
