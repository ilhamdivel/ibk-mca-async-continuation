package com.ibkglobal.integrator.api.rest.reportmanager.service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import com.ibkglobal.integrator.api.rest.SystemUtil;
import com.ibkglobal.integrator.api.rest.manager.model.ManagerModel.systemStatus;
import com.ibkglobal.integrator.api.rest.reportmanager.model.ReportSystem;
import com.ibkglobal.integrator.exception.ApiException;

@Service
public class ReportSystemService {
	
	OperatingSystemMXBean mxBean = ManagementFactory.getOperatingSystemMXBean();
	
	public ReportSystem getReportSystem() throws Exception {
		return SystemUtil.getReportSystem();
	}
	
	public systemStatus getUsageInfo() throws Exception {
		systemStatus retVal = new systemStatus();
		
		retVal.setCpuUsage(String.valueOf(this.getCpuUsage()));
		retVal.setDiskUsage(String.valueOf(this.getDiskUsage()));
		retVal.setMemoryUsage(String.valueOf(this.getMemoryUsage()));
		
		return retVal;
	}
	
	public float getCpuUsage() throws Exception {
		try {
			Method methodCpuUsage = mxBean.getClass().getDeclaredMethod("getSystemCpuLoad");
			
			//methodCpuUsage.setAccessible(true);
			ReflectionUtils.makeAccessible(methodCpuUsage);
			
			float cpuUsagePercent = Float.parseFloat(String.valueOf(methodCpuUsage.invoke(mxBean))) * 100;
			
			return cpuUsagePercent;
		} catch (Exception ex) {
			System.out.println("get cpu usage Error: " + ex.getMessage());
			throw new ApiException("get cpu usage Error: " + ex.getMessage());
		}
	}

	public float getMemoryUsage() throws Exception {
		try {
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
		} catch (Exception ex) {
			System.out.println("get memory usage Error: " + ex.getMessage());
			throw new ApiException("get memory usage Error: " + ex.getMessage());
		}
	}
	
	public float getDiskUsage() throws Exception {
		File temp = null;
		
		try {
			temp = new File("/");
			
			long diskTotal = temp.getTotalSpace();
			long diskFree = temp.getFreeSpace();
			long diskUsage = diskTotal - diskFree;
			float diskUsagePercent = ((float) diskUsage / (float) diskTotal) * 100;
			
			return diskUsagePercent;
		} catch (Exception ex) {
			throw new ApiException("get disk usage Error: " + ex.getMessage());
		}
	}
}
