package com.virjar.sekiro.api.databind;

import com.virjar.sekiro.api.SekiroRequest;
import com.virjar.sekiro.api.SekiroRequestHandler;

import java.lang.reflect.Constructor;

/**
 * Created by virjar on 2019/1/25.<br>
 * InvokeRequestConstructorActionRequestHandlerCreateHelper
 */

public class ICRCreateHelper implements ActionRequestHandlerGenerator {
    private Constructor<? extends SekiroRequestHandler> theConstructor;

    public ICRCreateHelper(Constructor<? extends SekiroRequestHandler> theConstructor) {
        this.theConstructor = theConstructor;
    }

    @Override
    public SekiroRequestHandler gen(SekiroRequest invokeRequest) {
        try {
            return theConstructor.newInstance(invokeRequest);
        } catch (Exception e) {
            // not happen
            throw new IllegalStateException(e);
        }
    }
}
