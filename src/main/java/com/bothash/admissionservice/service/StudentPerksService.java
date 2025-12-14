package com.bothash.admissionservice.service;

import java.util.List;

import com.bothash.admissionservice.dto.StudentPerkDTO;

public interface StudentPerksService {

	void assignPerkToStudent(Long studentId, Long perkId);
	
	void removePerkFromStudent(Long studentId, Long perkId);
	
	List<StudentPerkDTO> getPerksForStudent(Long studentId);
	
	
}
