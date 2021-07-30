package com.arquitecsoft.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class InjectionSQLReportModel {
    private String host;                                    // Objetivo a atacar (O archivo donde estan las x URLs a atacar)
    private Date timeStarted;                               // Inicio de la ejecucion
    private Date timeFinished;                              // Fin de la ejecucion
    private long attacksSitesRequested;                     // Cantidad z de registros en la biblioteca
    private long attacksCountTotalRequested;                // Cantidad z de registros en la biblioteca
    private long attacksCountTotalDone;                     // Se registran el total de ciclos por cada URL (x*z)
    private long attacksCountTotalSuccess;                  // Cantidad de exitos
    private List<AttributeOfAttributeClass> finalResponses; // Listado de respuestas exitosas

    public InjectionSQLReportModel() {
        this.timeStarted = new Date();
        this.attacksCountTotalRequested = 0;
        this.attacksCountTotalDone = 0;
        this.attacksCountTotalSuccess = 0;
    }

    public InjectionSQLReportModel(String host) {
        this.host = host;
        this.timeStarted = new Date();
        this.attacksCountTotalRequested = 0;
        this.attacksCountTotalDone = 0;
        this.attacksCountTotalSuccess = 0;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
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

    public long getAttacksSitesRequested() {
        return attacksSitesRequested;
    }

    public void setAttacksSitesRequested(long attacksSitesRequested) {
        this.attacksSitesRequested = attacksSitesRequested;
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

    public List<AttributeOfAttributeClass> getFinalResponses() {
        if (finalResponses == null) {
            finalResponses = new ArrayList<>();
        }
        return finalResponses;
    }

    public void incrementAttacksCountTotalDone() {
        this.attacksCountTotalDone++;
    }

    public void incrementAttacksCountTotalSuccess() {
        this.attacksCountTotalSuccess++;
    }
}
