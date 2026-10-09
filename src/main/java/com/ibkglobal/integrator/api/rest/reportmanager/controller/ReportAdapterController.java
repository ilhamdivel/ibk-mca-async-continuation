package com.ibkglobal.integrator.api.rest.reportmanager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ibkglobal.integrator.api.rest.reportmanager.service.ReportAdapterService;
import com.ibkglobal.integrator.exception.ApiException;
import com.ibkglobal.integrator.exception.model.ResultResponse;

@RestController
@RequestMapping("/integrator/1.0/reportmanager")
public class ReportAdapterController {
	
	@Autowired
	ReportAdapterService reportAdapterService;
	
	/**
	 * Get Report Adapter
	 * @return
	 * @throws Exception
	 */
	@GetMapping("/adapterList")
	public ResultResponse getReportAdapter(@RequestParam String rtId) throws Exception {
		try {
			return new ResultResponse(reportAdapterService.getReportAdapterList(rtId));
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	/**
	 * Get Report Adapter Detail
	 * @param id
	 * @return
	 */
	@GetMapping("/reportAdapterDetail/{id}")
	public ResultResponse getReportAdapterDetail(@PathVariable String id) throws Exception {
		try {
			return new ResultResponse(reportAdapterService.getReportAdapterDetail(id));
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
		
//		Map<String, Object> result = reportAdapterService.getReportAdapterDetail(id);
//		if (result == null) {
//			return new ResultResponse(false, "DATA가 없습니다.");
//		} else {
//			return new ResultResponse(result);
//		}
	}
}
