package com.ibkglobal.integrator.engine.manager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty4.http.NettyHttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.SimpleRegistry;
import org.junit.Test;

import com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterAsync;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.netty.factory.IBKHttpProducerInitializer;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.common.normal.copt.SttlSysCopt;

/**
 * Which dummy acks may be suspended.
 *
 * Camel 2.21.1's ClientChannelHandler calls the producer callback a second time when the channel
 * closes while the exchange is not done (always with disconnect=true). The GCB adapter-out
 * (ibkHttpProducerInitializer) guards suspended continuations against it
 * (BidSafeHttpClientChannelHandler) and marks its replies BID_SAFE_REPLY. A dummy ack from any other
 * producer - here the LOCAL adapter-out: Camel's default netty4-http client with the exact options of
 * MCAWorkPreProcess.localWork - keeps the office blocking wait.
 */
public class BidSafeReplyTest {
    private static final String KEY_PREFIX = "20261010SAFE";
    /** MCAWorkPreProcess.localWork (sysEnvrInfoDcd = L), default client initializer. */
    private static final String LOCAL_OPTIONS = "httpMethodRestrict=POST&disconnect=true&requestTimeout=300000";
    /** EndpointCreate.createHttp for the GCB adapter-out, prod options. */
    private static final String GCB_OPTIONS =
        "clientInitializerFactory=#ibkHttpProducerInitializer&disconnect=true&requestTimeout=90000";

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static IBKMessage dummyAck(String id) {
        SttlSysCopt copt = new SttlSysCopt();
        copt.setOtptTmgtDcd("4");
        copt.setWhbnSttlWrtnYmd("20261010");
        copt.setWhbnSttlCretSysNm("SAFE");
        copt.setWhbnSttlSrn(id);
        StandardTelegram telegram = new StandardTelegram();
        telegram.setSttlSysCopt(copt);
        IBKMessage message = new IBKMessage();
        message.setInterfaceId("DUMMY:" + id);
        message.setStandardTelegram(telegram);
        return message;
    }

    private static String get(int port, String id) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/httpin").openConnection();
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("X-Id", id);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return input.readLine();
        } finally {
            connection.disconnect();
        }
    }

    @Test
    public void dummyAckWithoutSafeReplyKeepsOfficeWait() throws Exception {
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        DefaultCamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(dummyAck("U1"));
        AtomicInteger calls = new AtomicInteger();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> processed = worker.submit(() -> new MCAWorkAfterAsync(manager).process(exchange, sync -> {
                assertTrue(sync);
                calls.incrementAndGet();
            }));
            String key = KEY_PREFIX + "U1";
            assertTrue(BidTestSupport.waitUntil(() -> {
                BidInfo ticket = manager.getBidInfoList().get(key);
                return ticket != null && ticket.getStatus() == BidInfo.BidStatus.WAIT;
            }, 3000));
            Thread.sleep(100);
            assertFalse("office wait holds the calling thread until the release", processed.isDone());
            assertEquals(0, manager.getAsyncPendingCount());
            Exchange release = new DefaultExchange(context);
            release.getIn().setBody("final-response");
            assertEquals(BidManager.ReleaseResult.DELIVERED, manager.bidResult(key, release));
            assertTrue(processed.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals("final-response", exchange.getIn().getBody());
            String stats = manager.getAsyncStats();
            assertTrue(stats, stats.contains("suspended=0") && stats.contains("officeWaits=1"));
        } finally {
            worker.shutdownNow();
            manager.shutdownAsync();
        }
    }

    @Test
    public void localAdapterOutDummyAckKeepsOfficeWaitAndIsAnsweredOnce() throws Exception {
        String stats = dummyAcksThroughHttp(LOCAL_OPTIONS, 6);
        assertTrue(stats, stats.contains("suspended=0") && stats.contains("officeWaits=6"));
    }

    @Test
    public void gcbAdapterOutDummyAckIsSuspendedAndAnsweredOnce() throws Exception {
        String stats = dummyAcksThroughHttp(GCB_OPTIONS, 6);
        assertTrue(stats, stats.contains("suspended=6") && stats.contains("officeWaits=0"));
    }

    /**
     * Concurrent dummy-ack transactions through real netty4-http; each is released once its ticket
     * waits and its dummy-ack channel has closed. Every client must get its own real response, and
     * the step after the continuation must run exactly once per transaction. Returns the stats line.
     */
    private String dummyAcksThroughHttp(String producerOptions, int transactions) throws Exception {
        int inPort = freePort();
        int gcbPort = freePort();
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("ibkHttpProducerInitializer", new IBKHttpProducerInitializer());   // as NettyBean does
        DefaultCamelContext context = new DefaultCamelContext(registry);
        context.addComponent("gcbhttp", new NettyHttpComponent());   // fake GCB gets its own executor group
        context.getShutdownStrategy().setTimeout(3);
        Map<String, AtomicInteger> afterContinuation = new ConcurrentHashMap<>();
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("gcbhttp:http://127.0.0.1:" + gcbPort + "/service/sync").transform(constant("dummy-ack"));
                from("netty4-http:http://127.0.0.1:" + inPort + "/httpin")
                    .setProperty("txId", header("X-Id"))
                    .removeHeaders("*")
                    .setBody(constant("request"))
                    .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                    .to("netty4-http:http://127.0.0.1:" + gcbPort + "/service/sync?" + producerOptions)
                    .process(e -> e.getIn().setBody(dummyAck(e.getProperty("txId", String.class))))
                    .process(new MCAWorkAfterAsync(manager))
                    .process(e -> {
                        afterContinuation.computeIfAbsent(e.getProperty("txId", String.class), k -> new AtomicInteger())
                            .incrementAndGet();
                        e.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                    });
            }
        });
        context.start();
        ExecutorService clients = Executors.newFixedThreadPool(transactions);
        try {
            List<Future<String>> responses = new ArrayList<>();
            for (int i = 0; i < transactions; i++) {
                String id = "T" + i;
                responses.add(clients.submit(() -> get(inPort, id)));
            }
            // GCB's real response for each waiting transaction
            Set<String> released = new HashSet<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (released.size() < transactions && System.nanoTime() < deadline) {
                for (BidInfo ticket : manager.getBidInfoList().values()) {
                    if (ticket.getStatus() == BidInfo.BidStatus.WAIT && released.add(ticket.getName())) {
                        Thread.sleep(50);   // the dummy-ack channel (disconnect=true) closes while it waits
                        Exchange release = new DefaultExchange(context);
                        release.getIn().setBody("FINAL:" + ticket.getName().substring(KEY_PREFIX.length()));
                        assertEquals(BidManager.ReleaseResult.DELIVERED, manager.bidResult(ticket.getName(), release));
                    }
                }
                Thread.sleep(5);
            }
            assertEquals(transactions, released.size());
            for (int i = 0; i < transactions; i++) {
                assertEquals("FINAL:T" + i, responses.get(i).get(10, TimeUnit.SECONDS));
            }
            Thread.sleep(200);   // a second producer callback would run the next step again
            for (int i = 0; i < transactions; i++) {
                assertEquals("T" + i, 1, afterContinuation.get("T" + i).get());
            }
            assertTrue(manager.getBidInfoList().isEmpty());
            assertEquals(0, manager.getAsyncPendingCount());
            return manager.getAsyncStats();
        } finally {
            clients.shutdownNow();
            manager.shutdownAsync();
            context.stop();
        }
    }
}
