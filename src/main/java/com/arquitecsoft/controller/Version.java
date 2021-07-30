package com.arquitecsoft.controller;

import org.wso2.carbon.metrics.core.annotation.Counted;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;

/**
 * Se encarga de ejecutar los comandos enviados al RPA
 * @since 0.1-SNAPSHOT
 * @author YMondragon
 */
@Counted
@Path("/version")
public class Version {

    @GET
    @Path("/")
    public String Version() {
        Main.LOG.info("Private token: " + Main.APP_TOKEN);
        Main.LOG.info("GetVersion() V_" + Main.VERSION);
        return Main.VERSION;
    }
}
