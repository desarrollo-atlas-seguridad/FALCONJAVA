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
@Path("/update_ftp_data")
public class Dashboard {
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
    @Produces("application/json")
    @Path("/test2")
    public String Test2(@HeaderParam("authorization") String token)  {
        if(!TokenValidator.Validate(token)){
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
            return out.toJson(new ResponseModel("error"));
        }

        ExecutorModel model = new ExecutorModel();
        List<ExecutorCommandModel> commands = new ArrayList<>();
        commands.add(new ExecutorCommandModel("INIT", "", "normal", "val_init"));
        List<String> p = new ArrayList<>();
        p.add("(1)");
        p.add("numero superior de runtime");
        commands.add(new ExecutorCommandModel("val_init", "contains", p, "END", "1"));
        commands.add(new ExecutorCommandModel("1", "PRUEBAS", "normal", "2"));
        commands.add(new ExecutorCommandModel("2", "ATLAS123", "normal", "3"));
        commands.add(new ExecutorCommandModel("3", "M", "normal", "4"));
        commands.add(new ExecutorCommandModel("4", "M", "normal", "5"));
        commands.add(new ExecutorCommandModel("5", "N", "normal", "6"));
        commands.add(new ExecutorCommandModel("6", "E", "normal", "7"));
        commands.add(new ExecutorCommandModel("7", "E", "normal","END"));
        model.setCommands(commands);
        model.setHost("10.1.1.7");
        model.setUser("jcaldero");
        model.setPassword("2014.atlas123.");
        return out.toJson(model);
    }

    @Counted
    @POST
    @Path("/files_downloaded")
    @Produces("application/json")
    public String runCommands(@HeaderParam("authorization") String token, String body)  {
        System.out.println("Body: " + body);
        if(!TokenValidator.Validate(token)){
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
            return out.toJson(new ResponseModel(ResponseModel.ERROR_CODE));
        }

        ExecutorModel bodyParams = null;
        try {
            bodyParams = out.fromJson(body, ExecutorModel.class);
        } catch (JsonIOException | JsonSyntaxException e){
            Main.LOG.error("Las configuracies de par\u00e1metros no son v\u00e1lidas");
            return out.toJson(new ResponseModel(ResponseModel.ERROR_PARAMS_CODE));
        }

//        CGUnoShell sh = new CGUnoShell("10.1.1.7", "jcaldero", "2014.atlas123.");
        Main.LOG.info("Shell a " + bodyParams.getHost());
        CGUnoShell sh = new CGUnoShell(bodyParams.getHost(), bodyParams.getUser(), bodyParams.getPassword());

        ResponseModel response = new ResponseModel();
        System.out.println("FIN___________");

        return out.toJson(response);
    }
}
