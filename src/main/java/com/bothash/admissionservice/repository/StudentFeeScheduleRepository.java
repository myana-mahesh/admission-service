package com.bothash.admissionservice.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.StudentFeeSchedule;

@Repository
public interface StudentFeeScheduleRepository extends JpaRepository<StudentFeeSchedule, Long> {

    @Query("SELECT s FROM StudentFeeSchedule s WHERE s.student.studentId = :studentId ORDER BY s.scheduledDate DESC")
    List<StudentFeeSchedule> findByStudentIdOrderByScheduledDateDesc(@Param("studentId") Long studentId);

    @Query("SELECT s FROM StudentFeeSchedule s WHERE s.student.studentId = :studentId AND s.status = :status ORDER BY s.scheduledDate ASC")
    List<StudentFeeSchedule> findByStudentIdAndStatus(@Param("studentId") Long studentId, @Param("status") String status);

    @Query("SELECT s FROM StudentFeeSchedule s WHERE s.scheduledDate = :date AND s.status = 'PENDING' ORDER BY s.student.studentId")
    List<StudentFeeSchedule> findPendingSchedulesForDate(@Param("date") LocalDate date);

    @Query("SELECT s FROM StudentFeeSchedule s WHERE s.scheduledDate BETWEEN :startDate AND :endDate AND s.status = 'PENDING' ORDER BY s.scheduledDate ASC")
    List<StudentFeeSchedule> findPendingSchedulesBetweenDates(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT s FROM StudentFeeSchedule s WHERE s.scheduledDate < :date AND s.status = 'PENDING' ORDER BY s.scheduledDate ASC")
    List<StudentFeeSchedule> findOverdueSchedules(@Param("date") LocalDate date);

    @Query("SELECT COUNT(s) FROM StudentFeeSchedule s WHERE s.student.studentId = :studentId AND s.status = 'PENDING'")
    Long countPendingByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT s FROM StudentFeeSchedule s WHERE s.status = :status ORDER BY s.scheduledDate DESC")
    Page<StudentFeeSchedule> findByStatus(@Param("status") String status, Pageable pageable);
}
