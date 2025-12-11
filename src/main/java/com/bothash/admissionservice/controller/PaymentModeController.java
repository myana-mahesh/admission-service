package com.bothash.admissionservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bothash.admissionservice.dto.PaymentModeDto;
import com.bothash.admissionservice.entity.PaymentModeMaster;
import com.bothash.admissionservice.service.impl.PaymentModeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payment-modes")
@RequiredArgsConstructor
public class PaymentModeController {

    private final PaymentModeService paymentModeMasterService;

    @GetMapping
    public List<PaymentModeDto> listActive() {
        return paymentModeMasterService.getActiveModes();
    }
    
 // JSON API for other server-side clients / JS

    @GetMapping("/list")
    public List<PaymentModeMaster> listApi() {
        return paymentModeMasterService.findAllSorted();
    }

    @GetMapping("/byid/{id}")
    public ResponseEntity<PaymentModeMaster> getOneApi(@PathVariable Long id) {
        return new ResponseEntity<PaymentModeMaster>(paymentModeMasterService.findById(id),HttpStatus.OK);
    }

    @PostMapping("")
    public ResponseEntity<?> saveApi(@RequestBody PaymentModeMaster form) {
        try {
            PaymentModeMaster saved = paymentModeMasterService.save(form);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @DeleteMapping("/deleteById/{id}")
    public ResponseEntity<Void> deleteApi(@PathVariable Long id) {
        paymentModeMasterService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}

