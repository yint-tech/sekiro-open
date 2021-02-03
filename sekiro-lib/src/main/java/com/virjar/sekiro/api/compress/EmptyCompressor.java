package com.virjar.sekiro.api.compress;

import java.io.IOException;

public class EmptyCompressor implements Compressor {
    @Override
    public String method() {
        return "none";
    }

    @Override
    public byte[] compress(byte[] input) throws IOException {
        return input;
    }

    @Override
    public byte[] decompress(byte[] input) throws IOException {
        return input;
    }
}
