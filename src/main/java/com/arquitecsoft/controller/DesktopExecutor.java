package com.arquitecsoft.controller;

import com.arquitecsoft.conectivity.DesktopDriver;
import com.arquitecsoft.model.ExecutorDesktopModel;
import com.arquitecsoft.model.ResponseModel;
import com.arquitecsoft.util.TokenValidator;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import java.awt.AWTException;
import java.io.File;
import org.wso2.carbon.metrics.core.annotation.Counted;

import javax.ws.rs.*;

/**
 * Se encarga de ejecutar los comandos enviados al RPA de tipo desktop
 * Inicia en un hilo aparte la ejecucion del RPA desktop
 * Depende de la libreria WINIUM
 * @since 0.02.1.0
 * @author JValencia
 */
@Counted
@Path("/execute/desktop")
public class DesktopExecutor {
    Gson out = new Gson();

    /**
     * Esta funcion es la entrada para ejecutar los comandos en el navegador
     * Responde al cliente que se conecto correctamente
     * Inicia en un hilo aparte la ejecucion del RPA Browser
     * Solo funciona en WINDOWS
     * // TODO: 2019-08-23 Linux Support
     * @param token Parametro de autenticacion, debe coincidir con el dato "app.token" en el archivo rpa1.properties
     * @param body JSON con datos de conexion y comandos
     * @return JSON con estructura de exito / error
     */
    @Counted
    @POST
    @Path("/run")
    @Produces("application/json")
    public String runCommands(@HeaderParam("authorization") String token, String body) throws AWTException {
        if (!TokenValidator.Validate(token)) {
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
            return out.toJson(new ResponseModel(ResponseModel.ERROR_CODE));
        }

        ExecutorDesktopModel bodyParams;
        try {
            bodyParams = out.fromJson(body, ExecutorDesktopModel.class);
        } catch (JsonIOException | JsonSyntaxException e) {
            e.printStackTrace();
            Main.LOG.error("Las configuracies de par\u00e1metros no son v\u00e1lidas, recibido: " + body);
            return out.toJson(new ResponseModel(ResponseModel.ERROR_PARAMS_CODE));
        }
        
        ResponseModel response = new ResponseModel();
        Main.LOG.info("Verificar ubicacion driver " + bodyParams.getDriverLocation());
        File location = new File(bodyParams.getDriverLocation());
        if(location.exists()){
            Main.LOG.info("Archivo localizado");
            response.setErrorCode("1");
            response.setErrorDesc("Conectado!");
            // Ejecutar hilo, soltarlo, y responder al cliente que solicita
            DesktopDriver threadRun = new DesktopDriver();
            threadRun.setCommands(bodyParams.getCommands());
            threadRun.setDriverLocation(bodyParams.getDriverLocation());
            threadRun.setPid(bodyParams.getPid());
            threadRun.setIdPending(bodyParams.getIdPending());
            threadRun.setSendOutput(bodyParams.isSendOutput());
            threadRun.setMaxIterations(bodyParams.getMaxIterations());
            threadRun.setTimeSleep(bodyParams.getTimeSleep());
            threadRun.run();

            /*Thread t = new Thread(threadRun);
            t.start();
            */
          }else{
            response.setErrorCode("2");
            response.setErrorDesc("El windows driver no se encuentra en la ruta especificada");
          }
        return out.toJson(response);
    }
}
