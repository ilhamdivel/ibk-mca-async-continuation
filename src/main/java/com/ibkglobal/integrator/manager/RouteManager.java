package com.ibkglobal.integrator.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.springframework.util.StringUtils;

import com.ibk.ibkglobal.data.integrator.route.type.InstanceRouteType;
import com.ibkglobal.config.RouteProperties;
import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.exception.SystemException;
import com.ibkglobal.integrator.manager.instance.InstanceAdmin;
import com.ibkglobal.integrator.manager.model.RouteInfo;
import com.ibkglobal.integrator.manager.model.RouteStat;
import com.ibkglobal.integrator.util.ConsumerUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;

import lombok.Getter;
import lombok.Setter;

public class RouteManager {

	@Getter
	CamelConfig camelConfig;

	RouteProperties routeProperties;

	@Getter
	@Setter
	private InstanceAdmin instanceAdmin;

	public RouteManager(CamelConfig camelConfig, RouteProperties routeProperties, InstanceAdmin instanceAdmin) {
		this.camelConfig = camelConfig;
		this.routeProperties = routeProperties;
		this.instanceAdmin = instanceAdmin;
	}

	public void init() throws Exception {
		LogManager.getLogger(LogType.SYSTEM).info("□■□■□■□■□■ IBK Route Init Start □■□■□■□■□■");
		
		// 업무별 라우터 정보 초기화
		instanceAdmin.init(routeProperties);
		// 카멜 정보 초기화
		camelConfig.init(routeProperties);
		// 라우터 정보 CamelContext 등록
		initBuilder();
		
		LogManager.getLogger(LogType.SYSTEM).info("□■□■□■□■□■ IBK Route Init End □■□■□■□■□■");
	}

	public void initBuilder() throws Exception {
		for (RouteInfo routeInfo : instanceAdmin.getAllRouteInfo()) {
			try {				
				LogManager.getLogger(LogType.SYSTEM).info("□■ RouteInfo Add : " + routeInfo.getRouteId() + " □■");
				LogManager.getLogger(LogType.SYSTEM).info(routeInfo.toString());
				
				if (routeInfo.getRouteCreate().get() instanceof Collection) {
					camelConfig.getCamelContext().addRouteDefinitions(routeInfo.getRouteCreate().get());
				} else {
					camelConfig.getCamelContext().addRouteDefinition(routeInfo.getRouteCreate().get());
				}
				
				LogManager.getLogger(LogType.SYSTEM).info("□■ RouteInfo Add Success : " + routeInfo.getRouteId() + " □■");
			} catch (Exception e) {
				throw new SystemException("○● RouteInfo Init Error : " + e + "●○");
			}
		}
	}

	public void start(String routeId) throws Exception {
		try {
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route Start : " + routeId + " □■");
			camelConfig.getCamelContext().startRoute(routeId);
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route Start Success : " + routeId + " □■");
		} catch (Exception e) {
			throw new SystemException("○● Route Start Error : " + e + "●○");
		}
	}

	public void stop(String routeId) throws Exception {
		try {
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route Stop : " + routeId + " □■");
			camelConfig.getCamelContext().stopRoute(routeId);
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route Stop Success : " + routeId + " □■");
		} catch (Exception e) {
			throw new SystemException("○● Route Stop Error : " + e + "●○");
		}
	}

	public void remove(String routeId) throws Exception {
		try {
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route Remove : " + routeId + " □■");
			camelConfig.getCamelContext().stopRoute(routeId);			
			camelConfig.getCamelContext().removeRoute(routeId);
			
			instanceAdmin.deleteRouteInfo(routeId);
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route Remove Success : " + routeId + " □■");
		} catch (Exception e) {
			throw new SystemException("○● Route Remove Error : " + e + "●○");
		}
	}
	
	public void remove(RouteInfo routeInfo) throws Exception {
		try {
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route Remove : " + routeInfo.getRouteId() + " □■");
			
			if (routeInfo.getRouteCreate().get() instanceof Collection) {
				List<RouteDefinition> rdL = routeInfo.getRouteCreate().get();
				
				// Camel 라우트 서비스 순차적으로 실행됨(Pool X), Pool이 의미가 없음
				for (RouteDefinition rd : rdL) {
					camelConfig.getCamelContext().stopRoute(rd.getId());			
					camelConfig.getCamelContext().removeRoute(rd.getId());
				}
			} else {
				camelConfig.getCamelContext().stopRoute(routeInfo.getRouteId());			
				camelConfig.getCamelContext().removeRoute(routeInfo.getRouteId());
			}			
			
			instanceAdmin.deleteRouteInfo(routeInfo.getRouteId());
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route Remove Success : " + routeInfo.getRouteId() + " □■");
		} catch (Exception e) {
			throw new SystemException("○● Route Remove Error : " + e + "●○");
		}
	}

	public void allStart() throws Exception {
		try {
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route AllStart □■");
			camelConfig.getCamelContext().start();
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route AllStart Success □■");
		} catch (Exception e) {
			throw new SystemException("○● Route AllStart Error : " + e + "●○");
		}
	}

	public void allStop() throws Exception {
		try {
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route AllStop □■");
			camelConfig.getCamelContext().stop();
			LogManager.getLogger(LogType.SYSTEM).info("□■ Route AllStop Success □■");
		} catch (Exception e) {
			throw new SystemException("○● Route AllStop Error : " + e + "●○");
		}
	}

	public void addBuilder(RouteInfo routeInfo) throws Exception {
		try {
			LogManager.getLogger(LogType.SYSTEM).info("□■ RouteInfo Add : " + routeInfo.getRouteId() + " □■");
			LogManager.getLogger(LogType.SYSTEM).info(routeInfo.toString());
			
			if (routeInfo.getRouteCreate().get() instanceof Collection) {
				camelConfig.getCamelContext().addRouteDefinitions(routeInfo.getRouteCreate().get());
			} else {
				camelConfig.getCamelContext().addRouteDefinition(routeInfo.getRouteCreate().get());
			}
			
			instanceAdmin.addRouteInfo(routeInfo);
			
			LogManager.getLogger(LogType.SYSTEM).info("□■ RouteInfo Add Success : " + routeInfo.getRouteId() + " □■");
		} catch (Exception e) {
			throw new SystemException("○● RouteInfo Add Error : " + e + "●○");
		}
	}
	
	public void updateBuilder(RouteInfo routeInfo) throws Exception {		
		try {
			RouteInfo beforeRouteInfo = instanceAdmin.getRouteInfo(routeInfo.getRouteId());
			
			LogManager.getLogger(LogType.SYSTEM).info("□■ RouteInfo Update : " + routeInfo.getRouteId() + " □■");
			LogManager.getLogger(LogType.SYSTEM).info(routeInfo.toString());
			
			// 종료
			if (beforeRouteInfo.getRouteCreate().get() instanceof Collection) {
				camelConfig.getCamelContext().removeRouteDefinitions(beforeRouteInfo.getRouteCreate().get());
			} else {
				camelConfig.getCamelContext().removeRouteDefinition(beforeRouteInfo.getRouteCreate().get());
			}
			
			// 기동		
			if (routeInfo.getRouteCreate().get() instanceof Collection) {
				camelConfig.getCamelContext().addRouteDefinitions(routeInfo.getRouteCreate().get());
			} else {
				camelConfig.getCamelContext().addRouteDefinition(routeInfo.getRouteCreate().get());
			}
			
			instanceAdmin.updateRouteInfo(routeInfo);
			
			LogManager.getLogger(LogType.SYSTEM).info("□■ RouteInfo Update Success : " + routeInfo.getRouteId() + " □■");
		} catch (Exception e) {
			throw new SystemException("○● RouteInfo Update Error : " + e + "●○");
		}
	}

	public void removeBuilder(RouteInfo routeInfo) throws Exception {
		try {
			LogManager.getLogger(LogType.SYSTEM).info("□■ RouteInfo Remove : " + routeInfo.getRouteId() + " □■");
			LogManager.getLogger(LogType.SYSTEM).info(routeInfo.toString());
			
			if (routeInfo.getRouteCreate().get() instanceof Collection) {
				camelConfig.getCamelContext().removeRouteDefinitions(routeInfo.getRouteCreate().get());
			} else {
				camelConfig.getCamelContext().removeRouteDefinition(routeInfo.getRouteCreate().get());
			}
			
			instanceAdmin.deleteRouteInfo(routeInfo);
			LogManager.getLogger(LogType.SYSTEM).info("□■ RouteInfo Remove Success : " + routeInfo.getRouteId() + " □■");
		} catch (Exception e) {
			throw new SystemException("○● RouteInfo Remove Error : " + e + "●○");
		}
	}

	/**
	 * Get RouteInfo
	 * 
	 * @param String
	 *            routeId
	 * @return RouteInfo
	 */
	public RouteInfo getRouteInfo(String routeId) {
		return instanceAdmin.getRouteInfo(routeId);
	}

	/**
	 * Get All RouteInfo
	 * 
	 * @return
	 */
	public List<RouteInfo> getAllRouteInfo() {
		return instanceAdmin.getAllRouteInfo();
	}

	public List<RouteInfo> getAllRouteInfo(InstanceRouteType instanceRouteType) {
		return instanceAdmin.getAllRouteInfo(instanceRouteType);
	}

	public List<RouteStat> routeStatusList() {
		List<RouteStat> infos = new ArrayList<>();

		List<RouteDefinition> rds = camelConfig.getCamelContext().getRouteDefinitions();

		rds.forEach(rd -> {

			RouteStat info = new RouteStat();

			info.setRouteId(rd.getId());
			info.setGroup(rd.getGroup());
			info.setDescription(rd.getDescriptionText());

			StringBuffer input = new StringBuffer();
			rd.getInputs().forEach(fd -> {
				if (!StringUtils.isEmpty(input.toString())) {
					input.append(",");
				}
				input.append(fd.getUri());
			});
			info.setFrom(input.toString());

			info.setStarted(rd.getStatus(camelConfig.getCamelContext()).isStarted());
			info.setStopped(rd.getStatus(camelConfig.getCamelContext()).isStopped());

			String[] outputs = new String[rd.getOutputs().size()];
			int i = 0;
			for (ProcessorDefinition<?> pd : rd.getOutputs()) {
				outputs[i++] = pd.toString();
			}
			info.setOutputs(outputs);

			Route route = camelConfig.getCamelContext().getRoute(rd.getId());
			if (route != null) {
				ConsumerUtil.consumerInfoSet(info, route.getConsumer());
			}

			infos.add(info);
		});

		return infos;
	}
}
