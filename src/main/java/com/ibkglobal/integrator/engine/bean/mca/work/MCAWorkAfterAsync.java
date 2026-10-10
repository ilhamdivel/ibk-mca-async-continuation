package com.ibkglobal.integrator.engine.bean.mca.work;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.AsyncCallback;
import org.apache.camel.util.AsyncProcessorHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ibkglobal.integrator.engine.manager.BidManager;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.util.BidUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;

@Component
public class MCAWorkAfterAsync implements AsyncProcessor {
    @Autowired
    private BidManager bidManager;

    public MCAWorkAfterAsync() { }

    public MCAWorkAfterAsync(BidManager manager) {
        this.bidManager = manager;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        AtomicBoolean completed = new AtomicBoolean();
        AsyncCallback guarded = doneSync -> {
            if (completed.compareAndSet(false, true)) {
                callback.done(doneSync);
            }
        };
        try {
            IBKMessage message = exchange.getIn().getBody(IBKMessage.class);
            StandardTelegram telegram = message.getStandardTelegram();
            if (telegram != null && "4".equals(telegram.getSttlSysCopt().getOtptTmgtDcd())) {
                // Same text as the office MCAWorkAfterProcess line: operations grep for it
                // to find dummy acks (N4) during BID root-cause analysis.
                LogManager.getLogger(LogType.ROOT)
                    .info("MCAWorkAfterProcess dummyCheck InterfaceID: " + message.getInterfaceId());
                BidInfo bidInfo = BidUtil.bidCreate(exchange, telegram);
                if (Boolean.TRUE.equals(exchange.getProperty(BidManager.BID_SAFE_REPLY, Boolean.class))) {
                    return bidManager.bidStartAsync(bidInfo, guarded);
                }
                // Not delivered by the GCB adapter-out handler (LOCAL adapter-out, TCP, ...):
                // suspending is unsafe there, keep the office blocking wait.
                bidManager.bidStartOfficeWait(bidInfo);
            }
        } catch (Exception failure) {
            exchange.setException(failure);
        }
        guarded.done(true);
        return true;
    }
}
