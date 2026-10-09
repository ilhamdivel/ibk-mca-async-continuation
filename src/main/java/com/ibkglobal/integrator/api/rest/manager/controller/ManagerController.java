package com.ibkglobal.integrator.api.rest.manager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibkglobal.integrator.api.rest.manager.service.ManagerService;
import com.ibkglobal.integrator.exception.model.ResultResponse;

@RestController
@RequestMapping("/integrator/1.0/manager")
public class ManagerController {
	
	@Autowired
	ManagerService managerService;
	
	@GetMapping("/usageInfo")
	public ResultResponse getUsageInfo() throws Exception {
		return new ResultResponse(managerService.getUsageInfo());
	}
	
	@GetMapping("/memoryUsage")
	public ResultResponse getMemoryUsage() throws Exception {
		return new ResultResponse(managerService.getMemoryUsage());
	}
	
	@GetMapping("/diskUsage")
	public ResultResponse getDiskUsage() throws Exception {
		return new ResultResponse(managerService.getDiskUsage());
	}
}
