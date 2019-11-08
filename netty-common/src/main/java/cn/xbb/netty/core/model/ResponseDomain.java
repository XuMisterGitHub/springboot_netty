package cn.xbb.netty.core.model;

import lombok.*;

/**
 * @author : xbbGithub
 * @date : Created in 2019/11/8
 * @description : 返回域
 **/
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@ToString
public class ResponseDomain {
    private String requestId;
    private Exception exception;
    private Object result;
    private Integer state;
    private String demo;

    public boolean hasException() {
        return exception != null;
    }
}
