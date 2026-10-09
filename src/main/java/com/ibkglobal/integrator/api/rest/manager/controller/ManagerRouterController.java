package com.ibkglobal.integrator.api.rest.manager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibkglobal.integrator.api.rest.manager.service.ManagerRouterService;
import com.ibkglobal.integrator.exception.ApiException;
import com.ibkglobal.integrator.exception.model.ResultResponse;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;

@RestController
@RequestMapping("/integrator/1.0/routermanager")
public class ManagerRouterController {
	
	@Autowired
	ManagerRouterService managerRouterService;
	
	@PostMapping("/addRouter")
	public ResultResponse addRouter(@RequestBody String data) throws Exception {
		try {
			managerRouterService.addRouter(data);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/applyRouter/{itncCd}/{routId}")
	public ResultResponse applyRouter(@PathVariable String itncCd, @PathVariable String routId) throws Exception {
		try {
			LogManager.getLogger(LogType.ROOT).info("applyRouter itncCd: " + itncCd + ", routeId: " + routId);
			managerRouterService.applyRouter(itncCd, routId);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@PostMapping("/updateRouter")
	public ResultResponse updateRouter(@RequestBody String data) throws Exception {
		try {
			managerRouterService.updateRouter(data);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/removeRouter/{routeId}")
	public ResultResponse removeRouter(@PathVariable String routeId) throws Exception {
		try {
			managerRouterService.removeRouter(routeId);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/startRouter/{routeId}")
	public ResultResponse startRouter(@PathVariable String routeId) throws Exception {
		try {
			managerRouterService.startRouter(routeId);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
	@GetMapping("/stopRouter/{routeId}")
	public ResultResponse stopRouter(@PathVariable String routeId) throws Exception {
		try {
			managerRouterService.stopRouter(routeId);
			return new ResultResponse(true);
		} catch (Exception ex) {
			throw new ApiException(ex.getMessage());
		}
	}
	
}