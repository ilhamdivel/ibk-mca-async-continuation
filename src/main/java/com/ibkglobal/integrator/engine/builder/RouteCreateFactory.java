package com.ibkglobal.integrator.engine.builder;

import org.springframework.stereotype.Component;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.type.RouteType;
import com.ibkglobal.integrator.engine.builder.route.RouteCreate;
import com.ibkglobal.integrator.engine.builder.route.mca.MCADefaultAdapterIn;
import com.ibkglobal.integrator.engine.builder.route.mca.MCADefaultAdapterOut;
import com.ibkglobal.integrator.engine.builder.route.mca.MCAHealthCheck;
import com.ibkglobal.integrator.engine.builder.route.mca.MCAInbound;
import com.ibkglobal.integrator.engine.builder.route.mca.MCALocalAdapterOut;
import com.ibkglobal.integrator.engine.builder.route.mca.bid.MCABidAdapter;
import com.ibkglobal.integrator.engine.builder.route.mca.bid.MCABidProcess;

@Component
public class RouteCreateFactory {

  public RouteCreate getCreate(RouteCreateInfo builderInfo) {

    // MCA
    if (builderInfo.getRouteType() == RouteType.MCA_DEFAULT_ADAPTER_IN) {
      return new MCADefaultAdapterIn(builderInfo);
    } else if (builderInfo.getRouteType() == RouteType.MCA_DEFAULT_ADAPTER_OUT) {
      return new MCADefaultAdapterOut(builderInfo);
    } else if (builderInfo.getRouteType() == RouteType.MCA_INBOUND) {
      return new MCAInbound(builderInfo);
    } else if (builderInfo.getRouteType() == RouteType.MCA_BID_PROCESS) {
      return new MCABidProcess(builderInfo);
    } else if (builderInfo.getRouteType() == RouteType.MCA_HEALTH_CHECK) {
      return new MCAHealthCheck(builderInfo);
    } else if (builderInfo.getRouteType() == RouteType.MCA_BID_ADAPTER) {
      return new MCABidAdapter(builderInfo);
    } else if (builderInfo.getRouteType() == RouteType.MCA_LOCAL_ADAPTER_OUT) {
      return new MCALocalAdapterOut(builderInfo);
    }

    // CUSTOM
    else if (builderInfo.getRouteType() == RouteType.CUSTOM) {
      try {
        RouteCreate routeCreate = (RouteCreate) Class.forName(builderInfo.getClassName()).newInstance();
        routeCreate.setRouteCreateInfo(builderInfo);
        routeCreate.create();

        return routeCreate;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return null;
  }
}
