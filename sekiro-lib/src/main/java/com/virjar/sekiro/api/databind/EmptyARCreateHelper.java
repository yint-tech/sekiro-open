package com.virjar.sekiro.api.databind;

import com.virjar.sekiro.api.SekiroRequest;
import com.virjar.sekiro.api.SekiroRequestHandler;

/**
 * Created by virjar on 2019/1/25.<br>
 * emptyActionRequestHandlerCreateHelper
 */

public class EmptyARCreateHelper implements ActionRequestHandlerGenerator {
    private Class<? extends SekiroRequestHandler> actionRequestHandlerClass;

    public EmptyARCreateHelper(Class<? extends SekiroRequestHandler> actionRequestHandlerClass) {
        this.actionRequestHandlerClass = actionRequestHandlerClass;
    }

    @Override
    public SekiroRequestHandler gen(SekiroRequest invokeRequest) {
        try {
            return actionRequestHandlerClass.newInstance();
        } catch (Exception e) {
            // not happen
            throw new IllegalStateException(e);
        }
    }
}
