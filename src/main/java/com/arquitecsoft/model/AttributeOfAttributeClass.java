package com.arquitecsoft.model;

import java.util.ArrayList;
import java.util.List;

public class AttributeOfAttributeClass {
    private String value;
    private List<AttributeClass> finalResponses;

    public AttributeOfAttributeClass() {
    }

    public AttributeOfAttributeClass(String value) {
        this.value = value;
    }

    public AttributeOfAttributeClass(String value, List<AttributeClass> finalResponses) {
        this.value = value;
        this.finalResponses = finalResponses;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public List<AttributeClass> getFinalResponses() {
        if (finalResponses == null) {
            finalResponses = new ArrayList<>();
        }
        return finalResponses;
    }
}
