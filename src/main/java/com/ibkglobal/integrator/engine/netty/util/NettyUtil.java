package com.ibkglobal.integrator.engine.netty.util;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.component.netty4.ChannelHandlerFactories;
import org.apache.camel.component.netty4.NettyConfiguration;
import org.springframework.util.StringUtils;

import com.ibkglobal.integrator.config.EndpointCode;
import com.ibkglobal.integrator.engine.netty.codec.IBKDefaultDecoder;
import com.ibkglobal.integrator.engine.netty.codec.IBKDefaultEncoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

public class NettyUtil {

  public final static Integer NETTY_MAX_BUFFER_SIZE = 1048576;

  /**
   * getSession
   * 
   * @param ctx
   * @return
   */
  public static String getSessionKey(ChannelHandlerContext ctx) {
    return ctx.pipeline().channel().id().asShortText();
  }

  /**
   * getIpPort
   * 
   * @param ctx
   * @return
   */
  public static String getKey(ChannelHandlerContext ctx) {
    String address = getHost(ctx);
    String port = getPort(ctx);

    return address + port;
  }

  /**
   * getIp
   * 
   * @param ctx
   * @return
   */
  public static String getHost(ChannelHandlerContext ctx) {
    SocketAddress address = ctx.channel().remoteAddress();

    InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();

    return address != null ? socketAddress.getAddress().getHostAddress() : "";
  }

  /**
   * getPort
   * 
   * @param ctx
   * @return
   */
  public static String getPort(ChannelHandlerContext ctx) {
    InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();

    Integer result = socketAddress.getPort();

    return result.toString();
  }

  /**
   * 코덱 셋
   * 
   * @param configuration
   * @param encoders
   * @param decoders
   */
  public static void setCodec(String protocol, NettyConfiguration configuration, List<ChannelHandler> encoders,
      List<ChannelHandler> decoders) {
    // 각종 옵션
    Map<String, Object> option = configuration.getOptions();

    // 코덱셋
    if (option != null) {
      String encoding = option.containsKey("encoding") ? option.get("encoding").toString() : "";

      if (!StringUtils.isEmpty(encoding)) {
        encoding = encoding.toLowerCase();
      }

      switch (encoding.toLowerCase(Locale.ENGLISH)) {
      case "obsbyte":
        encoders.add(new IBKDefaultEncoder());

        ByteBuf[] obsbyteEnd = new ByteBuf[] { Unpooled.wrappedBuffer(new byte[] { '@', '@' }) };
        decoders.add(
            ChannelHandlerFactories.newDelimiterBasedFrameDecoder(NETTY_MAX_BUFFER_SIZE, obsbyteEnd, false, "tcp"));
        decoders.add(new IBKDefaultDecoder());
        break;
      case "delimiter":
        encoders.add(new IBKDefaultEncoder());

        ByteBuf[] delimiterEnd = new ByteBuf[] { Unpooled.wrappedBuffer(new byte[] { '@', 'E', 'O', 'F' }) };
        decoders.add(
            ChannelHandlerFactories.newDelimiterBasedFrameDecoder(NETTY_MAX_BUFFER_SIZE, delimiterEnd, false, "tcp"));
        decoders.add(new IBKDefaultDecoder());
        break;
      case "fixed":
        encoders.add(new IBKDefaultEncoder());

        if (option.get(EndpointCode.LENGTH_LEN) != null) {
          Integer lengthLen = Integer.parseInt(option.get(EndpointCode.LENGTH_LEN).toString());

          decoders.add(new FixedLengthFrameDecoder(lengthLen));
        }
        decoders.add(new IBKDefaultDecoder());
        break;
      default:
        if (option.get(EndpointCode.LENGTH_LEN) != null) {
          Integer lengthOffset = option.get(EndpointCode.LENGTH_OFFSET) != null
              ? Integer.parseInt(option.get(EndpointCode.LENGTH_OFFSET).toString())
              : 0;
          Integer lengthLen = Integer.parseInt(option.get(EndpointCode.LENGTH_LEN).toString());

          encoders.add(new LengthFieldPrepender(lengthLen));
          decoders.add(ChannelHandlerFactories.newLengthFieldBasedFrameDecoder(NETTY_MAX_BUFFER_SIZE, lengthOffset,
              lengthLen, lengthOffset, lengthLen));
        }
        encoders.add(new IBKDefaultEncoder());
        decoders.add(new IBKDefaultDecoder());
        break;
      }
    }

    // 코덱정보 없을 때
    if (option == null) {
      encoders.add(new IBKDefaultEncoder());
      decoders.add(new IBKDefaultDecoder());
    }
  }
}
