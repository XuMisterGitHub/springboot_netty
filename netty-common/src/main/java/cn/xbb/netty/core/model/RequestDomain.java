package cn.xbb.netty.core.model;

import lombok.*;

/**
 * @author : xbbGithub
 * @date : Created in 2019/11/8
 * @description : 请求域
 **/
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@ToString
public class RequestDomain {
    String name;
    String requestId;
    Integer age;
    Integer state;
    Integer nodePort;
    String nodeIp;
}
