package cn.xbb.netty.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * @author : xbbGithub
 * @date : Created in 2019/11/8
 * @description : 初始哈加载netty客户端
 **/
@Component
public class NettyClientListener implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    private NettyClient nettyClient;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        if (null == contextRefreshedEvent.getApplicationContext().getParent()) {
            nettyClient.start();
        }
    }
}
