package com.arquitecsoft.model;

public class DashboardTokenModel {
    private DashboardTokenSuccessModel success;

    public DashboardTokenModel(DashboardTokenSuccessModel success) {
        this.success = success;
    }

    public DashboardTokenSuccessModel getSuccess() {
        return success;
    }

    public void setSuccess(DashboardTokenSuccessModel success) {
        this.success = success;
    }
}
