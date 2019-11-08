package cn.xbb.netty.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * @author : xbbGithub
 * @date : Created in 2019/11/8
 * @description : 初始化加载netty服务端
 **/
@Component
public class NettyServerListener implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    private NettyServer nettyServer;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        if (null == contextRefreshedEvent.getApplicationContext().getParent()) {
            nettyServer.start();
        }
    }
}
