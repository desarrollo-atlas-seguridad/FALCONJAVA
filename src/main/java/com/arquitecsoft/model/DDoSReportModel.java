package com.arquitecsoft.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DDoSReportModel {
    private String host;                            // Objetivo a atacar
    private Date timeStarted;                       // Inicio de la ejecucion
    private Date timeFinished;                      // Fin de la ejecucion
    private long attacksCountTotalRequested;        // Se solicitan x ataques en y intentos y en z hilos
    private long attacksTimeTotalRequested;         // Se solicitan x ataques en t tiempo y en z hilos
    private long attacksCountTotalDone;             // Se registran el total de ciclos por cada hilo (x*z)
    private long attacksCountTotalSuccess;          // Cantidad de exitos (En caso de ser exitoso cada hilo reporta 1, el total debería ser z en su mejor comportamiento)
    private List<Long> attacksSuccess;              // Codigos de hilo de los procesos exitosos
    private List<AttributeClass> finalCodes;        // Listado de codigos http status de cada hilo (lista de z elementos)
    private List<AttributeClass> finalResponses;    // Listado de respuesta del body de cada hilo (lista de z elementos)
    private List<AttributeClass> finalTimes;        // Listado de tiempo de ejecucion en milisegundos de cada hilo (lista de z elementos)
    private List<AttributeClass> finalCicles;       // Listado de ciclos de ejecucion de cada hilo (lista de z elementos)
    private List<AttributeClass> finalMgs;          // Listado de mensajes de cada hilo (lista de z elementos)

    public DDoSReportModel() {
        this.timeStarted = new Date();
    }

    public DDoSReportModel(String host, long attacksCountTotalRequested, long attacksTimeTotalRequested) {
        this.timeStarted = new Date();
        this.host = host;
        this.attacksCountTotalRequested = attacksCountTotalRequested;
        this.attacksTimeTotalRequested = attacksTimeTotalRequested;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public long getAttacksTimeTotalRequested() {
        return attacksTimeTotalRequested;
    }

    public void setAttacksTimeTotalRequested(long attacksTimeTotalRequested) {
        this.attacksTimeTotalRequested = attacksTimeTotalRequested;
    }

    public long getAttacksCountTotalRequested() {
        return attacksCountTotalRequested;
    }

    public void setAttacksCountTotalRequested(long attacksCountTotalRequested) {
        this.attacksCountTotalRequested = attacksCountTotalRequested;
    }

    public long getAttacksCountTotalDone() {
        return attacksCountTotalDone;
    }

    public void setAttacksCountTotalDone(long attacksCountTotalDone) {
        this.attacksCountTotalDone = attacksCountTotalDone;
    }

    public long getAttacksCountTotalSuccess() {
        return attacksCountTotalSuccess;
    }

    public void setAttacksCountTotalSuccess(long attacksCountTotalSuccess) {
        this.attacksCountTotalSuccess = attacksCountTotalSuccess;
    }

    public Date getTimeStarted() {
        return timeStarted;
    }

    public void setTimeStarted(Date timeStarted) {
        this.timeStarted = timeStarted;
    }

    public Date getTimeFinished() {
        return timeFinished;
    }

    public void setTimeFinished(Date timeFinished) {
        this.timeFinished = timeFinished;
    }

    public List<AttributeClass> getFinalCodes() {
        if (finalCodes == null) {
            finalCodes = new ArrayList<>();
        }
        return finalCodes;
    }

    public List<AttributeClass> getFinalResponses() {
        if (finalResponses == null) {
            finalResponses = new ArrayList<>();
        }
        return finalResponses;
    }

    public List<AttributeClass> getFinalTimes() {
        if (finalTimes == null) {
            finalTimes = new ArrayList<>();
        }
        return finalTimes;
    }

    public List<AttributeClass> getFinalMgs() {
        if (finalMgs == null) {
            finalMgs = new ArrayList<>();
        }
        return finalMgs;
    }

    public List<AttributeClass> getFinalCicles() {
        if (finalCicles == null) {
            finalCicles = new ArrayList<>();
        }
        return finalCicles;
    }

    public List<Long> getAttacksSuccess() {
        if (attacksSuccess == null) {
            attacksSuccess = new ArrayList<>();
        }
        return attacksSuccess;
    }
}
