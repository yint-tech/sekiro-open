package com.virjar.sekiro;

import io.netty.util.AttributeKey;

public interface Constants {
    int READ_IDLE_TIME = 40;

    int WRITE_IDLE_TIME = 20;

    /**
     * max packet is 最大允许传输10M的数据.
     */
    int MAX_FRAME_LENGTH = 10 * 1024 * 1024;

    int LENGTH_FIELD_OFFSET = 0;

    int LENGTH_FIELD_LENGTH = 4;

    int INITIAL_BYTES_TO_STRIP = 0;

    int LENGTH_ADJUSTMENT = 0;

    int defaultNatServerPort = 5600;

    int defaultNatHttpServerPort = 5601;

    AttributeKey<String> CLIENT_KEY = AttributeKey.newInstance("client_key");
    AttributeKey<String> GROUP_KEY = AttributeKey.newInstance("goup_key");

    int protocolMagic = 5597;
}
