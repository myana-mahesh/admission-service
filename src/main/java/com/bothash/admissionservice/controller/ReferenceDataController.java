package com.bothash.admissionservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bothash.admissionservice.entity.NationalityMaster;
import com.bothash.admissionservice.entity.ReligionMaster;
import com.bothash.admissionservice.repository.NationalityMasterRepository;
import com.bothash.admissionservice.repository.ReligionMasterRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final NationalityMasterRepository nationalityRepository;
    private final ReligionMasterRepository religionRepository;

    @GetMapping("/nationalities")
    public List<NationalityMaster> listNationalities() {
        return nationalityRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @GetMapping("/religions")
    public List<ReligionMaster> listReligions() {
        return religionRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @PostMapping("/nationalities")
    public NationalityMaster createNationality(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (nationalityRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nationality already exists");
        }
        NationalityMaster master = new NationalityMaster();
        master.setName(name);
        return nationalityRepository.save(master);
    }

    @PutMapping("/nationalities/{id}")
    public NationalityMaster updateNationality(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        NationalityMaster master = nationalityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nationality not found"));
        if (!master.getName().equalsIgnoreCase(name) && nationalityRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nationality already exists");
        }
        master.setName(name);
        return nationalityRepository.save(master);
    }

    @DeleteMapping("/nationalities/{id}")
    public void deleteNationality(@PathVariable Long id) {
        if (!nationalityRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nationality not found");
        }
        try {
            nationalityRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete this nationality because admissions already exist for it.");
        }
    }

    @PostMapping("/religions")
    public ReligionMaster createReligion(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (religionRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Religion already exists");
        }
        ReligionMaster master = new ReligionMaster();
        master.setName(name);
        return religionRepository.save(master);
    }

    @PutMapping("/religions/{id}")
    public ReligionMaster updateReligion(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        ReligionMaster master = religionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Religion not found"));
        if (!master.getName().equalsIgnoreCase(name) && religionRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Religion already exists");
        }
        master.setName(name);
        return religionRepository.save(master);
    }

    @DeleteMapping("/religions/{id}")
    public void deleteReligion(@PathVariable Long id) {
        if (!religionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Religion not found");
        }
        try {
            religionRepository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete this religion because admissions already exist for it.");
        }
    }
}
