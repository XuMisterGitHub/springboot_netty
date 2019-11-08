package cn.xbb.netty.server;

import cn.xbb.netty.core.coder.RpcDecoder;
import cn.xbb.netty.core.coder.RpcEncoder;
import cn.xbb.netty.core.model.RequestDomain;
import cn.xbb.netty.core.model.ResponseDomain;
import cn.xbb.netty.core.serialize.ProtoBufSerializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author : xbbGithub
 * @date : Created in 2019/11/8
 * @description : netty服务端
 **/
@Component
@Slf4j
public class NettyServer {

    private static EventLoopGroup bossGroup = new NioEventLoopGroup();
    private static EventLoopGroup workGroup = new NioEventLoopGroup();
    private static ServerBootstrap serverBootstrap = new ServerBootstrap();

    private static ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    //自定义channel管理对象
    private static ConcurrentHashMap<String, Channel> channelMap = new ConcurrentHashMap<>();

    void start() {
        try {
            serverBootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new RpcChannelInitializer())
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture channelFuture = serverBootstrap.bind(9999).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            workGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    private static class RpcChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline cp = ch.pipeline();
            //心跳机制15s
            cp.addLast(new IdleStateHandler(0, 0, 15, TimeUnit.SECONDS));
            cp.addLast(new HeartbeatHandler());
            cp.addLast(new RpcDecoder(RequestDomain.class, new ProtoBufSerializer()));
            cp.addLast(new RpcEncoder(ResponseDomain.class, new ProtoBufSerializer()));
            cp.addLast(new RpcNettyServerHandler());
        }
    }

    @ChannelHandler.Sharable
    private static class RpcNettyServerHandler extends SimpleChannelInboundHandler<RequestDomain> {

        @Override
        public void channelRead0(ChannelHandlerContext ctx, RequestDomain msg) throws Exception {
            log.info(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " 接收到消息：" + msg);
            ResponseDomain responseDomain = ResponseDomain.builder().demo("我是服务端channelRead0").build();
            ctx.writeAndFlush(responseDomain);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("服务端出现异常", cause);
            ctx.close();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("服务端与客户端已建立连接");
            exeTask();
            exeTask2();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            log.info("{}:下线", channel.remoteAddress());
        }


        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            //建立连接时channelGroup将添加所有的channel
            Channel channel = ctx.channel();
            //1. 调用channelGroup的writeAndFlush
            //2. 其实就相当于channelGroup中的每个channel都writeAndFlush
            //3. 先去广播，再将自己加入到channelGroup中
            log.info("{}:加入", channel.remoteAddress());
            channelGroup.writeAndFlush(channel.remoteAddress() + "加入");
            channelGroup.add(channel);

            //4. map自定义存储客户端ip
            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            channelMap.put(socketAddress.getAddress().getHostAddress(), ctx.channel());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            log.info("{}:离开", channel.remoteAddress());
            //1. 验证一下每次客户端断开连接，连接自动地从channelGroup中删除调。
            //2. 当断开连接的时候,netty会自动调用: channelGroup.remove(channel)
            log.info("channelGroup的大小: {}", channelGroup.size());

            //3.自定义的channelMap需要手动的删除断开的channel
            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            channelMap.remove(socketAddress.getAddress().getHostAddress());
            log.info("channelMap的大小: {}", channelMap.size());


        }
    }

    private static class HeartbeatHandler extends ChannelInboundHandlerAdapter {
        private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("HEARTBEAT", StandardCharsets.UTF_8));

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
            log.info("检测到客户端心跳:{}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            if (obj instanceof IdleStateEvent) {
                ctx.writeAndFlush(HEARTBEAT_SEQUENCE.duplicate()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                super.userEventTriggered(ctx, obj);
            }
        }
    }

    /**
     * 模拟服务端主动推送消息
     */
    private static void exeTask() {
        if (null != channelGroup && channelGroup.size() > 0) {
            for (int i = 1; i < 11; i++) {
                ResponseDomain build = ResponseDomain.builder().demo("调度中心1，准备下发任务" + i).build();
                channelGroup.writeAndFlush(build);
            }
        }
    }

    private static void exeTask2() {
        String ip = "127.0.0.1";
        if (null != channelMap && channelMap.size() > 0) {
            for (int i = 1; i < 11; i++) {
                ResponseDomain response = ResponseDomain.builder().demo("调度中心2，准备下发任务" + i).build();
                for (Map.Entry<String, Channel> entry : channelMap.entrySet()) {
                    if (ip.equals(entry.getKey()) && entry.getValue().isActive()) {
                        entry.getValue().writeAndFlush(response);
                    }
                }
            }
        }
    }

}