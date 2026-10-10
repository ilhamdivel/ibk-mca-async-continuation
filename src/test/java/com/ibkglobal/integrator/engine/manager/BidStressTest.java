package com.ibkglobal.integrator.engine.manager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty4.http.NettyHttpComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.Test;

import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.engine.bean.mca.work.MCAGcbComBean;
import com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterAsync;
import com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterProcess;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.netty.factory.IBKHttpProducerInitializer;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.BidUtil;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.common.normal.copt.SttlSysCopt;

/**
 * Mixed-load stress: many normal (non-BID) transactions, BID dummy-ack transactions (release after
 * the dummy and release before the dummy) and BID approval transactions (some followed by a late
 * real response after approval), all concurrent:
 *
 *   netty4-http Adapter-In -> direct:M.GCB0.COM0.ROUTE            (default, -Dbid.stress.topology=direct)
 *                          -> real MCAGcbComBean bridge -> direct  (deployed, -Dbid.stress.topology=bridge)
 *     release (real response 0/R/K): ProcessPreMCA's release step -> BidManager.bidResult -> ROUTE_STOP
 *     request : MCAWorkPreProcess pre-registration -> netty4-http to GCB (4 IO workers, as in prod)
 *               -> real MCAWorkAfterAsync (dummy ack suspends) -> after-process
 *
 * Real classes: MCAGcbComBean, MCAWorkAfterAsync, BidManager, BidUtil, IBKMessage, StandardTelegram,
 * SttlSysCopt, Camel 2.21.1 / Netty. Copied verbatim (they need internal Penta/mms artifacts to load):
 * MCAWorkPreProcess.bidPreRegister (MCAWorkPreProcess.java:67-89) and the release block of
 * ProcessPreMCA.init (ProcessPreMCA.java:94-106). GCB is simulated.
 *
 * Checks: every response belongs to its own transaction, no error except the expected
 * "bidInfo is null" of late approval releases, no ticket/permit/timer left, no thread parked in the
 * legacy wait, and normal transactions are not delayed by suspended dummy acks.
 *
 * Scale with -Dbid.stress.normal / .dummy / .approval / .clients. -Dbid.stress.legacy=true runs the
 * office blocking MCAWorkAfterProcess instead, for comparison only.
 *
 * Why "direct" is the default: with the deployed bridge every in-flight transaction (normal or BID)
 * parks its Adapter-In executor thread, and netty pins each new connection - including GCB's real
 * response - to the next executor round-robin. A release pinned to a parked thread waits behind it;
 * pinned to its own transaction's thread it waits until the 100 s BID timeout. That is the known,
 * pre-existing phase-2 limitation (BidTopologyTest), not something this change can fix, and it makes
 * a bridge stress run non-deterministic. The bridge mode is kept to measure it.
 */
public class BidStressTest {

    private static final int NORMAL = Integer.getInteger("bid.stress.normal", 1200);
    private static final int DUMMY = Integer.getInteger("bid.stress.dummy", 600);
    private static final int APPROVAL = Integer.getInteger("bid.stress.approval", 300);
    private static final int CLIENTS = Integer.getInteger("bid.stress.clients", 64);
    private static final boolean LEGACY = Boolean.getBoolean("bid.stress.legacy");
    private static final boolean BRIDGE = "bridge".equals(System.getProperty("bid.stress.topology", "direct"));
    private static final int ADAPTER_IN_THREADS = Integer.getInteger("bid.stress.adapterInThreads", 512);
    /**
     * GCB producer options exactly as EndpointCreate builds them for prod M.GCB0.1000.HC40610
     * (custom initializer, disconnect=true, reqTimeOut 90000) plus the 4 IO workers seen in prod.
     */
    private static final String GCB_OPTIONS = System.getProperty("bid.stress.gcbOptions",
        "clientInitializerFactory=#ibkHttpProducerInitializer&disconnect=true&requestTimeout=90000&workerCount=4");

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static IBKMessage message(String interfaceId, String id, boolean bidKey, String otpt, String rspn) {
        SttlSysCopt copt = new SttlSysCopt();
        copt.setSttlIntfId("GITO00008290");          // not GCBO00006560: MCAGcbComBean routes it to the inbound route
        copt.setOtptTmgtDcd(otpt);
        copt.setRspnPcrsDcd(rspn);
        if (bidKey) {
            copt.setWhbnSttlWrtnYmd("20261010");
            copt.setWhbnSttlCretSysNm("STRESS");
            copt.setWhbnSttlSrn(id);
        }
        StandardTelegram telegram = new StandardTelegram();
        telegram.setSttlSysCopt(copt);
        IBKMessage message = new IBKMessage();
        message.setInterfaceId(interfaceId);
        message.setStandardTelegram(telegram);
        return message;
    }

    private static MCAGcbComBean gcbComBean(DefaultCamelContext context) throws Exception {
        CamelConfig config = new CamelConfig();
        Field camelContext = CamelConfig.class.getDeclaredField("camelContext");
        camelContext.setAccessible(true);
        camelContext.set(config, context);
        MCAGcbComBean bean = new MCAGcbComBean();
        Field camelConfig = MCAGcbComBean.class.getDeclaredField("camelConfig");
        camelConfig.setAccessible(true);
        camelConfig.set(bean, config);
        return bean;
    }

    /** Returns {status, body}. */
    private static String[] http(int port, String kind, String id) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/httpin").openConnection();
        connection.setReadTimeout(30000);
        connection.setConnectTimeout(5000);
        connection.setRequestProperty("Connection", "close");   // new connection each time, like the LB
        connection.setRequestProperty("X-Kind", kind);
        connection.setRequestProperty("X-Id", id);
        try {
            int status = connection.getResponseCode();
            InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String body = "";
            if (stream != null) {
                try (BufferedReader input = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    body = String.valueOf(input.readLine());
                }
            }
            return new String[] { String.valueOf(status), body };
        } finally {
            connection.disconnect();
        }
    }

    private static boolean anyThreadParkedIn(String frame) {
        for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(false, false)) {
            for (StackTraceElement element : info.getStackTrace()) {
                if (element.toString().contains(frame)) return true;
            }
        }
        return false;
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static String latency(String label, List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return String.format("%-22s n=%5d  p50=%4d ms  p95=%4d ms  p99=%4d ms  max=%5d ms", label, sorted.size(),
            percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99), sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1));
    }

    @Test
    public void mixedNormalDummyAckAndApprovalTrafficRunsCorrectly() throws Exception {
        int inPort = freePort();
        int gcbPort = freePort();
        BidManager manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("ibkHttpProducerInitializer", new IBKHttpProducerInitializer());   // as NettyBean does
        DefaultCamelContext context = new DefaultCamelContext(registry);
        context.getComponent("netty4-http", NettyHttpComponent.class).setMaximumPoolSize(ADAPTER_IN_THREADS);
        NettyHttpComponent gcbComponent = new NettyHttpComponent();
        gcbComponent.setMaximumPoolSize(128);
        context.addComponent("gcbhttp", gcbComponent);
        context.getShutdownStrategy().setTimeout(5);

        MCAGcbComBean comBean = gcbComBean(context);
        Processor after;
        if (LEGACY) {
            MCAWorkAfterProcess legacy = new MCAWorkAfterProcess();
            Field field = MCAWorkAfterProcess.class.getDeclaredField("bidManager");
            field.setAccessible(true);
            field.set(legacy, manager);
            after = legacy::execute;
        } else {
            after = new MCAWorkAfterAsync(manager);
        }

        ExecutorService releaseSenders = Executors.newFixedThreadPool(64);
        ScheduledExecutorService releaseTimer = Executors.newScheduledThreadPool(4);
        Map<String, String[]> releaseOutcome = new ConcurrentHashMap<>();
        AtomicInteger maxPending = new AtomicInteger();

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                // ---- Simulated GCB: answers per transaction kind; sends real responses (releases) to MCA.
                from("gcbhttp:http://127.0.0.1:" + gcbPort + "/service/sync").process(e -> {
                    String[] request = e.getIn().getBody(String.class).split("\\|");
                    String kind = request[0];
                    String id = request[1];
                    Runnable release = () -> {
                        try { releaseOutcome.put(id, http(inPort, "release", id)); }
                        catch (Exception failure) { releaseOutcome.put(id, new String[] { "EXC", failure.toString() }); }
                    };
                    switch (kind) {
                    case "normal":
                        e.getIn().setBody("normal|" + id + "|0|0");
                        break;
                    case "approval":
                        e.getIn().setBody("approval|" + id + "|0|1");   // rspnPcrsDcd 1 = request for approval, no dummy
                        break;
                    case "approvalLate":
                        e.getIn().setBody("approval|" + id + "|0|1");
                        // approver acts later; GCB then sends the real response for a transaction already answered
                        releaseTimer.schedule(() -> releaseSenders.submit(release),
                            ThreadLocalRandom.current().nextInt(300, 600), TimeUnit.MILLISECONDS);
                        break;
                    case "dummyFirst":
                        e.getIn().setBody("dummy|" + id + "|4|0");
                        releaseTimer.schedule(() -> releaseSenders.submit(release),
                            ThreadLocalRandom.current().nextInt(5, 150), TimeUnit.MILLISECONDS);
                        break;
                    case "releaseFirst":
                        // The real response overtakes the dummy ack: send it (asynchronously, like FEP/CHC)
                        // and answer the dummy only once MCA has parked it (bounded wait).
                        releaseSenders.submit(release);
                        String key = "20261010STRESS" + id;
                        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                        while (System.nanoTime() < until) {
                            BidInfo ticket = manager.getBidInfoList().get(key);
                            if (ticket != null && ticket.getStatus() == BidInfo.BidStatus.RELEASED) break;
                            Thread.sleep(1);
                        }
                        e.getIn().setBody("dummy|" + id + "|4|0");
                        break;
                    default:
                        throw new IllegalStateException("unknown kind " + kind);
                    }
                });

                // ---- Adapter-In: deployed BEAN MCAGcbComBean.execute bridge, or archived to(direct:).
                RouteDefinition adapterIn = from("netty4-http:http://127.0.0.1:" + inPort + "/httpin")
                    .process(e -> {
                        String kind = e.getIn().getHeader("X-Kind", String.class);
                        String id = e.getIn().getHeader("X-Id", String.class);
                        boolean bidKey = !"normal".equals(kind);
                        String interfaceId = "release".equals(kind) ? "FINAL:" + id : "REQUEST:" + id;
                        e.getIn().setBody(message(interfaceId, id, bidKey, "release".equals(kind) ? "0" : null, null));
                    });
                (BRIDGE ? adapterIn.process(comBean::execute) : adapterIn.to("direct:M.GCB0.COM0.ROUTE"))
                    .process(e -> {
                        Object body = e.getIn().getBody();
                        e.getIn().setBody(body instanceof IBKMessage ? ((IBKMessage) body).getInterfaceId() : String.valueOf(body));
                        e.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                    });

                // ---- Inbound router (MCA_INBOUND).
                from("direct:M.GCB0.COM0.ROUTE")
                    .choice()
                      .when(header("X-Kind").isEqualTo("release"))
                        .process(e -> {
                            // ProcessPreMCA.java:94-106, verbatim logic for a real response (0/R/K)
                            IBKMessage ibkMessage = e.getIn().getBody(IBKMessage.class);
                            String bidKey = BidUtil.getBidKey(ibkMessage.getStandardTelegram());
                            BidManager.ReleaseResult result = manager.bidResult(bidKey, e.copy());
                            if (result == BidManager.ReleaseResult.NOT_FOUND) {
                                throw new IBKExceptionMCA(ErrorType.BID, "bidInfo is null, bidKey: " + bidKey);
                            }
                            e.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
                            e.getIn().setBody("release:" + result);
                        })
                      .otherwise()
                        .process(e -> {
                            // MCAWorkPreProcess.bidPreRegister (MCAWorkPreProcess.java:67-89), verbatim logic
                            IBKMessage ibkMessage = e.getIn().getBody(IBKMessage.class);
                            StandardTelegram standardTelegram = ibkMessage.getStandardTelegram();
                            if (!BidUtil.hasBidKey(standardTelegram)) {
                                return;
                            }
                            String key = BidUtil.getBidKey(standardTelegram);
                            BidInfo bidInfo = manager.bidPreRegister(key);
                            if (bidInfo == null) {
                                return;
                            }
                            e.setProperty("MCA_BID_OWNER", bidInfo);
                            e.addOnCompletion(new SynchronizationAdapter() {
                                @Override
                                public void onDone(Exchange done) {
                                    manager.bidUnregister(key, bidInfo);
                                }
                            });
                        })
                        .process(e -> e.getIn().setBody(e.getIn().getHeader("X-Kind", String.class) + "|"
                            + e.getIn().getHeader("X-Id", String.class)))
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                        .to("netty4-http:http://127.0.0.1:" + gcbPort + "/service/sync?" + GCB_OPTIONS)
                        .process(e -> {
                            String[] answer = e.getIn().getBody(String.class).split("\\|");
                            String kind = answer[0];
                            String id = answer[1];
                            boolean bidKey = !"normal".equals(kind);
                            String interfaceId = "dummy".equals(kind) ? "DUMMY:" + id : kind + ":" + id;
                            e.getIn().setBody(message(interfaceId, id, bidKey, answer[2], answer[3]));
                        })
                        .process(after)                                          // real MCAWorkAfterAsync
                        .process(e -> {
                            int pending = manager.getAsyncPendingCount();
                            maxPending.accumulateAndGet(pending, Math::max);
                        })
                    .end();
            }
        });
        context.start();

        // ---- Workload: shuffled mix.
        List<String[]> work = new ArrayList<>();
        for (int i = 0; i < NORMAL; i++) work.add(new String[] { "normal", "N" + i });
        for (int i = 0; i < DUMMY; i++) work.add(new String[] { i % 2 == 0 ? "dummyFirst" : "releaseFirst", "D" + i });
        for (int i = 0; i < APPROVAL; i++) work.add(new String[] { i % 3 == 0 ? "approvalLate" : "approval", "A" + i });
        Collections.shuffle(work, new Random(20261010));

        ExecutorService clients = Executors.newFixedThreadPool(CLIENTS);
        Map<String, Long> latencyMs = new ConcurrentHashMap<>();
        Map<String, String[]> responses = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        long started = System.nanoTime();
        for (String[] item : work) {
            futures.add(clients.submit(() -> {
                long t0 = System.nanoTime();
                try { responses.put(item[1], http(inPort, item[0], item[1])); }
                catch (Exception failure) { responses.put(item[1], new String[] { "EXC", failure.toString() }); }
                latencyMs.put(item[1], TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
            }));
        }
        for (Future<?> future : futures) future.get(10, TimeUnit.MINUTES);
        long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        // Late approval releases arrive up to 600 ms after their transaction finished.
        int lateApprovals = (APPROVAL + 2) / 3;
        int expectedReleases = DUMMY + lateApprovals;
        boolean allReleasesAnswered = BidTestSupport.waitUntil(() -> releaseOutcome.size() == expectedReleases, 30000);

        // ---- Verify every transaction.
        List<String> problems = new ArrayList<>();
        int crossed = 0;   // 200 answer carrying another transaction's id: the worst possible failure
        List<Long> normalLat = new ArrayList<>();
        List<Long> dummyLat = new ArrayList<>();
        List<Long> approvalLat = new ArrayList<>();
        int lateNotFound = 0;
        int lateParked = 0;
        for (String[] item : work) {
            String kind = item[0];
            String id = item[1];
            String[] response = responses.get(id);
            String expected = kind.startsWith("dummy") || kind.equals("releaseFirst") ? "FINAL:" + id
                : kind.startsWith("approval") ? "approval:" + id : "normal:" + id;
            if (response == null || !"200".equals(response[0]) || !expected.equals(response[1])) {
                problems.add(kind + " " + id + " expected 200 " + expected + " got "
                    + (response == null ? "null" : response[0] + " " + response[1]));
                if (response != null && "200".equals(response[0]) && !response[1].endsWith(":" + id)) crossed++;
            }
            long ms = latencyMs.get(id);
            (kind.equals("normal") ? normalLat : kind.startsWith("approval") ? approvalLat : dummyLat).add(ms);
            String[] release = releaseOutcome.get(id);
            if (kind.equals("dummyFirst") || kind.equals("releaseFirst")) {
                boolean ok = release != null && "200".equals(release[0])
                    && (release[1].equals("release:DELIVERED") || release[1].equals("release:PARKED"));
                if (!ok) problems.add(kind + " " + id + " release " + (release == null ? "null" : release[0] + " " + release[1]));
                if (kind.equals("releaseFirst") && ok && !release[1].equals("release:PARKED")) {
                    problems.add("releaseFirst " + id + " must be PARKED, was " + release[1]);
                }
            } else if (kind.equals("approvalLate")) {
                // Office behaviour: the approval answer already went out; the late real response finds no
                // ticket ("bidInfo is null") - or, if it overtakes the completion, is parked and discarded.
                if (release != null && "500".equals(release[0]) && release[1].contains("bidInfo is null")) lateNotFound++;
                else if (release != null && "200".equals(release[0]) && "release:PARKED".equals(release[1])) lateParked++;
                else problems.add("approvalLate " + id + " release " + (release == null ? "null" : release[0] + " " + release[1]));
            }
        }
        boolean clean = BidTestSupport.waitUntil(
            () -> manager.getBidInfoList().isEmpty() && manager.getAsyncPendingCount() == 0, 5000);
        String stats = manager.getAsyncStats();

        System.out.println("===== BID stress (" + (LEGACY ? "LEGACY office blocking wait" : "async continuation")
            + ", topology=" + (BRIDGE ? "bridge(MCAGcbComBean)" : "direct") + ", adapterInThreads=" + ADAPTER_IN_THREADS
            + ", gcb=" + GCB_OPTIONS
            + ") clients=" + CLIENTS + " wall=" + wallMs + " ms, maxPendingSeen=" + maxPending.get());
        System.out.println(latency("normal (non-BID)", normalLat));
        System.out.println(latency("BID dummy-ack", dummyLat));
        System.out.println(latency("BID approval", approvalLat));
        System.out.println("late approval releases: notFound=" + lateNotFound + " parkedThenDiscarded=" + lateParked
            + " of " + lateApprovals);
        System.out.println("stats: " + stats);
        System.out.println("releases answered: " + releaseOutcome.size() + " of " + expectedReleases);
        System.out.println("problems: " + problems.size() + " crossedReplies=" + crossed
            + (problems.isEmpty() ? "" : " first=" + problems.subList(0, Math.min(5, problems.size()))));

        try {
            assertTrue("releases answered " + releaseOutcome.size() + " of " + expectedReleases, allReleasesAnswered);
            assertTrue(problems.toString(), problems.isEmpty());
            assertEquals(lateApprovals, lateNotFound + lateParked);
            assertTrue("tickets/permits left: " + stats, clean);
            if (!LEGACY) {
                assertFalse("no thread may block in the legacy wait", anyThreadParkedIn("BidManager.workWait"));
                assertTrue(stats, stats.contains("timedOut=0"));
                assertTrue(stats, stats.contains("notAccepted=0"));
                assertTrue(stats, stats.contains("duplicate=0"));
                // GCB adapter-out replies (ibkHttpProducerInitializer) are suspended; any other client keeps
                // the office wait (BidSafeReplyTest).
                assertTrue(stats, stats.contains(GCB_OPTIONS.contains("#ibkHttpProducerInitializer")
                    ? "officeWaits=0" : "suspended=0"));
            }
        } finally {
            clients.shutdownNow();
            releaseTimer.shutdownNow();
            releaseSenders.shutdownNow();
            manager.shutdownAsync();
            context.stop();
        }
    }
}
