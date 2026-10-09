package com.ibkglobal.integrator.api.rest.reportmanager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ibkglobal.integrator.api.rest.reportmanager.service.ReportProcessService;
import com.ibkglobal.integrator.exception.model.ResultResponse;

@RestController
@RequestMapping("/integrator/1.0/reportmanager")
public class ReportProcessController {
	
	@Autowired
	ReportProcessService reportService;
	
	@GetMapping("/msgList")
	public ResultResponse getMsgList(@RequestParam String cacheType, @RequestParam String intfId, @RequestParam String pageNo, @RequestParam String pageSize) throws Exception {
		return reportService.getMsgListPage(cacheType, intfId, pageNo, pageSize);
	}
	
	@GetMapping("/msgInfo")
	public ResultResponse getMsgInfo(@RequestParam String cacheType, @RequestParam String intfId) throws Exception {
		return reportService.getMsgInfo(cacheType, intfId);
	}
}
