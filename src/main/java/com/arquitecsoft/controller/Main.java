/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.arquitecsoft.controller;

import org.apache.logging.log4j.LogManager;
import org.wso2.msf4j.MicroservicesRunner;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * @author YMondragon
 */
public class Main {

    public final static String VERSION = "0.02.2.1"; // Version.despliegue.caracteristica.commit
    public static final org.apache.logging.log4j.Logger LOG = LogManager.getRootLogger();
    private static int SERVER_PORT;
    public static String APP_TOKEN;
    public static String APP_DASHBOARD_HOST;
    public static String APP_DASHBOARD_USER;
    public static String APP_DASHBOARD_PASS;

    public static void main(String[] args) throws AWTException {
        Robot robot = new Robot();


        System.out.println("AtlasRpa V_" + Main.VERSION);
        Main.LOG.info("AtlasRpa V_" + Main.VERSION);

        // Todas las configuraciones del archivo .properties deben estar configuradas
        if(loadProperties()){
            new MicroservicesRunner(SERVER_PORT)
                    .deploy(new Version())
                    .deploy(new Executor())
                    .deploy(new Dashboard())
                    .deploy(new BrowserExecutor())
                    .deploy(new DesktopExecutor())
                    .start();
            Main.LOG.info("Started on " + Main.SERVER_PORT);
        }else{
            Main.LOG.error("El archivo de configuracion contiene errores");
        }
    }
    /**
     * Lee el archivo de propiedades "rpa1.properties"
     * Informa de cualquier parametro que haga falta
     * @return Boolean, True si el archivo properties esta correctamente estructurado
     */
    private static boolean loadProperties(){

        String propFile = "rpa1.properties";

        try {
            File prop = new File(propFile);
            if(!prop.exists()){
                Main.LOG.error("Es necesario configurar el archivo " + propFile);
                return false;
            }
        } catch (Exception e) {
            Main.LOG.error("Es necesario configurar el archivo " + propFile);
            return false;
        }

        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(propFile));
            String filePort = prop.getProperty("server.port");
            String fileToken = prop.getProperty("app.token");
            String dashboardHost = prop.getProperty("app.dashboard.host");
            String dashboardUser = prop.getProperty("app.dashboard.user");
            String dashboardPass = prop.getProperty("app.dashboard.pass");

            if(filePort == null || filePort.equals("") || filePort.equals("0")){
                Main.LOG.error("No es posible iniciar el sistema sin el par\u00e1metro 'server.port' del archivo " + propFile);
                return false;
            }else if(fileToken == null || fileToken.equals("")){
                Main.LOG.error("No es posible iniciar el sistema sin el par\u00e1metro 'app.token' del archivo " + propFile);
                return false;
            }else if(dashboardHost == null || dashboardHost.equals("")){
                Main.LOG.error("No es posible iniciar el sistema sin el par\u00e1metro 'app.dashboard.host' del archivo " + propFile);
                return false;
            }else if(dashboardUser == null || dashboardUser.equals("")){
                Main.LOG.error("No es posible iniciar el sistema sin el par\u00e1metro 'app.dashboard.user' del archivo " + propFile);
                return false;
            }else if(dashboardPass == null || dashboardPass.equals("")){
                Main.LOG.error("No es posible iniciar el sistema sin el par\u00e1metro 'app.dashboard.pass' del archivo " + propFile);
                return false;
            }

            else {
                SERVER_PORT = Integer.parseInt(filePort);
                APP_TOKEN = fileToken;
                APP_DASHBOARD_HOST = dashboardHost;
                APP_DASHBOARD_USER = dashboardUser;
                APP_DASHBOARD_PASS = dashboardPass;
                return true;
            }

        } catch (IOException | NumberFormatException e) {
            Main.LOG.error("Es necesario configurar el archivo " + propFile);
            return false;
        }

    }

    private static void logTest() {
        LOG.trace("This Will Be Printed On trace");
        LOG.debug("This Will Be Printed On Debug");
        LOG.info("This Will Be Printed On Info");
        LOG.warn("This Will Be Printed On Warn");
        LOG.error("This Will Be Printed On Error");
    }

    public static void sendKeys(Robot robot, String keys) {
        for (char c : keys.toCharArray()) {
            if(c=='/') {
                robot.keyPress(KeyEvent.VK_ALT);
                    robot.keyPress(KeyEvent.VK_NUMPAD0);
                    robot.keyRelease(KeyEvent.VK_NUMPAD0);
                    robot.keyPress(KeyEvent.VK_NUMPAD0);
                    robot.keyRelease(KeyEvent.VK_NUMPAD0);
                    robot.keyPress(KeyEvent.VK_NUMPAD4);
                    robot.keyRelease(KeyEvent.VK_NUMPAD4);
                    robot.keyPress(KeyEvent.VK_NUMPAD7);
                    robot.keyRelease(KeyEvent.VK_NUMPAD7);
                robot.keyRelease(KeyEvent.VK_ALT);
            }else {
                int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
                if (KeyEvent.CHAR_UNDEFINED == keyCode) {
                    throw new RuntimeException(
                            "Key code not found for character '" + c + "'");
                } else {
                    robot.keyPress(keyCode);
                    robot.keyRelease(keyCode);
                }
            }
            robot.delay(30);
        }
    }
}