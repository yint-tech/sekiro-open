package com.virjar.sekiro.api.compress;

import org.xerial.snappy.Snappy;

import java.io.IOException;

public class SnappyCompressor implements Compressor {
    @Override
    public String method() {
        return "Snappy";
    }

    @Override
    public byte[] compress(byte[] input) throws IOException {
        return Snappy.compress(input);
    }

    @Override
    public byte[] decompress(byte[] input) throws IOException {
        return Snappy.uncompress(input);
    }
}
