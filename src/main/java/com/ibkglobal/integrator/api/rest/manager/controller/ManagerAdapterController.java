package com.ibkglobal.integrator.api.rest.manager.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibkglobal.integrator.api.rest.manager.service.ManagerAdapterService;
import com.ibkglobal.integrator.exception.ApiException;
import com.ibkglobal.integrator.exception.model.ResultResponse;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;

@RestController
@RequestMapping("/integrator/1.0/adaptermanager")
public class ManagerAdapterController {
	
	@Autowired
	ManagerAdapterService managerAdapterService;
	
	@PostMapping("/addAdapter")
	public ResultResponse addAdapter(@RequestBody String data) throws Exception {
		try {
			managerAdapterService.addAdapter(data);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/applyAdapter/{itncCd}/{routId}")
	public ResultResponse applyAdapter(@PathVariable String itncCd, @PathVariable String routId) throws Exception {
		try {
			LogManager.getLogger(LogType.ROOT).info("applyAdapter itncCd: " + itncCd + ", routeId: " + routId);
			managerAdapterService.applyAdapter(itncCd, routId);
			System.out.println("applyAdapter return");
			return new ResultResponse(true);
		} catch (Exception ex) {
			System.out.println("applyAdapter Exception: " + ex.getStackTrace());
			throw new ApiException(ex.getMessage());
		}
	}
	
	@PostMapping("/updateAdapter")
	public ResultResponse updateAdapter(@RequestBody String data) throws Exception {
		try {
			managerAdapterService.updateAdapter(data);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/removeAdapter/{routeId}")
	public ResultResponse removeAdapter(@PathVariable String routeId) throws Exception {
		try {
			managerAdapterService.removeAdapter(routeId);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/startAdapter/{routeId}")
	public ResultResponse startAdapter(@PathVariable String routeId) throws Exception {
		try {
			managerAdapterService.startAdapter(routeId);
			System.out.println("startAdapter return");
			return new ResultResponse(true);
		} catch (Exception ex) {
			System.out.println("startAdapter Exception: " + ex.getStackTrace());
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/stopAdapter/{routeId}")
	public ResultResponse stopAdapter(@PathVariable String routeId) throws Exception {
		try {
			managerAdapterService.stopAdapter(routeId);
			System.out.println("stopAdapter return");
			return new ResultResponse(true);
		} catch (Exception ex) {
			System.out.println("stopAdapter Exception: " + ex.getStackTrace());
			throw new ApiException(ex.getMessage());
		}
	}
	
	@PostMapping("/setLogLevelAdapter")
	public ResultResponse setLogLevelAdapter(@RequestBody Map<String, String> data) throws Exception {
		try {
			managerAdapterService.setLogLevelAdapter(data);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
}
