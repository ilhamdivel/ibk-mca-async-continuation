package com.ibkglobal.integrator.engine.manager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterAsync;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class BidObservabilityTest {
    private Logger root;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> captured;
    private BidManager manager;
    private DefaultCamelContext context;

    @Before
    public void setUp() {
        root = LogManager.getLogger(LogType.ROOT);
        previousLevel = root.getLevel();
        root.setLevel(Level.INFO);
        captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        manager = new BidManager();
        manager.ibkTimeoutBid = mock(IBKTimeout.class);
        context = new DefaultCamelContext();
    }

    @After
    public void tearDown() {
        manager.shutdownAsync();
        root.detachAppender(captured);
        root.setLevel(previousLevel);
    }

    private List<String> messages() {
        return captured.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    private BidInfo ticket(String key) {
        BidInfo ticket = new BidInfo();
        ticket.setName(key);
        ticket.setBeforeExchange(new DefaultExchange(context));
        return ticket;
    }

    @Test
    public void dummyAckKeepsOfficeRcaLogLine() throws Exception {
        StandardTelegram telegram = mock(StandardTelegram.class, RETURNS_DEEP_STUBS);
        when(telegram.getSttlSysCopt().getOtptTmgtDcd()).thenReturn("4");
        when(telegram.getSttlSysCopt().getWhbnSttlWrtnYmd()).thenReturn("20261010");
        when(telegram.getSttlSysCopt().getWhbnSttlCretSysNm()).thenReturn("GIB");
        when(telegram.getSttlSysCopt().getWhbnSttlSrn()).thenReturn("SRN");
        IBKMessage message = mock(IBKMessage.class);
        when(message.getStandardTelegram()).thenReturn(telegram);
        when(message.getInterfaceId()).thenReturn("GITO00008290");
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(message);
        assertFalse(new MCAWorkAfterAsync(manager).process(exchange, sync -> { }));
        assertTrue(messages().contains("MCAWorkAfterProcess dummyCheck InterfaceID: GITO00008290"));
    }

    @Test
    public void earlyReleaseKeepsOfficeLogLineAndCounters() throws Exception {
        manager.bidPreRegister("early");
        manager.bidResult("early", new DefaultExchange(context));
        assertTrue(manager.bidStartAsync(ticket("early"), sync -> { }));
        assertTrue(messages().contains("Bid release arrived before dummy ack, complete without wait : early"));
        assertTrue(manager.getAsyncStats().contains("earlyRelease=1"));
        assertTrue(manager.getAsyncStats().contains("suspended=0"));
    }

    @Test
    public void countersTrackEveryOutcome() throws Exception {
        manager.setAsyncCapacity(2);
        assertFalse(manager.bidStartAsync(ticket("delivered"), sync -> { }));
        BidInfo slow = ticket("slow");
        assertFalse(manager.bidStartAsync(slow, sync -> { }));
        assertEquals(2, manager.getAsyncPendingCount());
        assertTrue(manager.getOldestAsyncPendingMillis() >= 0);

        assertTrue(manager.bidStartAsync(ticket("overflow"), sync -> { }));   // answered as BID timeout
        try {
            manager.bidStartAsync(ticket("slow"), sync -> { });                // second dummy, same key
            fail("duplicate dummy must keep the MCA_BID error");
        } catch (com.ibkglobal.integrator.exception.IBKExceptionMCA expected) { }

        manager.bidResult("delivered", new DefaultExchange(context));
        manager.bidTimeout("slow", slow);
        long end = System.currentTimeMillis() + 3000;
        while (manager.getAsyncPendingCount() > 0 && System.currentTimeMillis() < end) Thread.sleep(10);

        String stats = manager.getAsyncStats();
        assertTrue(stats, stats.startsWith("pending=0/2, oldestPendingMs=0"));
        assertTrue(stats, stats.contains("suspended=2, delivered=1, earlyRelease=0, timedOut=1, notAccepted=1, duplicate=1"));
        assertTrue(messages().contains("Bid Timeout : slow"));
    }
}
