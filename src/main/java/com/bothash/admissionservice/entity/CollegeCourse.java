package com.bothash.admissionservice.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "college_course",
       indexes = {
           @Index(name = "ix_college_course_college", columnList = "college_id"),
           @Index(name = "ix_college_course_course", columnList = "course_id")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_college_course", columnNames = {"college_id", "course_id"})
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollegeCourse extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long collegeCourseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "college_id", nullable = false)
    private College college;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private Integer totalSeats;

    @Column(nullable = false)
    private Integer onHoldSeats = 0;

    @Column(nullable = false)
    private Integer allocatedSeats = 0;

    private OffsetDateTime lastAllocatedAt;
}
