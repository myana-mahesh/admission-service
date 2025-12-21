package com.bothash.admissionservice.entity;

import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.bothash.admissionservice.enumpackage.AdmissionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "admission2",
       indexes = {
           @Index(name = "idx_adm_course_year", columnList = "course_id,year_id"),
           @Index(name = "idx_adm_student_year", columnList = "student_id,year_id")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Admission2 extends Auditable {
	
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long admissionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonManagedReference
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "year_id", nullable = false)
    private AcademicYear year;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(length = 50)
    private String formNo;

    private LocalDate formDate;

    private LocalDate dateOfAdm;

    @Column(length = 160)
    private String lastCollege;

    @Column(length = 160)
    private String collegeAttended;

    @Column(length = 120)
    private String collegeLocation;

    @Column(length = 255)
    private String remarks;

    private LocalDate examDueDate;
    
    
    private Double totalFees;
    
    private Double discount;
    
    private Integer noOfInstallments;
    
    private Integer courseYears;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AdmissionStatus status = AdmissionStatus.Draft;

    // Convenience relations
//    @OneToMany(mappedBy = "admission", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<AdmissionDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "admission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference("admission-uploads")
    private List<FileUpload> uploads = new ArrayList<>();

    @OneToMany(mappedBy = "admission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference("admission-installments")
    private List<FeeInstallment> installments = new ArrayList<>();

    @OneToOne(mappedBy = "admission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AdmissionSignoff signoff;
    
    @CreationTimestamp
	private LocalDateTime createdOn;
	
	@UpdateTimestamp
	private LocalDateTime modifiedOn;

    @OneToOne(mappedBy = "admission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference("admission-cancellation")
    private AdmissionCancellation cancellation;

/*    // 🔥 INVERSE SIDE
    @OneToOne(mappedBy = "admission", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SscDetails sscDetails;

    @OneToOne(mappedBy = "admission", cascade = CascadeType.ALL, orphanRemoval = true)
    private HscDetails hscDetails;*/

    @Column(name = "batch")
    private String batch;

    @Column(name = "registration_number", unique = true)
    private String registrationNumber;

    private String referenceName;

    // Admission taken at
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_branch_id")
    private BranchMaster admissionBranch;

    // Lecture attended at
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecture_branch_id")
    private BranchMaster lectureBranch;

}
