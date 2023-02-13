package cn.iinti.sekiro3.open.framework.trace;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个事件
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Event {
    /**
     * 发生时间
     */
    private long timestamp;
    /**
     * 事件消息
     */
    private String message;

    /**
     * 异常，如果存在
     */
    private Throwable throwable;

}
