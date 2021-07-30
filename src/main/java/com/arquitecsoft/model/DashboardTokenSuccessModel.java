package com.arquitecsoft.model;

public class DashboardTokenSuccessModel {
    private String token;
    private int id;
    private String username;
    private String name;
    private String avatar;
    private String created_at;
    private String updated_at;

    public DashboardTokenSuccessModel(String token) {
        this.token = token;
    }

    public DashboardTokenSuccessModel(String token, int id, String username, String name, String avatar, String created_at, String updated_at) {
        this.token = token;
        this.id = id;
        this.username = username;
        this.name = name;
        this.avatar = avatar;
        this.created_at = created_at;
        this.updated_at = updated_at;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getCreated_at() {
        return created_at;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }

    public String getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(String updated_at) {
        this.updated_at = updated_at;
    }
}
