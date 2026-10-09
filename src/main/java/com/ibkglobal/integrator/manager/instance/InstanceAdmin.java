package com.ibkglobal.integrator.manager.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.type.InstanceRouteType;
import com.ibk.ibkglobal.data.integrator.route.type.InstanceType;
import com.ibkglobal.common.convert.Converter;
import com.ibkglobal.common.file.FileReader;
import com.ibkglobal.common.file.FileType;
import com.ibkglobal.common.repository.CacheFactory;
import com.ibkglobal.common.repository.CacheType;
import com.ibkglobal.config.RouteProperties;
import com.ibkglobal.integrator.engine.builder.model.RouteCreateLoader;
import com.ibkglobal.integrator.engine.builder.service.RouteCreateService;
import com.ibkglobal.integrator.manager.model.RouteInfo;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;

import lombok.Getter;
import net.sf.ehcache.Cache;

@Component
public class InstanceAdmin {

  @Autowired
  RouteCreateService routeCreateService;

  @Autowired
  FileReader fileReader;

  @Autowired
  Converter converter;

  @Autowired
  CacheFactory cacheFactory;

  RouteProperties routeProperties;

  @Getter
  private Map<InstanceType, InstanceRoute> instanceRoutes;

  public void init(RouteProperties routeProperties) throws Exception {

    LogManager.getLogger(LogType.SYSTEM).info("□■ IBK Instance Init Start □■");
    LogManager.getLogger(LogType.SYSTEM)
        .info("□■ IBK Instance Load Type : " + routeProperties.getRouteBaseType() + " □■");
    LogManager.getLogger(LogType.SYSTEM)
        .info("□■ IBK Instance Instance Type : " + routeProperties.getInstanceType() + " □■");

    this.routeProperties = routeProperties;

    instanceRoutes = new HashMap<>();

    if (routeProperties.getInstanceType() == null)
      return;

    // Instance Info Init(MCA, FEP, EAI, ETC)
    routeProperties.getInstanceType().forEach(value -> {
      InstanceRoute instanceRoute = new InstanceRoute();
      instanceRoute.init();

      instanceRoutes.put(value, instanceRoute);
    });

    // Instance? Cache or File
    if (routeProperties.getRouteBaseType().equals("file")) {
      // Adapter and Router init
      RouteBaseInit();

      // Custom init
      RouteCustomInit();
    } else {
      // Route init
      RouteBaseInitCache();
    }

    LogManager.getLogger(LogType.SYSTEM).info("□■ IBK Instance Init End □■");
  }

  /**
   * 등록 된 어댑터/라우터 정보 초기화, 캐시방식
   * 
   * @throws Exception
   */
  public void RouteBaseInitCache() throws Exception {

    for (InstanceType instanceType : routeProperties.getInstanceType()) {
      Cache cache = null;
      ObjectMapper mapper = new ObjectMapper();
      switch (instanceType) {
      case MCA:
        cache = cacheFactory.getCacheRepository(CacheType.ROUTEMCA);
        break;
      case EAI:
        cache = cacheFactory.getCacheRepository(CacheType.ROUTEEAI);
        break;
      case FEP:
        cache = cacheFactory.getCacheRepository(CacheType.ROUTEFEP);
        break;
      default:
        break;
      }

      if (cache != null && cache.getSize() > 0) {
        List<RouteCreateInfo> routeCreateInfos = cache.getAll(cache.getKeys()).values().stream()
            .map(p -> mapper.convertValue(p.getObjectValue(), RouteCreateInfo.class)).collect(Collectors.toList());

        for (RouteCreateInfo routeCreateInfo : routeCreateInfos) {
          if (routeCreateInfo.isAutoStart()) {
            RouteInfo routeInfo = routeCreateService.createRouteInfo(routeCreateInfo);
            addRouteInfo(routeInfo);
          }
        }
      }
    }
  }

  /**
   * 등록 된 어댑터/라우터 정보 초기화, 파일방식
   * 
   * @throws Exception
   */
  public void RouteBaseInit() throws Exception {
    fileReader.readFileToMap(FileType.ROUTE, RouteCreateInfo.class).values().forEach(data -> {
      RouteInfo routeInfo = routeCreateService.createRouteInfo((RouteCreateInfo) data);
      addRouteInfo(routeInfo);
    });
  }

  /**
   * 사용자가 정의한 라우터 정보 초기화
   */
  public void RouteCustomInit() throws Exception {
    if (routeProperties.getCustomLoader() == null)
      return;

    routeProperties.getCustomLoader().forEach((key, value) -> {
      if (routeProperties.getInstanceType().contains(key)) {
        value.forEach(path -> {
          RouteCreateLoader routeCreateInfoList = null;

          try {
            routeCreateInfoList = fileReader.readFileToObject(path, RouteCreateLoader.class);
          } catch (Exception e) {
            // Error Log 남겨야함
            e.printStackTrace();
          }

          if (routeCreateInfoList != null && routeCreateInfoList.getRouteCreateInfoList() != null) {
            routeCreateInfoList.getRouteCreateInfoList().forEach(data -> {
              RouteInfo routeInfo = routeCreateService.createRouteInfo(data);
              addRouteInfo(routeInfo);
            });
          }
        });
      }
    });
  }

  /**
   * 라우터 아이디로 정보 Get
   * 
   * @param routeId
   * @return
   */
  public synchronized RouteInfo getRouteInfo(String routeId) {
    return getAllRouteInfo().stream().filter(routeInfo -> routeInfo.getRouteId().equals(routeId)).findFirst().get();
  }

  /**
   * 라우터 정보 추가
   * 
   * @param routeInfo
   */
  public synchronized void addRouteInfo(RouteInfo routeInfo) {
    Map<String, RouteInfo> routeInfoList = getRouteInfoList(routeInfo);

    if (routeInfoList != null) {
      routeInfoList.put(routeInfo.getRouteId(), routeInfo);
    }
  }

  /**
   * 라우터 정보 수정
   * 
   * @param routeInfo
   */
  public synchronized void updateRouteInfo(RouteInfo routeInfo) {
    Map<String, RouteInfo> routeInfoList = getRouteInfoList(routeInfo);

    if (routeInfoList != null) {
      routeInfoList.put(routeInfo.getRouteId(), routeInfo);
    }
  }

  /**
   * 라우터 정보 삭제
   * 
   * @param routeId
   */
  public synchronized void deleteRouteInfo(String routeId) {
    deleteRouteInfo(getRouteInfo(routeId));
  }

  /**
   * 라우터 정보 삭제
   * 
   * @param routeInfo
   */
  public synchronized void deleteRouteInfo(RouteInfo routeInfo) {
    Map<String, RouteInfo> routeInfoList = getRouteInfoList(routeInfo);

    if (routeInfoList != null) {
      routeInfoList.remove(routeInfo.getRouteId());
    }
  }

  /**
   * 인스턴스에 해당하고 타입에 해당하는 맵정보 Get
   * 
   * @param routeInfo
   * @return
   */
  public synchronized Map<String, RouteInfo> getRouteInfoList(RouteInfo routeInfo) {
    InstanceRoute instanceRoute = instanceRoutes.get(routeInfo.getInstanceType());

    if (instanceRoute == null) {
      return null;
    }

    Map<String, RouteInfo> routeInfoList = null;

    if (routeInfo.getInstanceRouteType() != null) {
      switch (routeInfo.getInstanceRouteType()) {
      case ADAPTER:
        routeInfoList = instanceRoute.getAdapterList();
        break;
      case ROUTER:
        routeInfoList = instanceRoute.getRouteList();
        break;
      default:
        routeInfoList = instanceRoute.getEtcList();
        break;
      }
    } else {
      routeInfoList = instanceRoute.getEtcList();
    }

    return routeInfoList;
  }

  /**
   * 전체 라우터 정보 Get
   * 
   * @return
   */
  public synchronized List<RouteInfo> getAllRouteInfo() {
    List<RouteInfo> routeInfoAll = new ArrayList<>();

    instanceRoutes.values().forEach(value -> {
      routeInfoAll.addAll(value.getAdapterList().values());
      routeInfoAll.addAll(value.getRouteList().values());
      routeInfoAll.addAll(value.getEtcList().values());
    });

    return routeInfoAll;
  }

  /**
   * 전체 라우터 정보 Get(Type)
   * 
   * @return
   */
  public synchronized List<RouteInfo> getAllRouteInfo(InstanceRouteType instanceRouteType) {
    List<RouteInfo> routeInfoAll = new ArrayList<>();

    instanceRoutes.values().forEach(value -> {
      switch (instanceRouteType) {
      case ADAPTER:
        routeInfoAll.addAll(value.getAdapterList().values());
        break;
      case ROUTER:
        routeInfoAll.addAll(value.getRouteList().values());
        break;
      default:
        routeInfoAll.addAll(value.getEtcList().values());
        break;
      }
    });

    return routeInfoAll;
  }
}
