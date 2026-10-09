package com.ibkglobal.integrator.engine.timer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.support.DefaultTimeoutMap;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.engine.manager.BidManager;
import com.ibkglobal.integrator.engine.model.BidInfo;

import lombok.Getter;

public class IBKTimeout<T> {

  @Autowired
  CamelConfig camelConfig;

  @Autowired
  BidManager bidManager;

  @Getter
  private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  @Getter
  private DefaultTimeoutMap<String, T> timeout = null;

  public void init() throws Exception {
    if (timeout != null) {
      start();
    }
  }

  public void timeCom() throws Exception {
    if (timeout == null) {
      timeout = new DefaultTimeoutMap<String, T>(executor, 200, true) {

        @Override
        public boolean onEviction(String key, T value) {

          if (camelConfig.getCamelContext().getStatus().isStopped()
              || camelConfig.getCamelContext().getStatus().isStopping()) {

            executor.shutdown();
            return false;
          }

          return true;
        }
      };
    }
  }

  public void timeBid() throws Exception {
    if (timeout == null) {
      timeout = new DefaultTimeoutMap<String, T>(executor, 200, true) {

        @Override
        public boolean onEviction(String key, T value) {

          if (camelConfig.getCamelContext().getStatus().isStopped()
              || camelConfig.getCamelContext().getStatus().isStopping()) {

            executor.shutdown();
            return false;
          }

          // pass the armed value so a stale timer can't time out a newer BidInfo
          bidManager.bidTimeout(key, (BidInfo) value);

          return true;
        }
      };
    }
  }

  /**
   * Start
   * 
   * @throws Exception
   */
  public void start() throws Exception {
    timeout.start();
  }

  /**
   * Stop
   * 
   * @throws Exception
   */
  public void stop() throws Exception {
    timeout.stop();
  }

  /**
   * Put
   * 
   * @param key
   * @param exchange
   * @param timeoutMillis
   * @return
   * @throws Exception
   */
  public T put(String key, T exchange, long timeoutMillis) throws Exception {
    return timeout.put(key, exchange, timeoutMillis);
  }

  /**
   * Put If Absent
   * 
   * @param key
   * @param exchange
   * @param timeoutMillis
   * @return
   * @throws Exception
   */
  public T putIfAbsent(String key, T exchange, long timeoutMillis) throws Exception {
    return timeout.putIfAbsent(key, exchange, timeoutMillis);
  }

  /**
   * Get
   * 
   * @param key
   * @return
   * @throws Exception
   */
  public T get(String key) throws Exception {
    return timeout.get(key);
  }

  /**
   * Remove
   * 
   * @param key
   * @return
   */
  public boolean remove(String key) {
    return timeout.remove(key) != null ? true : false;
  }
}
