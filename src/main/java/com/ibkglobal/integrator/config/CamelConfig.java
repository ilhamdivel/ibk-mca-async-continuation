package com.ibkglobal.integrator.config;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.interceptor.DefaultTraceFormatter;
import org.apache.camel.processor.interceptor.TraceFormatter;
import org.apache.camel.processor.interceptor.TraceInterceptor;
import org.apache.camel.processor.interceptor.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.ibkglobal.config.RouteProperties;

import lombok.Getter;

@Configuration
@SuppressWarnings("deprecation")
public class CamelConfig {

  @Getter
  @Autowired
  CamelContext camelContext;

  @Getter
  @Autowired
  ProducerTemplate producerTemplate;

  @Getter
  @Autowired
  ConsumerTemplate consumerTemplate;

  @Autowired
  ActiveMQConnectionFactory factory;

  @Autowired
  JmsComponent jmsComponent;

  // Context 설정 변경 시
  public void init(RouteProperties routeProperties) {
    jmsComponent.setConnectionFactory(factory);

    // initTracer();
  }

  /**
   * Tracer 설정
   */
  public void initTracer() {
    try {
      camelContext.setTracing(true);

      DefaultTraceFormatter formatter = new DefaultTraceFormatter();
      formatter.setShowBreadCrumb(false);
      formatter.setShowHeaders(false);

      Tracer tracer = new Tracer();
      tracer.setLogLevel(LoggingLevel.INFO);
      tracer.setLogName("dynamicTracerLog");
      // tracer.setFormatter(formatter);
      tracer.setFormatter(new TraceFormatter() {
        @Override
        public Object format(TraceInterceptor interceptor, ProcessorDefinition<?> node, Exchange exchange) {
          Message in = exchange.getIn();
          return formatter.format(interceptor, node, exchange);
        }
      });
      // camelContext.getProperties().put(Exchange.LOG_DEBUG_BODY_STREAMS, "true");
      camelContext.getProperties().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "10000");
      camelContext.addInterceptStrategy(tracer);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
