package com.virjar.sekiro.api.databind;


/**
 * Created by virjar on 2019/1/25.<br>
 * exception when transfer a value to bind field
 */
public class DataParseFailedException extends RuntimeException {

    private String attributeName;

    private Exception exception;


    public DataParseFailedException(String attributeName, Exception exception) {
        this.attributeName = attributeName;
        this.exception = exception;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public Exception getException() {
        return exception;
    }
}
