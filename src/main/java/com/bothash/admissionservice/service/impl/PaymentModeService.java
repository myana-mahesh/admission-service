package com.bothash.admissionservice.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.bothash.admissionservice.dto.PaymentModeDto;
import com.bothash.admissionservice.entity.PaymentModeMaster;
import com.bothash.admissionservice.repository.PaymentModeRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentModeService {
    private final PaymentModeRepository repo;

    public List<PaymentModeDto> getActiveModes() {
        return repo.findByActiveTrueOrderByDisplayOrderAsc()
                   .stream()
                   .map(pm -> new PaymentModeDto(pm.getCode(), pm.getLabel()))
                   .toList();
    }
    
    public PaymentModeMaster getByMode(String mode) {
        return repo.findByCodeAndActiveTrueOrderByDisplayOrderAsc(mode);
    }
   
    public List<PaymentModeMaster> findAll() {
        return repo.findAll();
    }

    public List<PaymentModeMaster> findAllSorted() {
        return repo.findAll(Sort.by(Sort.Direction.ASC, "displayOrder", "label"));
    }

    public PaymentModeMaster findById(Long id) {
        return repo.findByPaymentModeId(id);
    }

    public PaymentModeMaster save(PaymentModeMaster pm) {
        // simple uniqueness check for code
    	if(pm.getLabel()==null) {
    		pm.setLabel(pm.getCode());
    	}
        if (pm.getPaymentModeId() == null) {
            if (repo.existsByCode(pm.getCode())) {
                throw new IllegalArgumentException("Payment mode code already exists: " + pm.getCode());
            }
        } else {
            if (repo.existsByCodeAndPaymentModeIdNot(pm.getCode(), pm.getPaymentModeId())) {
                throw new IllegalArgumentException("Payment mode code already exists: " + pm.getCode());
            }
        }
        return repo.save(pm);
    }

    public void deleteById(Long id) {
        repo.deleteById(id);
    }
}