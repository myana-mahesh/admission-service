package com.bothash.admissionservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.AdmissionDocument;

@Repository
public interface AdmissionDocumentRepository extends JpaRepository<AdmissionDocument, Long> {
	  List<AdmissionDocument> findByAdmissionAdmissionId(Long admissionId);
	  Optional<AdmissionDocument> findByAdmissionAdmissionIdAndDocTypeDocTypeId(Long admissionId, Long docTypeId);

}
