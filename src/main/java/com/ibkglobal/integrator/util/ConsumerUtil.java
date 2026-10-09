package com.ibkglobal.integrator.util;

import org.apache.camel.Consumer;
import org.apache.camel.component.direct.DirectConsumer;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.netty4.NettyConsumer;
import org.apache.camel.component.netty4.http.NettyHttpConsumer;

import com.ibkglobal.integrator.manager.model.RouteStat;

public class ConsumerUtil {
	
	/**
	 * Consumer 종류 찾고 라우터 상태 정보에 매핑
	 * @param info
	 * @param consumer
	 */
	public static void consumerInfoSet(RouteStat info, Consumer consumer) {
		if (consumer instanceof DirectConsumer) {
			info.setConsumerStarted(((DirectConsumer) consumer).isStarted());
		} else if (consumer instanceof NettyConsumer) {
			info.setConsumerStarted(((NettyConsumer) consumer).isStarted());
		} else if (consumer instanceof NettyHttpConsumer) {
			info.setConsumerStarted(((NettyHttpConsumer) consumer).isStarted());
//		} else if (consumer instanceof JmsConsumer) {
//			info.setConsumerStarted(((JmsConsumer) consumer).isStarted());
		} else if (consumer instanceof FileConsumer) {
			info.setConsumerStarted(((FileConsumer) consumer).isStarted());
		}
	}
}
