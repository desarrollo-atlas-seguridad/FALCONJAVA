package com.arquitecsoft.controller;

import com.arquitecsoft.conectivity.CGUnoShell;
import com.arquitecsoft.conectivity.SistemaUnoShell;
import com.arquitecsoft.model.ExecutorCommandModel;
import com.arquitecsoft.model.ExecutorModel;
import com.arquitecsoft.model.ResponseModel;
import com.arquitecsoft.util.TokenValidator;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.wso2.carbon.metrics.core.annotation.Counted;

import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Se encarga de ejecutar los comandos enviados al RPA
 * Depende del archivo SistemaUno-jar-with-dependencies.jar
 *  Proyecto SistemaUno del repositorio
 * @since 0.1-SNAPSHOT
 * @author YMondragon
 */
@Counted
@Path("/execute")
public class Executor {
    Gson out = new Gson();

    @Counted
    @GET
    @Path("/test")
    public String Test() {
        System.out.println("Test invoked");
        
        return "Ttttest1";
    }

    @Counted
    @GET
    @Path("/test1")
    public String Test1(@HeaderParam("authorization") String token) {
        System.out.println("Es Test1, auth: " + token);
        return "Hello from WSO2 MSF4J, auth: " + token;
    }

    @Counted
    @POST
    @Path("/test1_2")
    @Produces("application/json")
    public String Test1_2(@HeaderParam("authorization") String token, String body)  {
        if(!TokenValidator.Validate(token)){
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
//            return out.toJson(new ResponseModel(ResponseModel.ERROR_CODE));
        }

        System.out.println("Body: " + body);
//        ExecutorModel bodyParams = null;
//        try {
//            bodyParams = out.fromJson(body, ExecutorModel.class);
//        } catch (JsonIOException | JsonSyntaxException e){
//            Main.LOG.error("Las configuracies de par\u00e1metros no son v\u00e1lidas");
//            return out.toJson(new ResponseModel(ResponseModel.ERROR_PARAMS_CODE));
//        }

        System.out.println("Es Test1, auth: " + token);
        return out.toJson(new ResponseModel(ResponseModel.SUCCESS_CODE));
    }

    @Counted
    @GET
    @Produces("application/json")
    @Path("/test2")
    public String Test2(@HeaderParam("authorization") String token)  {
        if(!TokenValidator.Validate(token)){
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
            return out.toJson(new ResponseModel("error"));
        }

        ExecutorModel model = new ExecutorModel();
        List<ExecutorCommandModel> commands = new ArrayList<>();
        commands.add(new ExecutorCommandModel("INIT", "KEY_INTRO", "key", "val_init"));
        List<String> p = new ArrayList<>();
        p.add("(1)");
        p.add("numero superior de runtime");
        commands.add(new ExecutorCommandModel("val_init", "contains", p, "END", "1"));
        commands.add(new ExecutorCommandModel("1",    "PRUEBAS", "normal", "intro_1"));
        commands.add(new ExecutorCommandModel("intro_1", "KEY_INTRO", "key", "2"));
        commands.add(new ExecutorCommandModel("2", "ATLAS123", "normal", "3"));
        commands.add(new ExecutorCommandModel("3", "D", "normal", "4"));
        commands.add(new ExecutorCommandModel("4", "N", "normal", "5"));
        commands.add(new ExecutorCommandModel("5", "N", "normal", "6"));
        commands.add(new ExecutorCommandModel("6", "R", "normal", "7"));
        commands.add(new ExecutorCommandModel("7", "C", "normal","8"));
        commands.add(new ExecutorCommandModel("8", "1906", "normal","intro_2"));
        commands.add(new ExecutorCommandModel("intro_2", "KEY_INTRO", "key", "10"));
        commands.add(new ExecutorCommandModel("10", "002", "normal","11"));
//        commands.add(new ExecutorCommandModel("10", "038", "normal","11"));
        commands.add(new ExecutorCommandModel("11", "LN", "normal","11.2"));
//        commands.add(new ExecutorCommandModel("11.2", "000220", "normal","12"));
        commands.add(new ExecutorCommandModel("11.2", "000739", "normal","12"));
        commands.add(new ExecutorCommandModel("12", "KEY_INTRO", "key", "13"));
        commands.add(new ExecutorCommandModel("13", "00", "normal","intro_3"));
        commands.add(new ExecutorCommandModel("intro_3", "KEY_INTRO", "key", "intro_4"));
        commands.add(new ExecutorCommandModel("intro_4", "KEY_INTRO", "key", "intro_5"));
        commands.add(new ExecutorCommandModel("intro_5", "KEY_INTRO", "key", "14"));
        commands.add(new ExecutorCommandModel("14", "66", "normal","intro_6"));
        commands.add(new ExecutorCommandModel("intro_6", "KEY_INTRO", "key", "15"));
        commands.add(new ExecutorCommandModel("15", "S", "normal","intro_7"));
        commands.add(new ExecutorCommandModel("intro_7", "KEY_INTRO", "key", "intro_8"));
        commands.add(new ExecutorCommandModel("intro_8", "KEY_INTRO", "key", "21"));

//        commands.add(new ExecutorCommandModel("val_init", "contains", p, "END", "1"));
//        commands.add(new ExecutorCommandModel("2", "ATLAS123D", "normal", "4"));
//        commands.add(new ExecutorCommandModel("4", "N", "normal", "5"));
//        commands.add(new ExecutorCommandModel("5", "N", "normal", "6"));
//        commands.add(new ExecutorCommandModel("6", "R", "normal", "7"));
//        commands.add(new ExecutorCommandModel("7", "C", "normal","8"));
//        commands.add(new ExecutorCommandModel("8", "KEY_F2", "key","21"));
        List<String> p1 = new ArrayList<>();
        p1.add("/apl/siesa/prt/URNM3073.P66");
        p1.add("14");
        commands.add(new ExecutorCommandModel("21", "count_lines", p1, "22", "END"));
        commands.add(new ExecutorCommandModel("22", "KEY_ESC", "key","intro_9"));
        commands.add(new ExecutorCommandModel("intro_9", "KEY_INTRO", "key", "intro_10"));
        commands.add(new ExecutorCommandModel("intro_10", "KEY_INTRO", "key", "23"));
        commands.add(new ExecutorCommandModel("23", "038", "normal","intro_11"));
        commands.add(new ExecutorCommandModel("intro_11", "KEY_INTRO", "key", "intro_12"));
        commands.add(new ExecutorCommandModel("intro_12", "KEY_INTRO", "key", "intro_13"));
        commands.add(new ExecutorCommandModel("intro_13", "KEY_INTRO", "key", "24"));
        commands.add(new ExecutorCommandModel("24", "100", "normal","intro_14"));
        commands.add(new ExecutorCommandModel("intro_14", "KEY_INTRO", "key", "intro_15"));
        commands.add(new ExecutorCommandModel("intro_15", "KEY_INTRO", "key", "intro_16"));
        commands.add(new ExecutorCommandModel("intro_16", "KEY_INTRO", "key", "25"));
        commands.add(new ExecutorCommandModel("25", "67", "normal","intro_17"));
        commands.add(new ExecutorCommandModel("intro_17", "KEY_INTRO", "key", "26"));
        commands.add(new ExecutorCommandModel("26", "S", "normal","27"));
        List<String> p2 = new ArrayList<>();
        p2.add("** ERROR NO DOCUMENTADO **");
        commands.add(new ExecutorCommandModel("27", "contains", p2, "END", "END"));




//
//        commands.add(new ExecutorCommandModel("23", "KEY_ESC", "key","24"));
//        commands.add(new ExecutorCommandModel("24", "KEY_ESC", "key","25"));
//        commands.add(new ExecutorCommandModel("25", "KEY_ESC", "key","26"));
//        commands.add(new ExecutorCommandModel("26", "KEY_ESC", "key","END"));


        model.setCommands(commands);
        model.setHost("10.1.1.7");
        model.setUser("jcaldero");
        model.setPassword("2014.atlas123.");
        return out.toJson(model);
    }

    @Counted
    @POST
    @Path("/run_test")
    @Produces("application/json")
    public String TestCommand(@HeaderParam("authorization") String token)  {
        if(!TokenValidator.Validate(token)){
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
            return out.toJson(new ResponseModel(ResponseModel.ERROR_CODE));
        }

        CGUnoShell sh = new CGUnoShell("10.1.1.7", "jcaldero", "2014.atlas123.");

        Main.LOG.info("Shell a 10.1.1.7");
        ResponseModel response = new ResponseModel();
        if(sh.isConnected()){
            Main.LOG.info("Conexion exitosa");
            response.setErrorCode("1");
            response.setErrorDesc("Conectado!");
            sh.disconnect();
            Main.LOG.info("Desconectado correctamente.");
        }else{
            Main.LOG.error("No es posible conectarse al shell destino");
            response.setErrorCode("2");
            response.setErrorDesc("No es posible alcanzar el destino");
        }

        return out.toJson(response);
    }

    /**
     * Esta funcion es la entrada para ejecutar los comandos en CGUno
     * Se encarga de validar si hay conexion al software CGUno
     * En caso de que haya conexion, se crea un Hilo y se ejecuta en segundo plano
     * Responde al cliente que se conecto correctamente
     * @param token Parametro de autenticacion, debe coincidir con el dato "app.token" en el archivo rpa1.properties
     * @param body JSON con datos de conexion y comandos
     * @return JSON con estructura de exito / error
     */
    @Counted
    @POST
    @Path("/run")
    @Produces("application/json")
    public String runCommands(@HeaderParam("authorization") String token, String body)  {
        if(!TokenValidator.Validate(token)){
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
            return out.toJson(new ResponseModel(ResponseModel.ERROR_CODE));
        }

        ExecutorModel bodyParams = null;
        try {
            bodyParams = out.fromJson(body, ExecutorModel.class);
        } catch (JsonIOException | JsonSyntaxException e){
            Main.LOG.error("Las configuracies de par\u00e1metros no son v\u00e1lidas, recibido: " + body);
            return out.toJson(new ResponseModel(ResponseModel.ERROR_PARAMS_CODE));
        }

//        CGUnoShell sh = new CGUnoShell("10.1.1.7", "jcaldero", "2014.atlas123.");
        Main.LOG.info("Shell a " + bodyParams.getHost());
        CGUnoShell sh = new CGUnoShell(bodyParams.getHost(), bodyParams.getUser(), bodyParams.getPassword());

        ResponseModel response = new ResponseModel();
        if(sh.isConnected()){
            Main.LOG.info("Conexion exitosa");
            response.setErrorCode("1");
            response.setErrorDesc("Conectado!");

            // Ejecutar hilo, soltarlo, y responder al cliente que solicita
            SistemaUnoShell threadRun = new SistemaUnoShell(sh);
            threadRun.setCommands(bodyParams.getCommands());
            threadRun.setPid(bodyParams.getPid());
            threadRun.setIdPending(bodyParams.getIdPending());
            threadRun.setHost(bodyParams.getHost());
            threadRun.setUsername(bodyParams.getUser());
            threadRun.setRuteResponse(bodyParams.getRuteResponse());
            threadRun.setPassword(bodyParams.getPassword());
            threadRun.setSendOutput(bodyParams.isSendOutput());
            threadRun.setMaxIterations(bodyParams.getMaxIterations());
            threadRun.run();
//            Thread t = new Thread(threadRun);
//            t.start();   
        }else{
            if(sh.getStatus().equals("fail_password")){
                Main.LOG.error("Contraseña incorrecta para conectarse al sistema");
                response.setErrorCode("3");
                response.setErrorDesc("Autenticaci\u00f3n al shell fallida");

            } else {
                response.setErrorCode("2");
                response.setErrorDesc("No es posible alcanzar el destino");
            }
        }

        return out.toJson(response);
    }
}
