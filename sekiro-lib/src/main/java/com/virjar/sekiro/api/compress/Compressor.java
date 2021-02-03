package com.virjar.sekiro.api.compress;

import java.io.IOException;

/**
 * 数据压缩传输
 */
public interface Compressor {
    /**
     * 压缩算法
     *
     * @return gzip\Snappy\等
     */
    String method();

    byte[] compress(byte[] input) throws IOException;

    byte[] decompress(byte[] input) throws IOException;
}
