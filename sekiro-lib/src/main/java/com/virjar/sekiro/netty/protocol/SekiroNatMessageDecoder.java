package com.virjar.sekiro.netty.protocol;

import com.virjar.sekiro.Constants;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class SekiroNatMessageDecoder extends ByteToMessageDecoder {

    private static final byte HEADER_SIZE = 4;

    private static final int TYPE_SIZE = 1;

    private static final int SERIAL_NUMBER_SIZE = 8;

    private static final int URI_LENGTH_SIZE = 1;


    protected SekiroNatMessage decode(ChannelHandlerContext ctx, ByteBuf in2) throws Exception {
        //  ByteBuf in = (ByteBuf) super.decode(ctx, in2);
        ByteBuf in = in2;
        if (in == null) {
            return null;
        }

        if (in.readableBytes() < HEADER_SIZE) {
            return null;
        }

//        int magic = in.readInt();
//        if (magic != Constants.protocolMagic) {
//            throw new IllegalStateException("invalid message");
//        }

        int frameLength = in.readInt();
        if (in.readableBytes() < frameLength) {
            return null;
        }
        SekiroNatMessage sekiroNatMessage = new SekiroNatMessage();
        byte type = in.readByte();
        long sn = in.readLong();

        sekiroNatMessage.setSerialNumber(sn);

        sekiroNatMessage.setType(type);

        byte uriLength = in.readByte();
        byte[] uriBytes = new byte[uriLength];
        in.readBytes(uriBytes);
        sekiroNatMessage.setExtra(new String(uriBytes));

        byte[] data = new byte[frameLength - TYPE_SIZE - SERIAL_NUMBER_SIZE - URI_LENGTH_SIZE - uriLength];
        in.readBytes(data);
        sekiroNatMessage.setData(data);

        in.release();

        return sekiroNatMessage;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (true) {
            if (in.readableBytes() < HEADER_SIZE) {
                return;
            }

            int frameLength = in.readInt();
            if (in.readableBytes() < frameLength) {
                return;
            }
            SekiroNatMessage sekiroNatMessage = new SekiroNatMessage();
            byte type = in.readByte();
            long sn = in.readLong();

            sekiroNatMessage.setSerialNumber(sn);

            sekiroNatMessage.setType(type);

            byte uriLength = in.readByte();
            byte[] uriBytes = new byte[uriLength];
            in.readBytes(uriBytes);
            sekiroNatMessage.setExtra(new String(uriBytes));

            byte[] data = new byte[frameLength - TYPE_SIZE - SERIAL_NUMBER_SIZE - URI_LENGTH_SIZE - uriLength];
            in.readBytes(data);
            sekiroNatMessage.setData(data);

            out.add(sekiroNatMessage);
            // in.release();

        }
    }
}