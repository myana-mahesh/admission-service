package com.bothash.admissionservice.service.impl;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.bothash.admissionservice.entity.StudentPerksMaster;
import com.bothash.admissionservice.repository.StudentPerksMasterRepository;
import com.bothash.admissionservice.service.StudentPerksMasterService;


@Service
public class StudentPerksMasterServiceImpl implements StudentPerksMasterService {

	@Autowired
    private StudentPerksMasterRepository perksRepo;

    @Override
    public List<StudentPerksMaster> getAllPerks() {
        // Sort by name for consistent UI
        return perksRepo.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }
    @Override
    public StudentPerksMaster getPerk(Long id) {
        return perksRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Perk not found with id: " + id));
    }

    @Override
    public StudentPerksMaster createPerk(String name) {
        StudentPerksMaster perk = new StudentPerksMaster();
        perk.setName(name);
        return perksRepo.save(perk);
    }

    @Override
    public StudentPerksMaster updatePerk(Long id, String name) {
        StudentPerksMaster perk = getPerk(id);
        perk.setName(name);
        return perksRepo.save(perk);
    }

    @Override
    public void deletePerk(Long id) {
        if (!perksRepo.existsById(id)) {
            throw new IllegalArgumentException("Perk not found with id: " + id);
        }
        perksRepo.deleteById(id);
    }
}

