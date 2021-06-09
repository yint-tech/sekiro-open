package com.virjar.sekiro.business.netty.routers.client;

import com.virjar.sekiro.business.api.core.Context;

public class ReplaceableContext extends Context {
    private Context context;

    public ReplaceableContext() {
        super(null);
    }

    public void setupParent(Context context) {
        this.context = context;

    }

    @Override
    public String getClientId() {
        if (context == null) {
            return "unknown";
        }
        return context.getClientId();
    }

    @Override
    public String getSekiroGroup() {
        if (context == null) {
            return "unknown";
        }
        return context.getSekiroGroup();
    }
}
