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
        if (capacity != null) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Collections.<String, Object>singletonMap("integrator.config.bid-async-capacity", capacity)));
        }
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
            assertEquals(512, start(context, null).getAsyncCapacityLimit());
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
