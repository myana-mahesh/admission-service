package com.bothash.admissionservice.controller;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.bothash.admissionservice.dto.CreateStudentRequest;
import com.bothash.admissionservice.dto.StudentDto;
import com.bothash.admissionservice.entity.Address;
import com.bothash.admissionservice.entity.Guardian;
import com.bothash.admissionservice.entity.Student;
import com.bothash.admissionservice.entity.StudentAddress;
import com.bothash.admissionservice.enumpackage.GuardianRelation;
import com.bothash.admissionservice.service.StudentService;

@RestController
@RequestMapping("/api/students")
@Validated
@RequiredArgsConstructor
public class StudentController {
  private final StudentService studentService;

  @PostMapping
  public ResponseEntity<Student> createOrUpdate(@RequestBody CreateStudentRequest req) {

      // Check if the student already exists using absId or mobile
      Student existingStudent = studentService.getPhoneNumber(req.getAbsId(), req.getMobile());

      if(existingStudent==null) {
    	  try {
    		  Optional<Student> existingStudentOpt = studentService.getById(req.getStudendId());
    	    	 if(existingStudentOpt.isPresent()) {
    	    		 existingStudent = existingStudentOpt.get();
    	    	 }
		} catch (Exception e) {
			
		}
    	 
      }
      
      Student student;
      
      // If the student already exists, update their details
      if (existingStudent != null) {
          existingStudent.setFullName(req.getFullName());
          existingStudent.setDob(req.getDob());
          existingStudent.setGender(req.getGender());
          existingStudent.setAadhaar(req.getAadhaar());
          existingStudent.setEmail(req.getEmail());
          existingStudent.setNationality(req.getNationality());
          existingStudent.setReligion(req.getReligion());
          existingStudent.setCaste(req.getCaste());
          existingStudent.setMobile(req.getMobile());
          existingStudent.setAbsId(req.getAbsId());

          // Update address if present
          if (req.getAddressLine1() != null) {
              Address addr = Address.builder()
                  .line1(req.getAddressLine1())
                  .city(req.getCity())
                  .state(req.getState())
                  .pincode(req.getPincode())
                  .build();
           // try to find the existing "current" StudentAddress
              Optional<StudentAddress> currentOpt = existingStudent.getAddresses().stream()
                  .filter(a -> "current".equalsIgnoreCase(a.getType()))
                  .findFirst();

              if (currentOpt.isPresent()) {
                  StudentAddress current = currentOpt.get();
                  // update existing instead of adding a new one
                  current.setAddress(addr);
                  // current.setUpdatedAt(Instant.now()); if you track it
              } else {
                  // if none exists, create one
                  StudentAddress sa = StudentAddress.builder()
                      .student(existingStudent)
                      .address(addr)
                      .type("current")
                      .build();
                  existingStudent.getAddresses().add(sa);
              }
          }
          
       // Build desired target list from request
          List<Guardian> incoming = new ArrayList<>();
          if (req.getFatherName() != null) {
              incoming.add(Guardian.builder()
                      .relation(GuardianRelation.Father)
                      .fullName(req.getFatherName())
                      .mobile(req.getFatherMobile())
                      .build());
          }
          if (req.getMotherName() != null) {
              incoming.add(Guardian.builder()
                      .relation(GuardianRelation.Mother)
                      .fullName(req.getMotherName())
                      .mobile(req.getMotherMobile())
                      .build());
          }

          // 1) Update existing or mark for removal by comparing a stable key (e.g., relation)
          Map<GuardianRelation, Guardian> incomingByRel = incoming.stream()
                  .collect(java.util.stream.Collectors.toMap(Guardian::getRelation, g -> g, (a,b)->a));

          Iterator<Guardian> it = existingStudent.getGuardians().iterator();
          while (it.hasNext()) {
              Guardian curr = it.next();
              Guardian desired = incomingByRel.remove(curr.getRelation()); // matched?
              if (desired != null) {
                  // update fields in-place
                  curr.setFullName(desired.getFullName());
                  curr.setMobile(desired.getMobile());
              } else {
                  // remove ones that are no longer present (orphanRemoval will delete)
                  it.remove();
                  curr.setStudent(null);
              }
          }

          // 2) Add the remaining new ones
          for (Guardian g : incomingByRel.values()) {
              g.setStudent(existingStudent);
              existingStudent.getGuardians().add(g);
          }


          student = studentService.createOrUpdateStudent(existingStudent); // Save updated student
      } else {
          // If the student doesn't exist, create a new one
          student = Student.builder()
              .fullName(req.getFullName())
              .dob(req.getDob())
              .gender(req.getGender())
              .aadhaar(req.getAadhaar())
              .email(req.getEmail())
              .nationality(req.getNationality())
              .religion(req.getReligion())
              .caste(req.getCaste())
              .mobile(req.getMobile())
              .absId(req.getAbsId())
              .build();

          // Add address and guardian if present in the request
          if (req.getAddressLine1() != null) {
              Address addr = Address.builder()
                  .line1(req.getAddressLine1())
                  .city(req.getCity())
                  .state(req.getState())
                  .pincode(req.getPincode())
                  .build();
              StudentAddress sa = StudentAddress.builder().student(student).address(addr).type("current").build();
              if(student.getAddresses()!=null)
            	  student.getAddresses().add(sa);
              else {
            	  List<StudentAddress> saList = Arrays.asList(sa);
            	  student.setAddresses(saList);
              }
          }
          List<Guardian> gaList = new ArrayList<>();
          if (req.getFatherName() != null) {
        	  Guardian guardian = Guardian.builder().student(student).relation(GuardianRelation.Father).fullName(req.getFatherName()).mobile(req.getFatherMobile()).build();
        	  if(student.getGuardians()!=null && !student.getGuardians().isEmpty()) {
        		  gaList = student.getGuardians();
        	  }else {
        		  gaList.add(guardian);
        	  }
          }
          if (req.getMotherName() != null) {
        	  Guardian guardian = Guardian.builder().student(student).relation(GuardianRelation.Mother).fullName(req.getMotherName()).mobile(req.getMotherMobile()).build();
        	if(gaList.isEmpty()) {
        		gaList = student.getGuardians();
        		if(gaList == null) {
        			gaList = new ArrayList<>();
        		}
        		gaList.add(guardian);
        	}else {
        		gaList.add(guardian);
      		  
      	  }
        			  
          }
          
          student.setGuardians(gaList);

          student = studentService.createOrUpdateStudent(student); // Create new student
      }

      return ResponseEntity.ok(student);
  }


  @GetMapping("/{id}")
  public ResponseEntity<Student> get(@PathVariable Long id){
    return studentService.getById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }
  
  @GetMapping
  public ResponseEntity<Page<StudentDto>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String q
  ) {
      // 0-based page index, sorted by createdAt desc (from Auditable)
      Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

      Page<Student> result = studentService.getStudents(q, pageable);
      Page<StudentDto> dtoPage = result.map(this::toDto);
      return ResponseEntity.ok(dtoPage);
  }
  
  private StudentDto toDto(Student s) {
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
