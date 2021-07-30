package com.arquitecsoft.model;

import java.util.ArrayList;
import java.util.List;

public class ExecutorBrowserModel {
    private int pid;
    private int idPending;
    private String driverLocation;
    private String downloadLocation;
    private boolean sendOutput;
    private int maxIterations;
    private List<ExecutorCommandModel> commands;
    private String timeSleep;
    public ExecutorBrowserModel() {
        this.sendOutput = true;
        this.commands = new ArrayList<>();
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
    public String getDriverLocation() {
        return driverLocation;
    }
    public void setDriverLocation(String driverLocation) {
        this.driverLocation = driverLocation;
    }
    public String getDownloadLocation() {
        return downloadLocation;
    }
    public void setDownloadLocation(String downloadLocation) {
        this.downloadLocation = downloadLocation;
    }

    public String getTimeSleep() {
        return timeSleep;
    }

    public void setTimeSleep(String timeSleep) {
        this.timeSleep = timeSleep;
    }
}
