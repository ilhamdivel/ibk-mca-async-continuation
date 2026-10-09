package com.ibkglobal.integrator.api.rest.manager.service;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.client.RestTemplate;

import com.ibkglobal.integrator.api.rest.manager.model.ManagerModel.systemStatus;
import com.ibkglobal.integrator.exception.model.ResultResponse;

@Service
public class ManagerService {
	
	OperatingSystemMXBean mxBean = ManagementFactory.getOperatingSystemMXBean();
	
	public systemStatus getUsageInfo() throws Exception {
		systemStatus retVal = new systemStatus();
		
		retVal.setCpuUsage(String.valueOf(this.getCpuUsage()));
		retVal.setDiskUsage(String.valueOf(this.getDiskUsage()));
		retVal.setMemoryUsage(String.valueOf(this.getMemoryUsage()));
		
		return retVal;
	}
	
	public float getCpuUsage() throws Exception {
		Method methodCpuUsage = mxBean.getClass().getDeclaredMethod("getSystemCpuLoad");
		
		//methodCpuUsage.setAccessible(true);
		ReflectionUtils.makeAccessible(methodCpuUsage);
		
		float cpuUsagePercent = Float.parseFloat(String.valueOf(methodCpuUsage.invoke(mxBean))) * 100;
		
		return cpuUsagePercent;
	}

	public float getMemoryUsage() throws Exception {
		Method methodMemFree = mxBean.getClass().getDeclaredMethod("getFreePhysicalMemorySize");
		Method methodMemTotal = mxBean.getClass().getDeclaredMethod("getTotalPhysicalMemorySize");
		
		//methodMemFree.setAccessible(true);
		//methodMemTotal.setAccessible(true);
		ReflectionUtils.makeAccessible(methodMemFree);
		ReflectionUtils.makeAccessible(methodMemTotal);
		
		float memFree = Float.parseFloat(String.valueOf(methodMemFree.invoke(mxBean)));
		float memTotel = Float.parseFloat(String.valueOf(methodMemTotal.invoke(mxBean)));
		float memUsage = memTotel - memFree;
		
		float memUsagePercent = (memUsage / memTotel) * 100;
		
		return memUsagePercent;
	}
	
	@SuppressWarnings("unchecked")
	public float getDiskUsage() throws Exception {
		Map<String, Object> healthTemp = new HashMap<>();
		
		String healthUrl = "http://10.104.162.93:80/health"; 
		
		RestTemplate healthRest = new RestTemplate();
		healthTemp = healthRest.getForObject(new URI(healthUrl), Map.class);
		
		Map<String, String> diskStats = (Map<String, String>) healthTemp.get("diskSpace");
		long diskTotal = Long.parseLong(String.valueOf(diskStats.get("total")));
		long diskFree = Long.parseLong(String.valueOf(diskStats.get("free")));
		long diskUsage = diskTotal - diskFree;
		float diskUsagePercent = ((float) diskUsage / (float) diskTotal) * 100;
		
		return diskUsagePercent;
	}
	
	public ResultResponse dateTest() throws Exception {
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		
		dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
		
		dateFormat.format(new Date());
		
		return new ResultResponse("");
	}
}
