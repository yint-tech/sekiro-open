package com.virjar.sekiro.api;


import com.virjar.sekiro.log.SekiroLogger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import external.com.alibaba.fastjson.JSONArray;
import external.com.alibaba.fastjson.JSONException;
import external.com.alibaba.fastjson.JSONObject;

public class SekiroRequest {
    private byte[] requestData;

    private long serialNo;

    public SekiroRequest(byte[] requestData, long serialNo) {
        this.requestData = requestData;
        this.serialNo = serialNo;
    }

    private Multimap nameValuePairsModel;
    private JSONObject jsonModel;

    public String getString(String name) {
        return getString(name, null);
    }

    public String getString(String name, String defaultValue) {
        initInnerModel();
        if (nameValuePairsModel != null) {
            String ret = nameValuePairsModel.getString(name);
            return ret == null ? defaultValue : ret;
        }
        if (jsonModel != null) {
            String ret = jsonModel.getString(name);
            return ret == null ? defaultValue : ret;
        }
        throw new IllegalStateException("parameter parse failed");
    }

    public int getInt(String name) {
        return getInt(name, 0);
    }

    public int getInt(String name, int defaultValue) {
        initInnerModel();
        if (nameValuePairsModel != null) {
            String value = nameValuePairsModel.getString(name);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        if (jsonModel != null) {
            try {
                Integer value = jsonModel.getInteger(name);
                if (value == null) {
                    return defaultValue;
                }
                return value;
            } catch (JSONException e) {
                return defaultValue;
            }
        }
        throw new IllegalStateException("parameter parse failed");
    }

    public List<String> getValues(String name) {
        initInnerModel();
        if (nameValuePairsModel != null) {
            return nameValuePairsModel.get(name);
        }
        if (jsonModel != null) {
            Object o = jsonModel.get(name);
            List<String> ret = new ArrayList<>();

            if (o instanceof CharSequence) {
                ret.add(o.toString());
                return ret;
            } else if (o instanceof JSONArray) {
                JSONArray jsonArray = (JSONArray) o;
                int size = jsonArray.size();
                for (int i = 0; i < size; i++) {
                    Object item = jsonArray.get(i);
                    if (!(item instanceof CharSequence)) {
                        continue;
                    }
                    ret.add(item.toString());
                }

            } else {
                ret.add(jsonModel.getString(name));
            }
            return ret;
        }
        throw new IllegalStateException("parameter parse failed");
    }

    public boolean hasParam(String name) {
        initInnerModel();
        if (nameValuePairsModel != null) {
            return nameValuePairsModel.containsKey(name);
        }
        if (jsonModel != null) {
            return jsonModel.containsKey(name);
        }
        throw new IllegalStateException("parameter parse failed");
    }

    public JSONObject getJsonParam() {
        initInnerModel();
        return jsonModel;
    }


    private void initInnerModel() {
        if (nameValuePairsModel != null || jsonModel != null) {
            return;
        }
        synchronized (this) {
            if (nameValuePairsModel != null || jsonModel != null) {
                return;
            }
            if (requestData == null) {
                throw new IllegalArgumentException("invoke request can not be empty");
            }
            String paramContent = new String(requestData, StandardCharsets.UTF_8);
            paramContent = paramContent.trim();
            SekiroLogger.info("receive invoke request: " + paramContent + "  requestId: " + serialNo);
            if (paramContent.startsWith("{")) {
                try {
                    jsonModel = JSONObject.parseObject(paramContent);
                } catch (JSONException e) {
                    //ignore
                }
            }
            if (jsonModel != null) {
                return;
            }
            nameValuePairsModel = Multimap.parseUrlEncoded(paramContent);
        }
    }

    public Multimap getNameValuePairsModel() {
        return nameValuePairsModel;
    }

    public JSONObject getJsonModel() {
        return jsonModel;
    }

    public long getSerialNo() {
        return serialNo;
    }
}
