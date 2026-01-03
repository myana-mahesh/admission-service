package com.bothash.admissionservice.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "file_upload",
       indexes = {
           @Index(name = "idx_upload_adm_doctype", columnList = "admission_id,doc_type_id"),
           @Index(name = "idx_upload_installment_payment", columnList = "installment_payment_id")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileUpload extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "admission_id", nullable = false)
    @JsonBackReference("admission-uploads")
    private Admission2 admission;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doc_type_id")
    private DocumentType docType; // nullable for misc
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "installment_id")
    private FeeInstallment installment; 

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "installment_payment_id")
    private FeeInstallmentPayment installmentPayment;

    @Column(length = 500, nullable = false)
    private String filename;

    @Column(length = 80)
    private String mimeType;

    @Column
    private Integer sizeBytes;

    @Column(length = 500, nullable = false)
    private String storageUrl;

    @Column(length = 64)
    private String sha256;
    
    private String label;
    
    
    @CreationTimestamp
	private LocalDateTime createdOn;
	
	@UpdateTimestamp
	private LocalDateTime modifiedOn;
}
