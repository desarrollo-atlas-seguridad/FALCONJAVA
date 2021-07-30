package com.arquitecsoft.controller;

import com.arquitecsoft.conectivity.ChromeDriverBrowser;
import com.arquitecsoft.model.ExecutorBrowserModel;
import com.arquitecsoft.model.ResponseModel;
import com.arquitecsoft.util.TokenValidator;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.wso2.carbon.metrics.core.annotation.Counted;

import javax.ws.rs.*;
import java.io.File;
import java.util.HashMap;

/**
 * Se encarga de ejecutar los comandos enviados al RPA de tipo navegador
 * Inicia en un hilo aparte la ejecucion del RPA Browser
 * Depende de la libreria SELENIUM
 * @since 0.02.1.0
 * @author YMondragon
 */
@Counted
@Path("/execute/browser")
public class BrowserExecutor {
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

    /**
     * Prueba basica de ejecucion
     * @param token
     * @return
     */
    @Counted
    @POST
    @Path("/run_test")
    @Produces("application/json")
    public String TestCommand(@HeaderParam("authorization") String token)  {
        System.out.println("Hello");
        if(!TokenValidator.Validate(token)){
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
            return out.toJson(new ResponseModel(ResponseModel.ERROR_CODE));
        }

        HashMap<String, Object> chromePrefs = new HashMap<String, Object>();
        chromePrefs.put("profile.default_content_settings.popups", 0);
        chromePrefs.put("download.default_directory", "C:\\Users\\Administrador.000\\Documents\\robot_downloads\\otra");

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", chromePrefs);
        System.setProperty("webdriver.chrome.driver","C:\\Windows\\chromedriver_win32 (3)\\chromedriver.exe");

        WebDriver driver = new ChromeDriver(options);

        ResponseModel response = new ResponseModel(null);

        try {

            String web = "http://syseleycontracali:Isabela2309@atlascrm.atlas.com.co/empleados/docs/DocList.aspx?View=Documents&Layout=3&RestrictionField1=CreatedBy&RestrictionValue1=";
            System.out.println("Entrando a: " + web);
            driver.get(web);
            driver.manage().window().maximize();

            System.out.println("Wait...");
            Thread.sleep(5000);

            response.setErrorCode("200");
            response.setErrorDesc("Finished");

            System.out.println("END");

        } catch (Exception e){
            e.printStackTrace();

        } finally {
            try {
                driver.close();
            } catch (Exception e) {}
        }

        return out.toJson(response);
    }

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
    public String runCommands(@HeaderParam("authorization") String token, String body) {
        if (!TokenValidator.Validate(token)) {
            Main.LOG.error("Configuraci\u00f3n de Token no v\u00e1lida");
            return out.toJson(new ResponseModel(ResponseModel.ERROR_CODE));
        }

        ExecutorBrowserModel bodyParams = null;
        try {
            bodyParams = out.fromJson(body, ExecutorBrowserModel.class);
        } catch (JsonIOException | JsonSyntaxException e) {
            Main.LOG.error("Las configuracies de par\u00e1metros no son v\u00e1lidas, recibido: " + body);
            return out.toJson(new ResponseModel(ResponseModel.ERROR_PARAMS_CODE));
        }

        ResponseModel response = new ResponseModel();
        Main.LOG.info("Verificar ubicacion driver " + bodyParams.getDriverLocation());
        File driverL = new File(bodyParams.getDriverLocation());
        if(driverL.exists()){
            Main.LOG.info("Archivo localizado");
            response.setErrorCode("1");
            response.setErrorDesc("Conectado!");

            // Ejecutar hilo, soltarlo, y responder al cliente que solicita
            ChromeDriverBrowser threadRun = new ChromeDriverBrowser();
            threadRun.setCommands(bodyParams.getCommands());
            threadRun.setPid(bodyParams.getPid());
            threadRun.setIdPending(bodyParams.getIdPending());
            threadRun.setSendOutput(bodyParams.isSendOutput());
            threadRun.setMaxIterations(bodyParams.getMaxIterations());
            threadRun.setDownloadsLocation(bodyParams.getDownloadLocation());
            threadRun.setDriverExecutorLocation(bodyParams.getDriverLocation());
            threadRun.setTimeSleep(bodyParams.getTimeSleep());
            threadRun.run();
//            threadRun.setHost(bodyParams.getHost());
//            threadRun.setUsername(bodyParams.getUser());
//            threadRun.setPassword(bodyParams.getPassword());
           // Thread t = new Thread(threadRun);
            //t.start();
        } else {
            response.setErrorCode("2");
            response.setErrorDesc("El BrowserDriver no se encuentra en la ruta especificada");
        }

        return out.toJson(response);
    }
}
