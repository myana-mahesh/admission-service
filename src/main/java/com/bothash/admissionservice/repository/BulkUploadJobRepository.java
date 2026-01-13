package com.bothash.admissionservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.BulkUploadJob;

public interface BulkUploadJobRepository extends JpaRepository<BulkUploadJob, UUID> {
    java.util.List<BulkUploadJob> findTop50ByTypeOrderByUploadedAtDesc(String type);
}
