package com.bothash.admissionservice.controller;

import java.util.List;
import java.util.Map;

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

import com.bothash.admissionservice.entity.StudentPerksMaster;
import com.bothash.admissionservice.service.StudentPerksMasterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/student-perks")
@RequiredArgsConstructor
public class StudentPerksMasterController {

    private final StudentPerksMasterService perksService;

    @GetMapping
    public ResponseEntity<List<StudentPerksMaster>> getAllPerks() {
        List<StudentPerksMaster> perks = perksService.getAllPerks();
        return ResponseEntity.ok(perks);
    }
    
 // GET single perk by id
    @GetMapping("/{id}")
    public ResponseEntity<StudentPerksMaster> getPerkById(@PathVariable Long id) {
        return ResponseEntity.ok(perksService.getPerk(id));
    }

    // CREATE new perk
    @PostMapping
    public ResponseEntity<StudentPerksMaster> createPerk(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        StudentPerksMaster created = perksService.createPerk(name.trim());
        return ResponseEntity.ok(created);
    }

    // UPDATE existing perk
    @PutMapping("/{id}")
    public ResponseEntity<StudentPerksMaster> updatePerk(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        StudentPerksMaster updated = perksService.updatePerk(id, name.trim());
        return ResponseEntity.ok(updated);
    }

    // DELETE perk
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePerk(@PathVariable Long id) {
        try {
            perksService.deletePerk(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Cannot delete this perk because admissions already exist for it.");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to delete the perk.");
        }
    }
}
