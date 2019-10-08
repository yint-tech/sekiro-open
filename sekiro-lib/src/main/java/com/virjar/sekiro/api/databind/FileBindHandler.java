package com.virjar.sekiro.api.databind;

import com.virjar.sekiro.api.SekiroRequest;


import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

import external.com.alibaba.fastjson.JSONException;
import external.com.alibaba.fastjson.JSONObject;
import external.com.alibaba.fastjson.parser.ParserConfig;
import external.com.alibaba.fastjson.util.TypeUtils;

/**
 * Created by virjar on 2019/1/25.<br>
 */

public class FileBindHandler {
    private String attributeName = null;
    private Object defaultValue = null;
    private boolean notNull;
    private Field targetFiled;

    public FileBindHandler(Object defaultValue, Field targetFiled, AutoBind autoBind) {
        this.defaultValue = defaultValue;
        this.targetFiled = targetFiled;
        if (autoBind != null) {
            notNull = autoBind.require();
            attributeName = autoBind.value();
        }
        if (attributeName == null || attributeName.trim().isEmpty()) {
            attributeName = targetFiled.getName();
        }
    }

    public Object transfer(SekiroRequest invokeRequest) {
        Object value = transferInternal(invokeRequest);
        if (notNull && value == null) {
            throw new ParamNotPresentException(attributeName);
        }
        return value;
    }

    private Object transferInternal(SekiroRequest invokeRequest) {
        if (targetFiled.getType() == String.class) {
            return invokeRequest.getString(attributeName, (String) defaultValue);
        }
        invokeRequest.getString("insureModelParsed");
        JSONObject jsonModel = invokeRequest.getJsonModel();
        if (jsonModel != null) {
            Object fieldValue = jsonModel.get(attributeName);
            if (fieldValue == null) {
                return defaultValue;
            }
            return TypeUtils.cast(fieldValue, targetFiled.getType(), ParserConfig.getGlobalInstance());
        }
        if (Collection.class.isAssignableFrom(targetFiled.getType()) || targetFiled.getType().isArray()) {
            //target is a collection
            List<String> valueCollections = invokeRequest.getNameValuePairsModel().get(attributeName);
            if (valueCollections == null) {
                return defaultValue;
            }
            try {
                return TypeUtils.cast(valueCollections, targetFiled.getType(), ParserConfig.getGlobalInstance());
            } catch (RuntimeException e) {
                throw new DataParseFailedException(attributeName, e);
            }
        }
        String stringValue = invokeRequest.getString(attributeName);
        if (stringValue == null) {
            return defaultValue;
        }
        try {
            return TypeUtils.cast(stringValue, targetFiled.getType(), ParserConfig.getGlobalInstance());
        } catch (JSONException e) {
            stringValue = stringValue.trim();
            try {
                Object jsonLikeObj = null;

                if (stringValue.startsWith("[") || stringValue.startsWith("{")) {
                    jsonLikeObj = JSONObject.parse(stringValue);
                }
                if (jsonLikeObj != null) {
                    return TypeUtils.cast(jsonLikeObj, targetFiled.getType(), ParserConfig.getGlobalInstance());
                }
            } catch (Exception e1) {
                //ignore
            }
            throw new DataParseFailedException(attributeName, e);
        }
    }
}
