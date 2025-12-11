package com.bothash.admissionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.Address;

@Repository 
public interface AddressRepository extends JpaRepository<Address, Long> {}
