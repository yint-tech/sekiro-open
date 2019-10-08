package com.virjar.sekiro.api.databind;


import com.virjar.sekiro.api.SekiroRequest;
import com.virjar.sekiro.api.SekiroRequestHandler;

/**
 * Created by virjar on 2019/1/25.<br>
 * create a handler  to deal with a certain request,create and auto bind value
 */
public interface ActionRequestHandlerGenerator {
    SekiroRequestHandler gen(SekiroRequest invokeRequest);
}
