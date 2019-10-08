package com.virjar.sekiro.netty.protocol;

import java.util.Arrays;

/**
 * 代理客户端与代理服务器消息交换协议
 *
 * @author fengfei
 */
public class SekiroNatMessage {

    /**
     * 心跳消息
     */
    public static final byte TYPE_HEARTBEAT = 0x07;

    /**
     * 设备注册到服务器
     */
    public static final byte C_TYPE_REGISTER = 0x01;

    /**
     * herems的invoke转发
     */
    public static final byte TYPE_INVOKE = 0x02;


    /**
     * 消息类型
     */
    private byte type;

    /**
     * 消息流水号
     */
    private long serialNumber;

    /**
     * 消息命令请求信息
     */
    private String extra;

    /**
     * 消息传输数据
     */
    private byte[] data;

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getExtra() {
        return extra;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getTypeReadable() {

        if (type == C_TYPE_REGISTER) {
            return "C_TYPE_REGISTER";
        }

        if (type == TYPE_HEARTBEAT) {
            return "TYPE_HEARTBEAT";
        }

        if (type == TYPE_INVOKE) {
            return "TYPE_INVOKE";
        }
        return "TYPE_UNKNOWN_" + type;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(long serialNumber) {
        this.serialNumber = serialNumber;
    }

    @Override
    public String toString() {
        return "ProxyMessage [type=" + type + ", serialNumber=" + serialNumber + ", extra=" + extra + ", data=" + Arrays.toString(data) + "]";
    }


}
