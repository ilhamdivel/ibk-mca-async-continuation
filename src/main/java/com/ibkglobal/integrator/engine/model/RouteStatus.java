package com.ibkglobal.integrator.engine.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * Route Stats Model
 *
 */
@Data
@XmlRootElement(name="routeStat")
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties({"selfProcessingTime", "oldestInflightExchangeId", "oldestInflightDuration", 
	"deltaProcessingTime", "meanProcessingTime", "startTimestamp", "resetTimestamp", "firstExchangeCompletedTimestamp",
	"firstExchangeCompletedExchangeId", "firstExchangeFailureTimestamp", "firstExchangeFailureExchangeId",
	"lastExchangeCompletedTimestamp", "lastExchangeCompletedExchangeId", "lastExchangeFailureTimestamp",
	"lastExchangeFailureExchangeId", "processorStat"})
public class RouteStatus {

	@XmlAttribute
	private String id;
	
	@XmlAttribute
	private String state;
	
	@XmlAttribute
	private String exchangesInflight;
	
	@XmlAttribute
	private String selfProcessingTime;
	
	@XmlAttribute
	private String oldestInflightExchangeId;
	
	@XmlAttribute
	private String oldestInflightDuration;
	
	@XmlAttribute
	private String exchangesCompleted;
	
	@XmlAttribute
	private String exchangesFailed;
	
	@XmlAttribute
	private String failuresHandled;
	
	@XmlAttribute
	private String redeliveries;
	
	@XmlAttribute
	private String externalRedeliveries;
	
	@XmlAttribute
	private String minProcessingTime;
	
	@XmlAttribute
	private String maxProcessingTime;
	
	@XmlAttribute
	private String totalProcessingTime;
	
	@XmlAttribute
	private String lastProcessingTime;
	
	@XmlAttribute
	private String deltaProcessingTime;
	
	@XmlAttribute
	private String meanProcessingTime;
	
	@XmlAttribute
	private String startTimestamp;
	
	@XmlAttribute
	private String resetTimestamp;
	
	@XmlAttribute
	private String firstExchangeCompletedTimestamp;
	
	@XmlAttribute
	private String firstExchangeCompletedExchangeId;
	
	@XmlAttribute
	private String firstExchangeFailureTimestamp;
	
	@XmlAttribute
	private String firstExchangeFailureExchangeId;
	
	@XmlAttribute
	private String lastExchangeCompletedTimestamp;
	
	@XmlAttribute
	private String lastExchangeCompletedExchangeId;
	
	@XmlAttribute
	private String lastExchangeFailureTimestamp;
	
	@XmlAttribute
	private String lastExchangeFailureExchangeId;
	
	@XmlElementWrapper(name="processorStats")
	private List<ProcessorStat> processorStat;
	
	/**
	 * Processor Stat
	 *
	 */
	@Data	
	@XmlRootElement(name="processorStat")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class ProcessorStat {
		
		@XmlAttribute
		private String id;
		@XmlAttribute
		private String index;
		@XmlAttribute
		private String state;
		@XmlAttribute
		private String accumulatedProcessingTime;
		@XmlAttribute
		private String exchangesCompleted;
		@XmlAttribute
		private String exchangesFailed;
		@XmlAttribute
		private String failuresHandled;
		@XmlAttribute
		private String redeliveries;
		@XmlAttribute
		private String externalRedeliveries;
		@XmlAttribute
		private String minProcessingTime;
		@XmlAttribute
		private String maxProcessingTime;
		@XmlAttribute
		private String totalProcessingTime;
		@XmlAttribute
		private String lastProcessingTime;
		@XmlAttribute
		private String deltaProcessingTime;
		@XmlAttribute
		private String meanProcessingTime;
		@XmlAttribute
		private String startTimestamp;
		@XmlAttribute
		private String resetTimestamp;
		@XmlAttribute
		private String firstExchangeCompletedTimestamp;
		@XmlAttribute
		private String firstExchangeCompletedExchangeId;
		@XmlAttribute
		private String firstExchangeFailureTimestamp;
		@XmlAttribute
		private String firstExchangeFailureExchangeId;
		@XmlAttribute
		private String lastExchangeCompletedTimestamp;
		@XmlAttribute
		private String lastExchangeCompletedExchangeId;
		@XmlAttribute
		private String lastExchangeFailureTimestamp;
		@XmlAttribute
		private String lastExchangeFailureExchangeId;
	}
}
