package cn.xbb.netty.client;

import cn.xbb.netty.core.coder.RpcDecoder;
import cn.xbb.netty.core.coder.RpcEncoder;
import cn.xbb.netty.core.model.RequestDomain;
import cn.xbb.netty.core.model.ResponseDomain;
import cn.xbb.netty.core.serialize.ProtoBufSerializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author : xbbGithub
 * @date : Created in 2019/11/8
 * @description : netty的客户端
 **/
@Slf4j
@Component
public class NettyClient {

    private static final String IP = "127.0.0.1";
    private static final Integer SERVER_PORT = 9999;


    /**
     * 通过nio方式来接收连接和处理连接
     */
    private EventLoopGroup workGroup = new NioEventLoopGroup();
    /**
     * 唯一标记
     */
    private boolean initFlag = true;


    void start() {
        doConnect(new Bootstrap(), workGroup);
    }

    private void doConnect(Bootstrap bootstrap, EventLoopGroup workGroup) {
        try {
            if (bootstrap != null) {
                bootstrap.group(workGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new RpcChannelInitializer())
                        .option(ChannelOption.SO_KEEPALIVE, true);

                ChannelFuture channelFuture = bootstrap.connect(new InetSocketAddress(IP, SERVER_PORT));
                channelFuture.addListener((ChannelFutureListener) future -> {
                    final EventLoop eventLoop = future.channel().eventLoop();
                    if (!future.isSuccess()) {
                        log.info("客户端重连, reconnect after 10s!");
                        eventLoop.schedule(() -> doConnect(new Bootstrap(), eventLoop), 10, TimeUnit.SECONDS);
                    }
                });

                if (initFlag) {
                    log.info("netty client start success!");
                    initFlag = false;
                }
                // 阻塞
                channelFuture.channel().closeFuture().sync();
            }
        } catch (Exception e) {
            log.error("Client connection failed:{}", e.getMessage());
        }

    }

    private static class RpcChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline cp = ch.pipeline();
            //设置心跳时间15s
            cp.addLast(new IdleStateHandler(0, 0, 15, TimeUnit.SECONDS));
            cp.addLast(new HeartbeatHandler());
            //编码
            cp.addLast(new RpcEncoder(RequestDomain.class, new ProtoBufSerializer()));
            //解码
            cp.addLast(new RpcDecoder(ResponseDomain.class, new ProtoBufSerializer()));
            cp.addLast(new RpcClientResponseHandler());
        }
    }

    @Slf4j
    private static class HeartbeatHandler extends ChannelInboundHandlerAdapter {
        private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("HEARTBEAT", StandardCharsets.UTF_8));

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
            log.info("检测到服务端心跳:{}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            if (obj instanceof IdleStateEvent) {
                //当捕获到 IdleStateEvent，发送心跳到远端，并添加一个监听器，如果发送失败就关闭服务端连接
                ctx.writeAndFlush(HEARTBEAT_SEQUENCE.duplicate()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                //如果捕获到的时间不是一个 IdleStateEvent，就将该事件传递到下一个处理器
                super.userEventTriggered(ctx, obj);
            }
        }

    }

    @ChannelHandler.Sharable
    private static class RpcClientResponseHandler extends SimpleChannelInboundHandler<ResponseDomain> {

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ResponseDomain msg) throws Exception {
            try {
                log.info("收到服务端发送的数据 {}", msg);
                ResponseDomain responseDomain = ResponseDomain.builder().demo("我是客户端channelRead0").build();
                ctx.writeAndFlush(responseDomain);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("rpc client caught exception", cause);
            ctx.close();
        }

        /**
         * 在到服务器的连接已经建立之后将被调用
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("客户端与服务端已建立连接");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            log.info("{}:下线", channel.remoteAddress());
            final EventLoop eventLoop = channel.eventLoop();
            eventLoop.schedule(() -> new NettyClient().doConnect(new Bootstrap(), new NioEventLoopGroup()), 10, TimeUnit.SECONDS);
        }
    }

}