package cn.xbb.netty.core.serialize;

/**
 * @author : xbbGithub
 * @date : Created in 2019/11/8
 * @description : 序列化接口
 **/
public interface Serializer  {
    /**
     * 把对象转换成byte数组
     * @param obj 对象
     * @return Object
     * */
    public abstract <T> byte[] serialize(T obj);

    /**
     * 把数组与类 转换成一个对象
     * @param bytes byte数组
     * @param clazz 类
     * @param <T> 对象
     * @return Object
     */
    public abstract <T> Object deserialize(byte[] bytes, Class<T> clazz);
}
