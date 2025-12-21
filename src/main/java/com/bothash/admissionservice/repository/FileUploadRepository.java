package com.bothash.admissionservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.FileUpload;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {
	  List<FileUpload> findByAdmissionAdmissionId(Long admissionId);
	  FileUpload findByAdmissionAdmissionIdAndDocTypeDocTypeId(Long admissionId, Long docTypeId);
	
	FileUpload findByAdmissionAdmissionIdAndDocTypeDocTypeIdAndInstallmentInstallmentId(Long admissionId,
			Long docTypeId, Long installmentId);
	
    long deleteByInstallment_InstallmentId(Long installmentId);
	List<FileUpload> findByInstallment_InstallmentId(Long installmentId);

}
