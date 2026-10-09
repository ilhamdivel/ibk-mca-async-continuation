package com.ibkglobal.integrator.engine.bean.mca.work;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.AsyncCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ibkglobal.integrator.engine.manager.BidManager;
import com.ibkglobal.integrator.util.BidUtil;
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
        org.apache.camel.util.AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
        AsyncCallback guarded = doneSync -> {
            if (completed.compareAndSet(false, true)) {
                callback.done(doneSync);
            }
        };
        try {
            IBKMessage message = exchange.getIn().getBody(IBKMessage.class);
            StandardTelegram telegram = message.getStandardTelegram();
            if (telegram != null && "4".equals(telegram.getSttlSysCopt().getOtptTmgtDcd())) {
                return bidManager.bidStartAsync(BidUtil.bidCreate(exchange, telegram), guarded);
            }
        } catch (Exception failure) {
            exchange.setException(failure);
        }
        guarded.done(true);
        return true;
    }
}
