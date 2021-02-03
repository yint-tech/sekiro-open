package com.virjar.sekiro.api.compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressor implements Compressor {
    @Override
    public String method() {
        return "gzip";
    }

    @Override
    public byte[] compress(byte[] input) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
        gzipOutputStream.write(input);
        gzipOutputStream.flush();
        gzipOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    public byte[] decompress(byte[] input) throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(input);
        GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];
        int n;
        while ((n = gzipInputStream.read(buff)) > 0) {
            byteArrayOutputStream.write(buff, 0, n);
        }
        return byteArrayOutputStream.toByteArray();

    }
}
