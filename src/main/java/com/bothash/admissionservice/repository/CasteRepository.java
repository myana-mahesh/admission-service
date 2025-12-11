package com.bothash.admissionservice.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bothash.admissionservice.entity.Caste;

@Repository
public interface CasteRepository extends JpaRepository<Caste, Long> { }
