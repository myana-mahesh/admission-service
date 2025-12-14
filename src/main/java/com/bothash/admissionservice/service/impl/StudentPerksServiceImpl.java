package com.bothash.admissionservice.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.bothash.admissionservice.dto.StudentPerkDTO;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.entity.StudentPerksMaster;
import com.bothash.admissionservice.entity.StudentsPersMapping;
import com.bothash.admissionservice.repository.StudentPerksMasterRepository;
import com.bothash.admissionservice.repository.StudentsPersMappingRepository;
import com.bothash.admissionservice.service.StudentPerksService;
import com.bothash.admissionservice.repository.StudentRepository;

@Service
public class StudentPerksServiceImpl implements StudentPerksService{

	@Autowired
	private  StudentRepository studentRepository;
	@Autowired
    private  StudentPerksMasterRepository perksMasterRepository;
	@Autowired
    private  StudentsPersMappingRepository mappingRepository;

    @Transactional
    @Override
    public void assignPerkToStudent(Long studentId, Long perkId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));

        StudentPerksMaster perk = perksMasterRepository.findById(perkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perk not found"));

        boolean alreadyAssigned = mappingRepository
                .findByStudent_StudentIdAndStudentPerksMaster_Id(studentId, perkId)
                .isPresent();

        if (alreadyAssigned) {
            // silently ignore or throw 409 – I’ll ignore to make it idempotent
            return;
        }

        StudentsPersMapping mapping = new StudentsPersMapping();
        mapping.setStudent(student);
        mapping.setStudentPerksMaster(perk);

        mappingRepository.save(mapping);
    }

    @Transactional
    @Override
    public void removePerkFromStudent(Long studentId, Long perkId) {
        mappingRepository.deleteByStudent_StudentIdAndStudentPerksMaster_Id(studentId, perkId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<StudentPerkDTO> getPerksForStudent(Long studentId) {
        List<StudentsPersMapping> mappings = mappingRepository.findByStudent_StudentId(studentId);

        return mappings.stream()
                .map(m -> new StudentPerkDTO(
                        m.getStudentPerksMaster().getId(),
                        m.getStudentPerksMaster().getName()
                ))
                .collect(Collectors.toList());
    }
}
