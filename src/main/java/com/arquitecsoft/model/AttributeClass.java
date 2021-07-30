package com.arquitecsoft.model;

public class AttributeClass {
    private String key;
    private String value;
    private String msg;

    public AttributeClass() {
    }

    public AttributeClass(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public AttributeClass(String key, String value, String msg) {
        this.key = key;
        this.value = value;
        this.msg = msg;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
