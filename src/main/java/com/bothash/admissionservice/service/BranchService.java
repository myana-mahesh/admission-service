package com.bothash.admissionservice.service;

import com.bothash.admissionservice.entity.BranchMaster;
import com.bothash.admissionservice.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public List<BranchMaster> getAllBranches() {
        return branchRepository.findAll();
    }
}
