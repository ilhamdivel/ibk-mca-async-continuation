package com.ibkglobal.integrator.engine.builder.model;

import java.util.List;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;

import lombok.Data;

@Data
public class RouteCreateLoader {
	private String                loaderName;
	private List<RouteCreateInfo> routeCreateInfoList;
}
