package com.virjar.sekiro.api.databind;


import com.virjar.sekiro.api.CommonRes;
import com.virjar.sekiro.api.SekiroRequest;
import com.virjar.sekiro.api.SekiroRequestHandler;
import com.virjar.sekiro.api.SekiroResponse;
import com.virjar.sekiro.utils.Defaults;
import com.virjar.sekiro.utils.ReflectUtil;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Created by virjar on 2019/1/25.<br>
 * create a actionRequestHandler with capability of auto inject attribute
 */

public class FieldBindGenerator implements ActionRequestHandlerGenerator {

    private Map<Field, FileBindHandler> fileBindHandlerMap;
    private ActionRequestHandlerGenerator instanceCreateHelper;
    private Map<Field, Object> copyFiledMap;

    public FieldBindGenerator(List<Field> autoBindFields, ActionRequestHandlerGenerator instanceCreateHelper, Map<Field, Object> copyFiledMap) {
        this.fileBindHandlerMap = toBindHandler(autoBindFields);
        this.instanceCreateHelper = instanceCreateHelper;
        this.copyFiledMap = copyFiledMap;
    }

    private Map<Field, FileBindHandler> toBindHandler(List<Field> autoBindFields) {
        Map<Field, FileBindHandler> fileBindHandlerMap = new HashMap<>();
        for (Field field : autoBindFields) {
            AutoBind fieldAnnotation = field.getAnnotation(AutoBind.class);
            Object defaultValue = null;
            if (fieldAnnotation != null) {
                Class<?> wrapperType = ReflectUtil.primitiveToWrapper(field.getType());
                if (wrapperType == String.class) {
                    defaultValue = fieldAnnotation.defaultStringValue();
                } else if (wrapperType == Integer.class) {
                    defaultValue = fieldAnnotation.defaultIntValue();
                } else if (wrapperType == Double.class) {
                    defaultValue = fieldAnnotation.defaultDoubleValue();
                } else if (wrapperType == Boolean.class) {
                    defaultValue = fieldAnnotation.defaultBooleanValue();
                } else if (wrapperType == Long.class) {
                    defaultValue = fieldAnnotation.defaultLongValue();
                }
            }
            fileBindHandlerMap.put(field, new FileBindHandler(defaultValue, field, fieldAnnotation));
        }
        return fileBindHandlerMap;
    }

    private void bindFiled(SekiroRequest invokeRequest, SekiroRequestHandler actionRequestHandler) {
        for (Map.Entry<Field, FileBindHandler> entry : fileBindHandlerMap.entrySet()) {
            Field key = entry.getKey();
            Object value = entry.getValue().transfer(invokeRequest);
            if (value != null) {
                try {
                    key.set(actionRequestHandler, value);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        for (Map.Entry<Field, Object> entry : copyFiledMap.entrySet()) {
            Field key = entry.getKey();
            try {
                Object o = key.get(actionRequestHandler);
                if (o == null || isPrimitiveDefault(key.getType(), o)) {
                    key.set(actionRequestHandler, entry.getValue());
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean isPrimitiveDefault(Class type, Object object) {
        if (!type.isPrimitive()) {
            Class<?> theUnwrapType = ReflectUtil.wrapperToPrimitive(type);
            if (theUnwrapType == null) {
                return false;
            }
            type = theUnwrapType;
        }
        return Defaults.defaultValue(type) == object;
    }

    @Override
    public SekiroRequestHandler gen(SekiroRequest invokeRequest) {
        SekiroRequestHandler gen = instanceCreateHelper.gen(invokeRequest);
        try {
            bindFiled(invokeRequest, gen);
        } catch (final RuntimeException e) {
            if (e instanceof ParamNotPresentException) {
                return new SekiroRequestHandler() {

                    @Override
                    public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {
                        sekiroResponse.failed("the param: {" + ((ParamNotPresentException) e).getAttributeName() + "} not presented");
                    }
                };
            }
            if (e instanceof DataParseFailedException) {
                return new SekiroRequestHandler() {
                    @Override
                    public void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse) {
                        DataParseFailedException dataParseFailedException = (DataParseFailedException) e;
                        sekiroResponse.failed(CommonRes.statusBadRequest, dataParseFailedException);
                    }
                };
            }
            throw e;
        }
        return gen;
    }
}