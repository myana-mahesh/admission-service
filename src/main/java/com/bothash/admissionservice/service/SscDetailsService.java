package com.bothash.admissionservice.service;

import com.bothash.admissionservice.entity.Admission2;
import com.bothash.admissionservice.entity.SscDetails;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.repository.Admission2Repository;
import com.bothash.admissionservice.repository.SscDetailsRepository;
import com.bothash.admissionservice.repository.StudentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class SscDetailsService {

    private final SscDetailsRepository repository;
    private final Admission2Repository admissionRepository;
    private final StudentRepository studentRepository;

    public SscDetailsService(SscDetailsRepository repository,
                             Admission2Repository admissionRepository, StudentRepository studentRepository) {
        this.repository = repository;
        this.admissionRepository = admissionRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public SscDetails saveOrUpdateByStudent(Long studentId, SscDetails input) {

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // 🔎 Check existing SSC details
        SscDetails existing = student.getSscDetails();
        if (input == null) {
            return existing;
        }

        boolean hasAnyValue = input.getPercentage() != null
                || StringUtils.hasText(input.getBoard())
                || input.getPassingYear() != null
                || StringUtils.hasText(input.getRegistrationNumber());

        if (existing != null) {
            existing.setPercentage(input.getPercentage());
            existing.setBoard(StringUtils.hasText(input.getBoard()) ? input.getBoard().trim() : null);
            existing.setPassingYear(input.getPassingYear());
            existing.setRegistrationNumber(StringUtils.hasText(input.getRegistrationNumber())
                    ? input.getRegistrationNumber().trim()
                    : null);

            return repository.save(existing);
        }

        if (!hasAnyValue) {
            return null;
        }

        SscDetails newSsc = new SscDetails();
        newSsc.setPercentage(input.getPercentage());
        newSsc.setBoard(StringUtils.hasText(input.getBoard()) ? input.getBoard().trim() : null);
        newSsc.setPassingYear(input.getPassingYear());
        newSsc.setRegistrationNumber(StringUtils.hasText(input.getRegistrationNumber())
                ? input.getRegistrationNumber().trim()
                : null);

        newSsc.setStudent(student);
        student.setSscDetails(newSsc);

        studentRepository.save(student);

        return newSsc;
    }



    public Optional<SscDetails> getByAdmission(Long admissionId) {
        return repository.findByStudent_StudentId(admissionId);
    }
}
