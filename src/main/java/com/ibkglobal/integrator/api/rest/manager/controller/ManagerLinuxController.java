package com.ibkglobal.integrator.api.rest.manager.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibkglobal.integrator.api.rest.manager.service.ManagerLinuxService;
import com.ibkglobal.integrator.exception.model.ResultResponse;

@RestController
@RequestMapping("/integrator/1.0/linux")
public class ManagerLinuxController {

	@Autowired
	ManagerLinuxService managerLinuxService;
	
	@GetMapping("/execute/{order}")
	public ResultResponse execute(@PathVariable String order) throws Exception {
		return new ResultResponse(managerLinuxService.execute(order));
	}
}
