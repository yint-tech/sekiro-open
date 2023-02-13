package cn.iinti.sekiro3.open.utils;


import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ConsistentHashUtil {
    private static final int seed = 0x1234ABCD;
    private static final long m = 0xc6a4a7935bd1e995L;
    private static final int r = 47;

    public static long murHash(String key) {

        ByteBuffer buf = ByteBuffer.wrap(key.getBytes());

        ByteOrder byteOrder = buf.order();
        buf.order(ByteOrder.LITTLE_ENDIAN);
        long h = seed ^ (buf.remaining() * m);
        long k;
        while (buf.remaining() >= 8) {
            k = buf.getLong();

            k *= m;
            k ^= k >>> r;
            k *= m;

            h ^= k;
            h *= m;
        }

        if (buf.remaining() > 0) {
            ByteBuffer finish = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            finish.put(buf);
            // fix： 强转是必要的，这里有一个jdk的bug,jdk11编译后运行在jdk1.8将会触发这个bug，请注意不要使用ide提示优化
            // https://github.com/hazelcast/hazelcast/issues/14214
            ((Buffer) finish).rewind();
            h ^= finish.getLong();
            h *= m;
        }

        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;

        buf.order(byteOrder);
        return h;
    }

}
