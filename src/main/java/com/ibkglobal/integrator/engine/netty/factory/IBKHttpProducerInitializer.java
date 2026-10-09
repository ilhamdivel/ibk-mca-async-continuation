package com.ibkglobal.integrator.engine.netty.factory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.netty4.ChannelHandlerFactory;
import org.apache.camel.component.netty4.ClientInitializerFactory;
import org.apache.camel.component.netty4.NettyProducer;
import org.apache.camel.component.netty4.http.NettyHttpConfiguration;
import org.apache.camel.component.netty4.http.NettyHttpProducer;
import org.apache.camel.component.netty4.http.handlers.HttpClientChannelHandler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.timeout.ReadTimeoutHandler;

public class IBKHttpProducerInitializer extends ClientInitializerFactory {
	
	protected NettyHttpConfiguration configuration;
    private NettyHttpProducer producer;
    
    public IBKHttpProducerInitializer() {}
    
    public IBKHttpProducerInitializer(NettyHttpProducer nettyProducer) {
    	this.producer = nettyProducer;
    	configuration = nettyProducer.getConfiguration();
    }
    
    @Override
	protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        pipeline.addLast("http", new HttpClientCodec());
        
        List<ChannelHandler> encoders = producer.getConfiguration().getEncoders();
        for (int x = 0; x < encoders.size(); x++) {
            ChannelHandler encoder = encoders.get(x);
            if (encoder instanceof ChannelHandlerFactory) {
                encoder = ((ChannelHandlerFactory) encoder).newChannelHandler();
            }
            pipeline.addLast("encoder-" + x, encoder);
        }

        List<ChannelHandler> decoders = producer.getConfiguration().getDecoders();
        for (int x = 0; x < decoders.size(); x++) {
            ChannelHandler decoder = decoders.get(x);
            if (decoder instanceof ChannelHandlerFactory) {
                // use the factory to create a new instance of the channel as it may not be shareable
                decoder = ((ChannelHandlerFactory) decoder).newChannelHandler();
            }
            pipeline.addLast("decoder-" + x, decoder);
        }
        pipeline.addLast("aggregator", new HttpObjectAggregator(configuration.getChunkedMaxContentLength()));

        if (producer.getConfiguration().getRequestTimeout() > 0) {
            ChannelHandler timeout = new ReadTimeoutHandler(producer.getConfiguration().getRequestTimeout(), TimeUnit.MILLISECONDS);
            pipeline.addLast("timeout", timeout);
        }
       
        pipeline.addLast("handler", new HttpClientChannelHandler(producer));
	}

	@Override
	public ClientInitializerFactory createPipelineFactory(NettyProducer producer) {
		return new IBKHttpProducerInitializer((NettyHttpProducer) producer);
	}
}
