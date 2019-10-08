package com.virjar.sekiro.api.databind;

/**
 * Created by virjar on 2019/1/25.<br>
 * assert if some param not presented
 */

public class ParamNotPresentException extends RuntimeException {
    private String attributeName;

    public ParamNotPresentException(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeName() {
        return attributeName;
    }
}
