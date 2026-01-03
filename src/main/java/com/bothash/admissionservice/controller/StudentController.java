package com.bothash.admissionservice.controller;

import com.bothash.admissionservice.entity.*;
import com.bothash.admissionservice.enumpackage.Gender;
import com.bothash.admissionservice.repository.CourseRepository;
import com.bothash.admissionservice.repository.Admission2Repository;
import com.bothash.admissionservice.repository.StudentsPersMappingRepository;
import com.bothash.admissionservice.service.HscDetailsService;
import com.bothash.admissionservice.service.SscDetailsService;
import com.bothash.admissionservice.service.StudentOtherPaymentValueService;
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
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.bothash.admissionservice.dto.CreateStudentRequest;
import com.bothash.admissionservice.dto.StudentDto;
import com.bothash.admissionservice.enumpackage.GuardianRelation;
import com.bothash.admissionservice.service.StudentService;

@RestController
@RequestMapping("/api/students")
@Validated
@RequiredArgsConstructor
public class StudentController {
  private final StudentService studentService;
  private final SscDetailsService sscDetailsService;
  private final HscDetailsService hscDetailsService;
    private final CourseRepository courseRepo;
  private final Admission2Repository admission2Repository;
  private final StudentsPersMappingRepository studentsPersMappingRepository;
  private final StudentOtherPaymentValueService studentOtherPaymentValueService;
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
      Course course = null;
      if (req.getCourseCode() != null) {
          course = courseRepo.findById(req.getCourseCode()).orElse(null);
      }

      String registrationNumber = StringUtils.hasText(req.getRegistrationNumber())
              ? req.getRegistrationNumber().trim()
              : null;

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
          if (StringUtils.hasText(req.getAbsId())) {
              existingStudent.setAbsId(req.getAbsId());
          }
          existingStudent.setBloodGroup(req.getBloodGroup());
          existingStudent.setAge(req.getAge());
          existingStudent.setBatch(req.getBatch());
          existingStudent.setRegistrationNumber(registrationNumber);
          existingStudent.setCourse(course);
         // existingStudent.setAcademicYearLabel(req.getAcademicYearLabel());


          // Update address if present
          if (req.getAddressLine1() != null) {
              Address addr = Address.builder()
                  .line1(req.getAddressLine1())
                  .area(req.getArea())
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
          sscDetailsService.saveOrUpdateByStudent(student.getStudentId(),req.getSscDetails());
          hscDetailsService.saveOrUpdateByStudent(student.getStudentId(),req.getHscDetails());
          studentOtherPaymentValueService.saveValues(student.getStudentId(), req.getOtherPayments());
      } else {
          // If the student doesn't exist, create a new one
          Student.StudentBuilder builder = Student.builder()
              .fullName(req.getFullName())
              .dob(req.getDob())
              .gender(req.getGender())
              .aadhaar(req.getAadhaar())
              .email(req.getEmail())
              .nationality(req.getNationality())
              .religion(req.getReligion())
              .caste(req.getCaste())
              .mobile(req.getMobile())
              .bloodGroup(req.getBloodGroup())
              .age(req.getAge())
              .batch(req.getBatch())
              .registrationNumber(registrationNumber)
              .course(course);
          if (StringUtils.hasText(req.getAbsId())) {
              builder.absId(req.getAbsId());
          }
          student = builder.build();

          // Add address and guardian if present in the request
          if (req.getAddressLine1() != null) {
              Address addr = Address.builder()
                  .line1(req.getAddressLine1())
                  .area(req.getArea())
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
          sscDetailsService.saveOrUpdateByStudent(student.getStudentId(),req.getSscDetails());
          hscDetailsService.saveOrUpdateByStudent(student.getStudentId(),req.getHscDetails());
          studentOtherPaymentValueService.saveValues(student.getStudentId(), req.getOtherPayments());
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
	    dto.setAge(s.getAge());
	    dto.setBatch(s.getBatch());
	    dto.setRegistrationNumber(s.getRegistrationNumber());
	    if (s.getCourse() != null) {
	        dto.setCourseId(s.getCourse().getCourseId());
	        dto.setCourseName(s.getCourse().getName());
	    }
	    return dto;
	}



    @GetMapping("/students-filter")
    public ResponseEntity<Page<StudentDto>> listStudents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,

            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long collegeId,
            @RequestParam(required = false) Long admissionYearId,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) Long perkId,
            @RequestParam(required = false) String gender
    ) {

        Gender genderEnum = null;

        if (gender != null) {
            for (Gender g : Gender.values()) {
                if (g.name().equalsIgnoreCase(gender)) {
                    genderEnum = g;
                    break;
                }
            }
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        List<Long> admissionFilteredIds = null;
        if (collegeId != null || courseId != null || admissionYearId != null) {
            admissionFilteredIds = admission2Repository.findStudentIdsByFilters(
                    collegeId, courseId, admissionYearId
            );
            if (admissionFilteredIds.isEmpty()) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
        }

        List<Long> perkFilteredIds = null;
        if (perkId != null) {
            perkFilteredIds = studentsPersMappingRepository.findStudentIdsByPerkId(perkId);
            if (perkFilteredIds.isEmpty()) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
        }

        List<Long> finalIds = null;
        if (admissionFilteredIds != null && perkFilteredIds != null) {
            finalIds = admissionFilteredIds.stream()
                    .filter(perkFilteredIds::contains)
                    .toList();
            if (finalIds.isEmpty()) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
        } else if (admissionFilteredIds != null) {
            finalIds = admissionFilteredIds;
        } else if (perkFilteredIds != null) {
            finalIds = perkFilteredIds;
        }
        Page<Student> students = studentService.getStudents(
                q, batch, genderEnum, finalIds, pageable
        );

        return ResponseEntity.ok(students.map(this::toDto));
    }
}
