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

        if (existing != null) {
            // ✅ UPDATE CASE
            if (input.getPercentage() != null) {
                existing.setPercentage(input.getPercentage());
            }
            if (StringUtils.hasText(input.getBoard())) {
                existing.setBoard(input.getBoard());
            }
            if (input.getPassingYear() != null) {
                existing.setPassingYear(input.getPassingYear());
            }
            if (StringUtils.hasText(input.getRegistrationNumber())) {
                existing.setRegistrationNumber(input.getRegistrationNumber());
            }

            return repository.save(existing);
        }

        // ✅ CREATE (IMPORTANT PART)
        SscDetails newSsc = new SscDetails();
        newSsc.setPercentage(input.getPercentage());
        newSsc.setBoard(input.getBoard());
        newSsc.setPassingYear(input.getPassingYear());
        newSsc.setRegistrationNumber(input.getRegistrationNumber());

        // 🔗 set BOTH sides
        newSsc.setStudent(student);
        student.setSscDetails(newSsc);

        // ✅ Save only student (cascade will save SSC)
        studentRepository.save(student);

        return newSsc;
    }



    public Optional<SscDetails> getByAdmission(Long admissionId) {
        return repository.findByStudent_StudentId(admissionId);
    }
}
