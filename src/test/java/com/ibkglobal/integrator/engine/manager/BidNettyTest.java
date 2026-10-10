package com.ibkglobal.integrator.engine.manager;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.builder.RouteBuilder;
import com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterAsync;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;

public class BidNettyTest {
    @Test
    public void realHttpResponseWaitsWithoutBlockingContinuation() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        StandardTelegram telegram = mock(StandardTelegram.class, RETURNS_DEEP_STUBS);
        when(telegram.getSttlSysCopt().getOtptTmgtDcd()).thenReturn("4");
        when(telegram.getSttlSysCopt().getWhbnSttlWrtnYmd()).thenReturn("20261009");
        when(telegram.getSttlSysCopt().getWhbnSttlCretSysNm()).thenReturn("SYSTEM");
        when(telegram.getSttlSysCopt().getWhbnSttlSrn()).thenReturn("SRN");
        IBKMessage message = mock(IBKMessage.class);
        when(message.getStandardTelegram()).thenReturn(telegram);
        DefaultCamelContext context = new DefaultCamelContext();
        MCAWorkAfterAsync processor = new MCAWorkAfterAsync(manager);
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("netty4-http:http://127.0.0.1:" + port + "/proof")
                    .process(exchange -> {
                        exchange.getIn().setBody(message);
                        // as BidSafeHttpClientChannelHandler marks the GCB reply
                        exchange.setProperty(BidManager.BID_SAFE_REPLY, Boolean.TRUE);
                    })
                    .process(processor)
                    .process(exchange -> exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain"));
            }
        });
        ExecutorService client = Executors.newSingleThreadExecutor();
        context.start();
        try {
            Future<String> response = client.submit(() -> {
                HttpURLConnection connection = (HttpURLConnection) new URL(
                    "http://127.0.0.1:" + port + "/proof").openConnection();
                connection.setReadTimeout(5000);
                try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    return input.readLine();
                } finally { connection.disconnect(); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (manager.getBidInfoList().isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertFalse(manager.getBidInfoList().isEmpty());
            assertFalse(response.isDone());
            String key = manager.getBidInfoList().keySet().iterator().next();
            Exchange release = new DefaultExchange(context);
            release.getIn().setBody("final-response");
            manager.bidResult(key, release);
            assertEquals("final-response", response.get(5, TimeUnit.SECONDS));
            assertTrue(manager.getBidInfoList().isEmpty());
        } finally {
            manager.shutdownAsync();
            context.stop();
            client.shutdownNow();
        }
    }
}
