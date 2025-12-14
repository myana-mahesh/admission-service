package com.bothash.admissionservice.service;

import java.util.List;

import com.bothash.admissionservice.entity.StudentPerksMaster;

public interface StudentPerksMasterService {
	
	public List<StudentPerksMaster> getAllPerks();
	public StudentPerksMaster getPerk(Long id);
	public StudentPerksMaster createPerk(String name);
	public StudentPerksMaster updatePerk(Long id, String name);
	public void deletePerk(Long id);

}
