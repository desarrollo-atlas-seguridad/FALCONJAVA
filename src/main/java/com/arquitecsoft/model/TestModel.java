package com.arquitecsoft.model;

public class TestModel {
    private int att1;
    private String seccondAtt;

    public TestModel() {
    }

    public TestModel(int att1, String seccondAtt) {
        this.att1 = att1;
        this.seccondAtt = seccondAtt;
    }

    public int getAtt1() {
        return att1;
    }

    public void setAtt1(int att1) {
        this.att1 = att1;
    }

    public String getSeccondAtt() {
        return seccondAtt;
    }

    public void setSeccondAtt(String seccondAtt) {
        this.seccondAtt = seccondAtt;
    }
}
