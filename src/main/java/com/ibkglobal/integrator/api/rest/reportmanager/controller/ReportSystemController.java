package com.ibkglobal.integrator.api.rest.reportmanager.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ibkglobal.integrator.api.rest.reportmanager.service.ReportSystemService;
import com.ibkglobal.integrator.exception.ApiException;
import com.ibkglobal.integrator.exception.model.ResultResponse;

@RestController
@RequestMapping("/integrator/1.0/systemmanager")
public class ReportSystemController {
	
	@Autowired
	ReportSystemService reportSystemService;
	
	@GetMapping("/reportsystem")
	public ResultResponse getReportSystem() throws Exception {
		try {
			return new ResultResponse(reportSystemService.getReportSystem());
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/usageInfo")
	public ResultResponse getUsageInfo() throws Exception {
		try {
			return new ResultResponse(reportSystemService.getUsageInfo());
		} catch (Exception ex) {
			throw new ApiException("get usageInfo Error: " + ex.getMessage());
		}
	}
	
	@GetMapping("/memoryUsage")
	public ResultResponse getMemoryUsage() throws Exception {
		return new ResultResponse(reportSystemService.getMemoryUsage());
	}
	
	@GetMapping("/diskUsage")
	public ResultResponse getDiskUsage() throws Exception {
		return new ResultResponse(reportSystemService.getDiskUsage());
	}
}
