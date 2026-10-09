package com.ibkglobal.integrator.api.rest.manager.service;

import org.springframework.stereotype.Service;

import com.ibkglobal.common.util.CommandUtil;

@Service
public class ManagerLinuxService {
	
	public String execute(String order) throws Exception  {		
		return CommandUtil.execute(CommandUtil.commandConvert(order));
	}
}
