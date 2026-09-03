package com.bothash.admissionservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bothash.admissionservice.entity.BranchRemittanceAcknowledgment;

public interface BranchRemittanceAcknowledgmentRepository
        extends JpaRepository<BranchRemittanceAcknowledgment, Long> {

    Optional<BranchRemittanceAcknowledgment> findByRemittanceIdAndAcknowledgedBy(
            Long remittanceId, String acknowledgedBy);

    /** All remittance ids the given user has acknowledged. */
    List<BranchRemittanceAcknowledgment> findByAcknowledgedBy(String acknowledgedBy);
}
