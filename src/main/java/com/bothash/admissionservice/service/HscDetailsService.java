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

        boolean hasAnyValue = StringUtils.hasText(input.getCollegeName())
                || StringUtils.hasText(input.getSubjects())
                || StringUtils.hasText(input.getBoard())
                || StringUtils.hasText(input.getRegistrationNumber())
                || input.getPassingYear() != null
                || input.getPhysicsMarks() != null
                || input.getChemistryMarks() != null
                || input.getBiologyMarks() != null
                || input.getPcbPercentage() != null
                || input.getPercentage() != null;

        if (existing != null) {
            existing.setCollegeName(StringUtils.hasText(input.getCollegeName()) ? input.getCollegeName().trim() : null);
            existing.setSubjects(StringUtils.hasText(input.getSubjects()) ? input.getSubjects().trim() : null);
            existing.setBoard(StringUtils.hasText(input.getBoard()) ? input.getBoard().trim() : null);
            existing.setRegistrationNumber(StringUtils.hasText(input.getRegistrationNumber())
                    ? input.getRegistrationNumber().trim()
                    : null);
            existing.setPassingYear(input.getPassingYear());
            existing.setPhysicsMarks(input.getPhysicsMarks());
            existing.setChemistryMarks(input.getChemistryMarks());
            existing.setBiologyMarks(input.getBiologyMarks());
            existing.setPcbPercentage(input.getPcbPercentage());
            existing.setPercentage(input.getPercentage());

            return hscRepository.save(existing);
        }

        if (!hasAnyValue) {
            return null;
        }

        HscDetails newHsc = new HscDetails();
        newHsc.setCollegeName(StringUtils.hasText(input.getCollegeName()) ? input.getCollegeName().trim() : null);
        newHsc.setSubjects(StringUtils.hasText(input.getSubjects()) ? input.getSubjects().trim() : null);
        newHsc.setBoard(StringUtils.hasText(input.getBoard()) ? input.getBoard().trim() : null);
        newHsc.setRegistrationNumber(StringUtils.hasText(input.getRegistrationNumber())
                ? input.getRegistrationNumber().trim()
                : null);
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
