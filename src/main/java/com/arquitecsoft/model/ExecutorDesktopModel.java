package com.arquitecsoft.model;

import java.util.ArrayList;
import java.util.List;

public class ExecutorDesktopModel {

    private int pid;
    private int idPending;
    private boolean sendOutput;
    private int maxIterations;
    private String driverLocation;
    private String timeSleep;

   
    private List<ExecutorCommandModel> commands;

    public ExecutorDesktopModel() {
        this.sendOutput = true;
        this.commands = new ArrayList<>();
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public int getIdPending() {
        return idPending;
    }

    public void setIdPending(int idPending) {
        this.idPending = idPending;
    }

    public boolean isSendOutput() {
        return sendOutput;
    }

    public void setSendOutput(boolean sendOutput) {
        this.sendOutput = sendOutput;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public List<ExecutorCommandModel> getCommands() {
        return commands;
    }

    public void setCommands(List<ExecutorCommandModel> commands) {
        this.commands = commands;
    }
     public String getDriverLocation() {
        return driverLocation;
    }

    public void setDriverLocation(String driverLocation) {
        this.driverLocation = driverLocation;
    }

    public String getTimeSleep() {
        return timeSleep;
    }

    public void setTimeSleep(String timeSleep) {
        this.timeSleep = timeSleep;
    }
}
