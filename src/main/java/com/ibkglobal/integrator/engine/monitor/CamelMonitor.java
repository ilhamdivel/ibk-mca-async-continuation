package com.ibkglobal.integrator.engine.monitor;

import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.engine.model.RouteStatus;

@Component
public class CamelMonitor {
	
	@Autowired
	CamelConfig camelConfig;

	/*
	 * <routeStat id="M.GCB0.0000.TS20000" state="Started" exchangesInflight="0" selfProcessingTime="0" oldestInflightExchangeId="" oldestInflightDuration="" exchangesCompleted="0" exchangesFailed="0" failuresHandled="0" redeliveries="0" externalRedeliveries="0" minProcessingTime="0" maxProcessingTime="0" totalProcessingTime="0" lastProcessingTime="-1" deltaProcessingTime="0" meanProcessingTime="-1" startTimestamp="2018-08-24T14:49:10.413+0900" resetTimestamp="2018-08-24T14:49:10.413+0900" firstExchangeCompletedTimestamp="1970-01-01T08:59:59.999+0900" firstExchangeCompletedExchangeId="" firstExchangeFailureTimestamp="1970-01-01T08:59:59.999+0900" firstExchangeFailureExchangeId="" lastExchangeCompletedTimestamp="1970-01-01T08:59:59.999+0900" lastExchangeCompletedExchangeId="" lastExchangeFailureTimestamp="1970-01-01T08:59:59.999+0900" lastExchangeFailureExchangeId="">
		  <processorStats>
		    <processorStat id="bean149" index="291" state="Started" accumulatedProcessingTime="0" exchangesCompleted="0" exchangesFailed="0" failuresHandled="0" redeliveries="0" externalRedeliveries="0" minProcessingTime="0" maxProcessingTime="0" totalProcessingTime="0" lastProcessingTime="-1" deltaProcessingTime="0" meanProcessingTime="-1" startTimestamp="2018-08-24T14:49:10.413+0900" resetTimestamp="2018-08-24T14:49:10.413+0900" firstExchangeCompletedTimestamp="1970-01-01T08:59:59.999+0900" firstExchangeCompletedExchangeId="" firstExchangeFailureTimestamp="1970-01-01T08:59:59.999+0900" firstExchangeFailureExchangeId="" lastExchangeCompletedTimestamp="1970-01-01T08:59:59.999+0900" lastExchangeCompletedExchangeId="" lastExchangeFailureTimestamp="1970-01-01T08:59:59.999+0900" lastExchangeFailureExchangeId=""/>
		  </processorStats>
		</routeStat>
	 */	
	public RouteStatus getRouteStatus(String routeId) {
		ManagedRouteMBean routeMBean = camelConfig.getCamelContext().getManagedRoute(routeId, ManagedRouteMBean.class);
		
		try {
			String xml = routeMBean.dumpRouteStatsAsXml(true, true);
			if (!StringUtils.isEmpty(xml)) {
				JAXBContext jaxbContext = JAXBContext.newInstance(RouteStatus.class);
				Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
				return (RouteStatus) jaxbUnmarshaller.unmarshal(new StringReader(xml));
			}
		} catch (Exception ex) {
			//throw new ApiException(ex.getMessage());
		}
		
		return null;
	}
}
