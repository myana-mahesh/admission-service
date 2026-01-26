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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.bothash.admissionservice.dto.CreateStudentRequest;
import com.bothash.admissionservice.dto.StudentDto;
import com.bothash.admissionservice.enumpackage.GuardianRelation;
import com.bothash.admissionservice.service.AdmissionAuditService;
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
  private final AdmissionAuditService admissionAuditService;
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
          String prevFullName = existingStudent.getFullName();
          java.time.LocalDate prevDob = existingStudent.getDob();
          Gender prevGender = existingStudent.getGender();
          String prevAadhaar = existingStudent.getAadhaar();
          String prevEmail = existingStudent.getEmail();
          String prevNationality = existingStudent.getNationality();
          String prevReligion = existingStudent.getReligion();
          String prevCaste = existingStudent.getCaste();
          String prevMobile = existingStudent.getMobile();
          String prevAbsId = existingStudent.getAbsId();
          String prevBloodGroup = existingStudent.getBloodGroup();
          Integer prevAge = existingStudent.getAge();
          String prevBatch = existingStudent.getBatch();
          String prevRegistrationNumber = existingStudent.getRegistrationNumber();
          Long prevCourseId = existingStudent.getCourse() != null ? existingStudent.getCourse().getCourseId() : null;

          StudentAddress prevAddress = existingStudent.getAddresses().stream()
                  .filter(a -> "current".equalsIgnoreCase(a.getType()))
                  .findFirst()
                  .orElse(null);
          String prevAddressLine1 = prevAddress != null && prevAddress.getAddress() != null
                  ? prevAddress.getAddress().getLine1()
                  : null;
          String prevArea = prevAddress != null && prevAddress.getAddress() != null
                  ? prevAddress.getAddress().getArea()
                  : null;
          String prevCity = prevAddress != null && prevAddress.getAddress() != null
                  ? prevAddress.getAddress().getCity()
                  : null;
          String prevState = prevAddress != null && prevAddress.getAddress() != null
                  ? prevAddress.getAddress().getState()
                  : null;
          String prevPincode = prevAddress != null && prevAddress.getAddress() != null
                  ? prevAddress.getAddress().getPincode()
                  : null;

          Guardian prevFather = existingStudent.getGuardians().stream()
                  .filter(g -> g.getRelation() == GuardianRelation.Father)
                  .findFirst()
                  .orElse(null);
          Guardian prevMother = existingStudent.getGuardians().stream()
                  .filter(g -> g.getRelation() == GuardianRelation.Mother)
                  .findFirst()
                  .orElse(null);
          String prevFatherName = prevFather != null ? prevFather.getFullName() : null;
          String prevFatherMobile = prevFather != null ? prevFather.getMobile() : null;
          String prevMotherName = prevMother != null ? prevMother.getFullName() : null;
          String prevMotherMobile = prevMother != null ? prevMother.getMobile() : null;

          SscDetails prevSsc = existingStudent.getSscDetails();
          String prevSscBoard = prevSsc != null ? prevSsc.getBoard() : null;
          Integer prevSscYear = prevSsc != null ? prevSsc.getPassingYear() : null;
          Double prevSscPercent = prevSsc != null ? prevSsc.getPercentage() : null;
          String prevSscRegNo = prevSsc != null ? prevSsc.getRegistrationNumber() : null;

          HscDetails prevHsc = existingStudent.getHscDetails();
          String prevHscCollege = prevHsc != null ? prevHsc.getCollegeName() : null;
          String prevHscSubjects = prevHsc != null ? prevHsc.getSubjects() : null;
          String prevHscRegNo = prevHsc != null ? prevHsc.getRegistrationNumber() : null;
          Integer prevHscYear = prevHsc != null ? prevHsc.getPassingYear() : null;
          Integer prevHscPhysics = prevHsc != null ? prevHsc.getPhysicsMarks() : null;
          Integer prevHscChem = prevHsc != null ? prevHsc.getChemistryMarks() : null;
          Integer prevHscBio = prevHsc != null ? prevHsc.getBiologyMarks() : null;
          Double prevHscPcbPercent = prevHsc != null ? prevHsc.getPcbPercentage() : null;
          Double prevHscPercent = prevHsc != null ? prevHsc.getPercentage() : null;

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

          Admission2 admission = admission2Repository
                  .findFirstByStudentStudentIdOrderByUpdatedAtDesc(student.getStudentId());
          if (admission != null) {
              Map<String, Object> changes = new LinkedHashMap<>();
              addChange(changes, "fullName", prevFullName, student.getFullName());
              addChange(changes, "dob", prevDob, student.getDob());
              addChange(changes, "gender", prevGender, student.getGender());
              addChange(changes, "aadhaar", prevAadhaar, student.getAadhaar());
              addChange(changes, "email", prevEmail, student.getEmail());
              addChange(changes, "nationality", prevNationality, student.getNationality());
              addChange(changes, "religion", prevReligion, student.getReligion());
              addChange(changes, "caste", prevCaste, student.getCaste());
              addChange(changes, "mobile", prevMobile, student.getMobile());
              addChange(changes, "absId", prevAbsId, student.getAbsId());
              addChange(changes, "bloodGroup", prevBloodGroup, student.getBloodGroup());
              addChange(changes, "age", prevAge, student.getAge());
              addChange(changes, "batch", prevBatch, student.getBatch());
              addChange(changes, "registrationNumber", prevRegistrationNumber, student.getRegistrationNumber());
              addChange(changes, "courseId", prevCourseId,
                      student.getCourse() != null ? student.getCourse().getCourseId() : null);

              StudentAddress newAddress = student.getAddresses().stream()
                      .filter(a -> "current".equalsIgnoreCase(a.getType()))
                      .findFirst()
                      .orElse(null);
              addChange(changes, "address.line1", prevAddressLine1,
                      newAddress != null && newAddress.getAddress() != null
                              ? newAddress.getAddress().getLine1()
                              : null);
              addChange(changes, "address.area", prevArea,
                      newAddress != null && newAddress.getAddress() != null
                              ? newAddress.getAddress().getArea()
                              : null);
              addChange(changes, "address.city", prevCity,
                      newAddress != null && newAddress.getAddress() != null
                              ? newAddress.getAddress().getCity()
                              : null);
              addChange(changes, "address.state", prevState,
                      newAddress != null && newAddress.getAddress() != null
                              ? newAddress.getAddress().getState()
                              : null);
              addChange(changes, "address.pincode", prevPincode,
                      newAddress != null && newAddress.getAddress() != null
                              ? newAddress.getAddress().getPincode()
                              : null);

              Guardian newFather = student.getGuardians().stream()
                      .filter(g -> g.getRelation() == GuardianRelation.Father)
                      .findFirst()
                      .orElse(null);
              Guardian newMother = student.getGuardians().stream()
                      .filter(g -> g.getRelation() == GuardianRelation.Mother)
                      .findFirst()
                      .orElse(null);
              addChange(changes, "father.fullName", prevFatherName, newFather != null ? newFather.getFullName() : null);
              addChange(changes, "father.mobile", prevFatherMobile, newFather != null ? newFather.getMobile() : null);
              addChange(changes, "mother.fullName", prevMotherName, newMother != null ? newMother.getFullName() : null);
              addChange(changes, "mother.mobile", prevMotherMobile, newMother != null ? newMother.getMobile() : null);

              SscDetails newSsc = student.getSscDetails();
              addChange(changes, "ssc.board", prevSscBoard, newSsc != null ? newSsc.getBoard() : null);
              addChange(changes, "ssc.passingYear", prevSscYear, newSsc != null ? newSsc.getPassingYear() : null);
              addChange(changes, "ssc.percentage", prevSscPercent, newSsc != null ? newSsc.getPercentage() : null);
              addChange(changes, "ssc.registrationNumber", prevSscRegNo, newSsc != null ? newSsc.getRegistrationNumber() : null);

              HscDetails newHsc = student.getHscDetails();
              addChange(changes, "hsc.collegeName", prevHscCollege, newHsc != null ? newHsc.getCollegeName() : null);
              addChange(changes, "hsc.subjects", prevHscSubjects, newHsc != null ? newHsc.getSubjects() : null);
              addChange(changes, "hsc.registrationNumber", prevHscRegNo, newHsc != null ? newHsc.getRegistrationNumber() : null);
              addChange(changes, "hsc.passingYear", prevHscYear, newHsc != null ? newHsc.getPassingYear() : null);
              addChange(changes, "hsc.physicsMarks", prevHscPhysics, newHsc != null ? newHsc.getPhysicsMarks() : null);
              addChange(changes, "hsc.chemistryMarks", prevHscChem, newHsc != null ? newHsc.getChemistryMarks() : null);
              String newHscSubjects = newHsc != null ? newHsc.getSubjects() : null;
              boolean isPcm = newHscSubjects != null && newHscSubjects.trim().equalsIgnoreCase("PCM");
              String thirdSubjectField = isPcm ? "hsc.mathematicsMarks" : "hsc.biologyMarks";
              addChange(changes, thirdSubjectField, prevHscBio, newHsc != null ? newHsc.getBiologyMarks() : null);
              addChange(changes, "hsc.pcbPercentage", prevHscPcbPercent, newHsc != null ? newHsc.getPcbPercentage() : null);
              addChange(changes, "hsc.percentage", prevHscPercent, newHsc != null ? newHsc.getPercentage() : null);

              if (!changes.isEmpty()) {
                  admissionAuditService.record(
                          admission,
                          "STUDENT_UPDATED",
                          resolveAuditActor(null),
                          Map.of("studentId", student.getStudentId()),
                          changes
                  );
              }
          }
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

  private void addChange(Map<String, Object> changes, String field, Object before, Object after) {
      if (!Objects.equals(before, after)) {
          Map<String, Object> delta = new LinkedHashMap<>();
          delta.put("label", buildLabel(field));
          delta.put("before", before);
          delta.put("after", after);
          changes.put(field, delta);
      }
  }

  private String buildLabel(String field) {
      if (!StringUtils.hasText(field)) {
          return field;
      }
      String normalized = field.replace('.', ' ').replace('_', ' ').trim();
      StringBuilder out = new StringBuilder();
      char prev = 0;
      for (int i = 0; i < normalized.length(); i++) {
          char ch = normalized.charAt(i);
          if (Character.isUpperCase(ch) && i > 0 && Character.isLetterOrDigit(prev) && prev != ' ') {
              out.append(' ');
          }
          out.append(ch);
          prev = ch;
      }
      String spaced = out.toString().trim();
      if (spaced.isEmpty()) {
          return field;
      }
      return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  private String resolveAuditActor(String fallback) {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.isAuthenticated()) {
          Object principal = auth.getPrincipal();
          if (principal instanceof Jwt jwt) {
              String nameClaim = jwt.getClaimAsString("name");
              if (StringUtils.hasText(nameClaim)) {
                  return nameClaim;
              }
              String preferred = jwt.getClaimAsString("preferred_username");
              if (StringUtils.hasText(preferred)) {
                  return preferred;
              }
              String email = jwt.getClaimAsString("email");
              if (StringUtils.hasText(email)) {
                  return email;
              }
          }
          String name = auth.getName();
          if (StringUtils.hasText(name) && !"anonymousUser".equalsIgnoreCase(name)) {
              return name;
          }
      }
      return StringUtils.hasText(fallback) ? fallback : null;
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
