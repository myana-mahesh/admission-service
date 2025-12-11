package com.bothash.admissionservice.service.impl;

import com.bothash.admissionservice.dto.StudentDto;
import com.bothash.admissionservice.dto.StudentDto;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.repository.StudentRepository;
import com.bothash.admissionservice.service.StudentService;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentServiceImpl implements StudentService {
	private final StudentRepository studentRepo;

	@Override
	public Student createOrUpdateStudent(Student s) {
		return studentRepo.save(s);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Student> getById(Long id) {
		return studentRepo.findById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Student> getByAbsId(String absId) {
		return studentRepo.findByAbsId(absId);
	}

	@Override
	public Student getByAbsIdorPhoneNumber(String absId, String mobile) {
		return studentRepo.findByAbsIdOrMobile(absId,mobile);
	}

	@Override
	public Student getPhoneNumber(String absId, String mobile) {
		// TODO Auto-generated method stub
		return studentRepo.findByMobile(mobile);
	}
	
	@Override
    public Page<Student> getStudents(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return studentRepo.findAll(pageable);
        }
        return studentRepo.search(q, pageable);
    }
	
	@Override
	public StudentDto toDto(Student s) {
	    StudentDto dto = new StudentDto();
	    dto.setStudentId(s.getStudentId());
	    dto.setAbsId(s.getAbsId());
	    dto.setFullName(s.getFullName());
	    dto.setDob(s.getDob());
	    dto.setGender(s.getGender().name());
	    dto.setAadhaar(s.getAadhaar());
	    dto.setNationality(s.getNationality());
	    dto.setReligion(s.getReligion());
	    dto.setCaste(s.getCaste());
	    dto.setEmail(s.getEmail());
	    dto.setMobile(s.getMobile());
	    return dto;
	}
}
