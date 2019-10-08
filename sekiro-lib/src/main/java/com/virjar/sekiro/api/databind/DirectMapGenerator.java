package com.virjar.sekiro.api.databind;

import com.virjar.sekiro.api.SekiroRequest;
import com.virjar.sekiro.api.SekiroRequestHandler;

/**
 * Created by virjar on 2019/1/25.<br>
 * do not need bind attribute，use singleton instance
 */
public class DirectMapGenerator implements ActionRequestHandlerGenerator {
    private SekiroRequestHandler delegate;

    public DirectMapGenerator(SekiroRequestHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public SekiroRequestHandler gen(SekiroRequest invokeReques) {
        return delegate;
    }
}