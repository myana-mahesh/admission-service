package com.bothash.admissionservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bothash.admissionservice.entity.StudentsPersMapping;

public interface StudentsPersMappingRepository extends JpaRepository<StudentsPersMapping, Long> {

    List<StudentsPersMapping> findByStudent_StudentId(Long studentId);

    Optional<StudentsPersMapping> findByStudent_StudentIdAndStudentPerksMaster_Id(Long studentId, Long perkId);

    void deleteByStudent_StudentIdAndStudentPerksMaster_Id(Long studentId, Long perkId);

    @Query("select distinct m.student.studentId from StudentsPersMapping m where m.studentPerksMaster.id = :perkId")
    List<Long> findStudentIdsByPerkId(@Param("perkId") Long perkId);
}
