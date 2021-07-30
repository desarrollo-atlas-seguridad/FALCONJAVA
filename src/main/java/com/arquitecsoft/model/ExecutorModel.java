package com.arquitecsoft.model;

import java.util.ArrayList;
import java.util.List;

public class ExecutorModel {
    private int pid;
    private int idPending;
    private String host;
    private String user;
    private String password;
    private String ruteResponse;
    private boolean sendOutput;
    private int maxIterations;
    private List<ExecutorCommandModel> commands;

    public ExecutorModel() {
        this.sendOutput = true;
        this.commands = new ArrayList<>();
    }

    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public String getUser() {
        return user;
    }
    public void setUser(String user) {
        this.user = user;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public List<ExecutorCommandModel> getCommands() {
        return commands;
    }
    public void setCommands(List<ExecutorCommandModel> commands) {
        this.commands = commands;
    }
    public void addCommand(ExecutorCommandModel command){
        this.commands.add(command);
    }
    public ExecutorCommandModel getCommand(int pos){
        return this.commands.get(pos);
    }
    public void removeCommand(int pos){
        this.commands.remove(pos);
    }
    public int getPid() {
        return pid;
    }
    public void setPid(int pid) {
        this.pid = pid;
    }
    public boolean isSendOutput() {
        return sendOutput;
    }
    public void setSendOutput(boolean sendOutput) {
        this.sendOutput = sendOutput;
    }
    public int getIdPending() {
        return idPending;
    }
    public void setIdPending(int idPending) {
        this.idPending = idPending;
    }
    public int getMaxIterations() {
        return maxIterations;
    }
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }   
    public String getRuteResponse() {
        return ruteResponse;
    }

    public void setRuteResponse(String ruteResponse) {
        this.ruteResponse = ruteResponse;
    }

}
