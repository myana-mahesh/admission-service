package com.bothash.admissionservice.service;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.HscDetails;
import com.bothash.admissionservice.repository.Admission2Repository;
import com.bothash.admissionservice.repository.HscDetailsRepository;
import com.bothash.admissionservice.repository.StudentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class HscDetailsService {

    private final HscDetailsRepository hscRepository;
    private final Admission2Repository admissionRepository;
    private final StudentRepository studentRepository;

    public HscDetails saveOrUpdateByStudent(Long studentId, HscDetails input) {

        com.bothash.admissionservice.entity.Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        HscDetails existing = student.getHscDetails();
        if (input == null) {
            return existing;
        }

        if (existing != null) {
            // ✅ UPDATE CASE
            if (StringUtils.hasText(input.getCollegeName())) {
                existing.setCollegeName(input.getCollegeName());
            }
            if (StringUtils.hasText(input.getSubjects())) {
                existing.setSubjects(input.getSubjects());
            }
            if (StringUtils.hasText(input.getBoard())) {
                existing.setBoard(input.getBoard());
            }
            if (StringUtils.hasText(input.getRegistrationNumber())) {
                existing.setRegistrationNumber(input.getRegistrationNumber());
            }
            if (input.getPassingYear() != null) {
                existing.setPassingYear(input.getPassingYear());
            }
            if (input.getPhysicsMarks() != null) {
                existing.setPhysicsMarks(input.getPhysicsMarks());
            }
            if (input.getChemistryMarks() != null) {
                existing.setChemistryMarks(input.getChemistryMarks());
            }
            if (input.getBiologyMarks() != null) {
                existing.setBiologyMarks(input.getBiologyMarks());
            }
            if (input.getPcbPercentage() != null) {
                existing.setPcbPercentage(input.getPcbPercentage());
            }
            if (input.getPercentage() != null) {
                existing.setPercentage(input.getPercentage());
            }

            return hscRepository.save(existing);
        }

        HscDetails newHsc = new HscDetails();
        newHsc.setCollegeName(input.getCollegeName());
        newHsc.setSubjects(input.getSubjects());
        newHsc.setBoard(input.getBoard());
        newHsc.setRegistrationNumber(input.getRegistrationNumber());
        newHsc.setPassingYear(input.getPassingYear());
        newHsc.setPhysicsMarks(input.getPhysicsMarks());
        newHsc.setChemistryMarks(input.getChemistryMarks());
        newHsc.setBiologyMarks(input.getBiologyMarks());
        newHsc.setPcbPercentage(input.getPcbPercentage());
        newHsc.setPercentage(input.getPercentage());

        newHsc.setStudent(student);
        student.setHscDetails(newHsc);

        studentRepository.save(student);

        return newHsc;
    }
}
