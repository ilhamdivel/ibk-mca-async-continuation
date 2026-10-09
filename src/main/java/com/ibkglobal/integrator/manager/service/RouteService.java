package com.ibkglobal.integrator.manager.service;

import java.util.Collection;
import java.util.List;

import org.apache.camel.model.RouteDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.type.InstanceRouteType;
import com.ibkglobal.common.repository.CacheFactory;
import com.ibkglobal.common.repository.CacheType;
import com.ibkglobal.integrator.engine.builder.service.RouteCreateService;
import com.ibkglobal.integrator.manager.RouteManager;
import com.ibkglobal.integrator.manager.model.RouteInfo;
import com.ibkglobal.integrator.manager.model.RouteStat;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

@Service
public class RouteService {
	
	@Autowired
	RouteManager routeManager;
	
	@Autowired
	CacheFactory cacheFactory;
	
	@Autowired
	RouteCreateService routeCreateService;
	
	public List<RouteStat> routeStatusList() {
		return routeManager.routeStatusList();
	}
	
	/**
	 * 라우트 기동
	 * @param routeId
	 */
	public void start(String routeId) throws Exception {
		RouteInfo routeInfo = routeManager.getRouteInfo(routeId);
		
		if (routeInfo.getRouteCreate().get() instanceof Collection) {
			List<RouteDefinition> rdL = routeInfo.getRouteCreate().get();
			
			// Camel 라우트 서비스 순차적으로 실행됨(Pool X), Pool이 의미가 없음
			for (RouteDefinition rd : rdL) {
				routeManager.start(rd.getId());
			}
			
//			ExecutorService executorService = Executors.newFixedThreadPool(5);			
//			try {
//				List<Callable<Boolean>> callables = new ArrayList<>();
//				
//				for (RouteDefinition rd : rdL) {
//					callables.add(new Callable<Boolean>() {					
//						@Override
//						public Boolean call() throws Exception {
//							try {
//								routeManager.start(rd.getId());								
//								return true;
//							} catch (Exception e) {
//								return false;
//							}
//						}
//					});
//				}				
//				List<Future<Boolean>> futures = executorService.invokeAll(callables);
//				
//				for (Future<Boolean> future : futures) {
//					System.out.println("결과 값 : " + future.get());
//				}
//			} catch (Exception e) {
//				e.printStackTrace();
//			} finally {
//				executorService.shutdown();
//			}			
		} else {
			routeManager.start(routeId);
		}
	}
	
	/**
	 * 라우트 중지
	 * @param routeId
	 */
	public void stop(String routeId) throws Exception {
		RouteInfo routeInfo = routeManager.getRouteInfo(routeId);
		
		if (routeInfo.getRouteCreate().get() instanceof Collection) {
			List<RouteDefinition> rdL = routeInfo.getRouteCreate().get();
			
			// Camel 라우트 서비스 순차적으로 종료됨(Pool X), Pool이 의미가 없음
			for (RouteDefinition rd : rdL) {
				routeManager.stop(rd.getId());
			}
			
//			ExecutorService executorService = Executors.newFixedThreadPool(5);			
//			try {
//				List<Callable<Boolean>> callables = new ArrayList<>();
//				
//				for (RouteDefinition rd : rdL) {
//					callables.add(new Callable<Boolean>() {					
//						@Override
//						public Boolean call() throws Exception {
//							try {
//								routeManager.stop(rd.getId());			
//								return true;
//							} catch (Exception e) {
//								return false;
//							}
//						}
//					});
//				}				
//				List<Future<Boolean>> futures = executorService.invokeAll(callables);
//				
//				for (Future<Boolean> future : futures) {
//					System.out.println("결과 값 : " + future.get());
//				}
//			} catch (Exception e) {
//				e.printStackTrace();
//			} finally {
//				executorService.shutdown();
//			}			
		} else {
			routeManager.stop(routeId);
		}
	}
	
	/**
	 * 라우트 삭제
	 * @param routeId
	 */
	public void remove(String routeId) throws Exception {
		RouteInfo routeInfo = routeManager.getRouteInfo(routeId);
		
		routeManager.remove(routeInfo);
	}
	
	/**
	 * 전체 기동
	 */
	public void allStart() throws Exception {
		routeManager.allStart();
	}
	
	/**
	 * 전체 중지
	 */
	public void allStop() throws Exception {
		routeManager.allStop();
	}
	
	/**
	 * 라우트 추가
	 * @param routeInfo
	 */
	public void addBuilder(RouteInfo routeInfo) throws Exception {	
		routeManager.addBuilder(routeInfo);
	}
	
	/**
	 * 캐시에 등록한 라우트 추가
	 * @param info
	 */
	public void applyBuilder(String itncCd, String routId) throws Exception {
		Cache cache = null;
		
		switch (itncCd) {
		case "MCA" :
			cache = cacheFactory.getCacheRepository(CacheType.ROUTEMCA);
			break;
		case "EAI" :
			cache = cacheFactory.getCacheRepository(CacheType.ROUTEEAI);
			break;
		case "FEP" :
			cache = cacheFactory.getCacheRepository(CacheType.ROUTEFEP);
			break;
		default :
			break;
		}
		
		Element element = cache.get(routId);
		
		if (element != null) {
			RouteCreateInfo routeCreateInfo = (RouteCreateInfo) element.getObjectValue();
			
			routeManager.addBuilder(routeCreateService.createRouteInfo(routeCreateInfo));
		}
	}
	
	/**
	 * 라우트 정보변경
	 * @param routeInfo
	 */
	public void updateBuilder(RouteInfo routeInfo) throws Exception {	
		routeManager.updateBuilder(routeInfo);
	}
	
	/**
	 * 라우트 삭제
	 * @param routeInfo
	 */
	public void removeBuilder(RouteInfo routeInfo) throws Exception {
		routeManager.removeBuilder(routeInfo);
	}
	
	/**
	 * 로그 레벨 변경
	 * @param routeId
	 * @param logLevel
	 */
	public void logLevelSet(String routeId, String logLevel) {
		RouteInfo routeInfo = getRouteInfo(routeId);
		
		routeInfo.getRouteCreate().getRouteCreateInfo().setLogLevel(logLevel);
	}
	
	/**
	 * Get RouteInfo
	 * @param routeId
	 * @return
	 */
	public RouteInfo getRouteInfo(String routeId) {
		return routeManager.getRouteInfo(routeId);
	}
	
	/**
	 * Get RouteInfoAll
	 * @return
	 */
	public List<RouteInfo> getAllRouteInfo() {
		return routeManager.getAllRouteInfo();
	}
	
	/**
	 * Get RouteInfoAll(Type)
	 * @return
	 */
	public List<RouteInfo> getAllRouteInfo(InstanceRouteType instanceRouteType) {
		return routeManager.getAllRouteInfo(instanceRouteType);
	}
}
