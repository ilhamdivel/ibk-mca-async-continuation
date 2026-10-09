package com.ibkglobal.integrator.engine.manager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty4.http.NettyHttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.engine.bean.mca.work.MCAGcbComBean;
import com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterAsync;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;

/**
 * Pins down what the continuation does and does NOT free in the two Adapter-In wirings.
 *
 * Prod (2026-10-06 logs): M.GCB0.COM0.HS30710 -> BEAN MCAGcbComBean.execute -> ProducerTemplate.send(direct:...)
 *   -> the GCB-reply IO thread (NettyClientTCPWorker) is freed, but the Adapter-In executor thread
 *      (NettyEventExecutorGroup) stays parked in the synchronous ProducerTemplate bridge until release/timeout.
 * Archived JSON: HS30710 -> to(direct:M.GCB0.COM0.ROUTE) -> both are freed.
 *
 * When MCAGcbComBean becomes asynchronous (phase 2), flip prodBridgeStillParksAdapterInThread.
 */
public class BidTopologyTest {
    private static final String KEY = "20261010GIBSRN1";

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static IBKMessage message(String otptTmgtDcd) {
        StandardTelegram telegram = mock(StandardTelegram.class, RETURNS_DEEP_STUBS);
        when(telegram.getSttlSysCopt().getSttlIntfId()).thenReturn("GITO00008290");
        when(telegram.getSttlSysCopt().getOtptTmgtDcd()).thenReturn(otptTmgtDcd);
        when(telegram.getSttlSysCopt().getWhbnSttlWrtnYmd()).thenReturn("20261010");
        when(telegram.getSttlSysCopt().getWhbnSttlCretSysNm()).thenReturn("GIB");
        when(telegram.getSttlSysCopt().getWhbnSttlSrn()).thenReturn("SRN1");
        IBKMessage message = mock(IBKMessage.class);
        when(message.getStandardTelegram()).thenReturn(telegram);
        when(message.getInterfaceId()).thenReturn("GITO00008290");
        return message;
    }

    private static MCAGcbComBean gcbComBean(DefaultCamelContext context) throws Exception {
        CamelConfig config = new CamelConfig();
        java.lang.reflect.Field camelContext = CamelConfig.class.getDeclaredField("camelContext");
        camelContext.setAccessible(true);
        camelContext.set(config, context);
        MCAGcbComBean bean = new MCAGcbComBean();
        java.lang.reflect.Field camelConfig = MCAGcbComBean.class.getDeclaredField("camelConfig");
        camelConfig.setAccessible(true);
        camelConfig.set(bean, config);
        return bean;
    }

    private static String get(int port, String auditHeader) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/httpin").openConnection();
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Connection", "close");
        if (auditHeader != null) connection.setRequestProperty("X-Audit", auditHeader);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return input.readLine();
        } finally { connection.disconnect(); }
    }

    private static boolean anyThreadParkedIn(String threadNameFragment, String frameFragment) {
        for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(false, false)) {
            if (threadNameFragment != null && !info.getThreadName().contains(threadNameFragment)) continue;
            for (StackTraceElement frame : info.getStackTrace()) {
                if (frame.toString().contains(frameFragment)) return true;
            }
        }
        return false;
    }

    private static final class Scenario implements AutoCloseable {
        final BidManager manager = new BidManager();
        final DefaultCamelContext context = new DefaultCamelContext();
        final ExecutorService clients = Executors.newCachedThreadPool();
        final int inPort;
        Future<String> original;

        Scenario(boolean prodBridge, int adapterInPool) throws Exception {
            manager.ibkTimeoutBid = mock(IBKTimeout.class);
            inPort = freePort();
            int gcbPort = freePort();
            context.getComponent("netty4-http", NettyHttpComponent.class).setMaximumPoolSize(adapterInPool);
            context.addComponent("gcbhttp", new NettyHttpComponent()); // fake GCB gets its own executor group
            context.getShutdownStrategy().setTimeout(3);
            MCAGcbComBean comBean = gcbComBean(context);
            MCAWorkAfterAsync after = new MCAWorkAfterAsync(manager);
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    from("gcbhttp:http://127.0.0.1:" + gcbPort + "/service/sync").transform(constant("dummy-ack"));
                    if (prodBridge) {
                        from("netty4-http:http://127.0.0.1:" + inPort + "/httpin")
                            .process(e -> e.getIn().setBody(message(null)))
                            .process(comBean::execute)
                            .process(e -> e.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain"));
                    } else {
                        from("netty4-http:http://127.0.0.1:" + inPort + "/httpin")
                            .process(e -> e.getIn().setBody(message(null)))
                            .to("direct:M.GCB0.COM0.ROUTE")
                            .process(e -> e.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain"));
                    }
                    from("direct:M.GCB0.COM0.ROUTE")
                        .choice()
                          .when(header("X-Audit").isEqualTo("release"))
                            .process(e -> {   // ProcessPreMCA role for a real response (0/R/K)
                                Exchange release = e.copy();
                                release.getIn().setBody("final-response");
                                e.getIn().setBody("release:" + manager.bidResult(KEY, release));
                                e.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
                            })
                          .otherwise()
                            .removeHeaders("*")
                            .setBody(constant("request"))
                            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                            .to("netty4-http:http://127.0.0.1:" + gcbPort + "/service/sync")
                            .process(e -> e.getIn().setBody(message("4")))
                            .process(after)
                        .end();
                }
            });
            context.start();
        }

        void sendOriginalAndWaitForDummy() throws Exception {
            original = clients.submit(() -> get(inPort, null));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (manager.getAsyncPendingCount() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals("dummy ack must suspend one continuation", 1, manager.getAsyncPendingCount());
            Thread.sleep(200);
        }

        @Override
        public void close() throws Exception {
            manager.shutdownAsync();
            context.stop();
            clients.shutdownNow();
        }
    }

    @Test
    public void prodBridgeFreesIoWorkerButStillParksAdapterInThread() throws Exception {
        try (Scenario scenario = new Scenario(true, 4)) {
            scenario.sendOriginalAndWaitForDummy();
            assertFalse("no thread may block in the legacy wait", anyThreadParkedIn(null, "BidManager.workWait"));
            assertFalse("GCB reply IO worker must be free",
                anyThreadParkedIn("NettyClientTCPWorker", "AsyncProcessorAwaitManager"));
            // Known limitation (phase 2): MCAGcbComBean's ProducerTemplate.send keeps the Adapter-In thread parked.
            assertTrue("Adapter-In executor is still parked in the synchronous ProducerTemplate bridge",
                anyThreadParkedIn("Camel (" + scenario.context.getName() + ")", "AsyncProcessorAwaitManager.await"));
            Exchange release = new DefaultExchange(scenario.context);
            release.getIn().setBody("final-response");
            assertEquals(BidManager.ReleaseResult.DELIVERED, scenario.manager.bidResult(KEY, release));
            assertEquals("final-response", scenario.original.get(10, TimeUnit.SECONDS));
            assertEquals(0, scenario.manager.getAsyncPendingCount());
        }
    }

    @Test
    public void directAdapterInFreesTheOnlyExecutorSoReleaseIsServed() throws Exception {
        try (Scenario scenario = new Scenario(false, 1)) {
            scenario.sendOriginalAndWaitForDummy();
            assertFalse(anyThreadParkedIn("Camel (" + scenario.context.getName() + ")", "AsyncProcessorAwaitManager.await"));
            // One Adapter-In executor only: the release can be served because nothing is parked on it.
            assertEquals("release:DELIVERED", get(scenario.inPort, "release"));
            assertEquals("final-response", scenario.original.get(10, TimeUnit.SECONDS));
            assertEquals(0, scenario.manager.getAsyncPendingCount());
        }
    }
}
