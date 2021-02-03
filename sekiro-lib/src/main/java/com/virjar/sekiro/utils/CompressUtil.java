package com.virjar.sekiro.utils;

import com.virjar.sekiro.log.SekiroLogger;
import external.com.alibaba.fastjson.JSON;
import external.com.alibaba.fastjson.annotation.JSONField;
import org.xerial.snappy.Snappy;

import java.io.IOException;

public class CompressUtil {

    public static class CompressResponse {
        /**
         * 压缩、解压缩之前的 原始byte数组
         */
        private byte[] src;
        /**
         * 是否process 成功
         */
        private boolean success;

        public byte[] getSrc() {
            return src;
        }

        public void setSrc(byte[] src) {
            this.src = src;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }
    }

    public static class Extra {
        /**
         * 原始的context type
         */
        @JSONField(name = "ct")
        private String contextType;
        /**
         * 是否压缩
         */
        @JSONField(name = "compress")
        private boolean isCompress;

        /**
         * 只有这种格式才压缩
         *
         * @return
         */
        public boolean IsJsonContext() {
            return "application/json; charset=utf-8".equals(contextType);
        }

        public String getContextType() {
            return contextType;
        }

        public void setContextType(String contextType) {
            this.contextType = contextType;
        }

        public boolean isCompress() {
            return isCompress;
        }

        public void setCompress(boolean compress) {
            isCompress = compress;
        }

        @Override
        public String toString() {
            return "Extra{" +
                    "contextType='" + contextType + '\'' +
                    ", isCompress=" + isCompress +
                    '}';
        }
    }

    public static boolean canCompress(Extra extra) {
        return extra != null && extra.isCompress() && extra.IsJsonContext();
    }

    /**
     * 解析旧的contexttype -> 新的extra plus
     *
     * @param str
     * @return
     */
    public static Extra parseContextType(String str) {
        try {
            Extra extra = JSON.parseObject(str, Extra.class);
            return extra;
        } catch (Exception e) {
//            SekiroLogger.warn("parseContextType error: ", e);
            return null;
        }
    }

    /**
     * 压缩失败 返回原始的结构
     *
     * @param src
     * @return
     */
    public static CompressResponse compress(byte[] src) {
        CompressResponse response = new CompressResponse();
        try {
            byte[] compress = Snappy.compress(src);
            response.setSrc(compress);
            response.setSuccess(true);
        } catch (IOException e) {
            SekiroLogger.warn("compress error: ", e);
            response.setSrc(src);
            response.setSuccess(false);
        }

        return response;
    }


    /**
     * 解压失败 返回原始的结构
     *
     * @param src
     * @return
     */
    public static CompressResponse uncompress(byte[] src) {
        CompressResponse response = new CompressResponse();
        try {
            byte[] uncompress = Snappy.uncompress(src);
            response.setSrc(uncompress);
            response.setSuccess(true);
        } catch (IOException e) {
            SekiroLogger.warn("uncompress error: ", e);
            response.setSrc(src);
            response.setSuccess(false);
        }
        return response;
    }

}
