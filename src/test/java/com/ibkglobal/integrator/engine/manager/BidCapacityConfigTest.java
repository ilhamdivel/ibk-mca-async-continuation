package com.ibkglobal.integrator.engine.manager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collections;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;

import com.ibkglobal.integrator.engine.timer.IBKTimeout;

public class BidCapacityConfigTest {

    @Configuration
    static class Placeholders {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    private BidManager start(AnnotationConfigApplicationContext context, String capacity) {
        return start(context, capacity, null);
    }

    private BidManager start(AnnotationConfigApplicationContext context, String capacity, String threads) {
        java.util.Map<String, Object> properties = new java.util.HashMap<>();
        if (capacity != null) properties.put("integrator.config.bid-async-capacity", capacity);
        if (threads != null) properties.put("integrator.config.bid-completion-threads", threads);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        // registerSingleton: the mock is not post-processed, so its inherited @Autowired fields stay empty.
        context.getBeanFactory().registerSingleton("ibkTimeoutBid", mock(IBKTimeout.class));
        context.register(Placeholders.class, BidManager.class);
        context.refresh();
        return context.getBean(BidManager.class);
    }

    @Test
    public void defaultsTo512WhenPropertyIsAbsent() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            BidManager manager = start(context, null);
            assertEquals(512, manager.getAsyncCapacityLimit());
            assertEquals(8, manager.getCompletionThreads());
        } finally { context.close(); }
    }

    @Test
    public void readsIntegratorConfigBidCompletionThreads() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            BidManager manager = start(context, "64", "3");
            assertEquals(64, manager.getAsyncCapacityLimit());
            assertEquals(3, manager.getCompletionThreads());
        } finally { context.close(); }
    }

    @Test
    public void readsIntegratorConfigBidAsyncCapacity() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            assertEquals(64, start(context, "64").getAsyncCapacityLimit());
        } finally { context.close(); }
    }
}
