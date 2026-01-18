package com.bothash.admissionservice.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admission_document_return")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionDocumentReturn extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long returnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", nullable = false)
    private Admission2 admission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_type_id", nullable = false)
    private DocumentType docType;

    @Column(name = "returned_on")
    private LocalDate returnedOn;

    @Column(length = 500)
    private String reason;

    @Column(length = 120)
    private String returnedBy;

    @Column(length = 20)
    private String actionType;

    @Column(name = "resubmitted_on")
    private LocalDate resubmittedOn;

    @Column(length = 120)
    private String resubmittedTo;

    @Column(length = 500)
    private String resubmissionReason;

    @Column(length = 120)
    private String resubmittedBy;
}
