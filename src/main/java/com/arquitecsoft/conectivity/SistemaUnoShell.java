package com.arquitecsoft.conectivity;

import com.arquitecsoft.connector.DashboardRequest;
import com.arquitecsoft.controller.Main;
import com.arquitecsoft.model.ExecutorCommandModel;
import com.arquitecsoft.model.ExecutorModel;
import com.arquitecsoft.util.StringCleaner;
import com.google.gson.Gson;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Clase tipo Hilo que se encarga de ejecutar los comandos de un shell CGUno
 * @author YMondragon
 */
public class SistemaUnoShell implements Runnable{
    private int pid;
    private int idPending;
    private String host;
    private String username;
    private String password;
    private String ruteResponse;
    private boolean sendOutput;
    private int maxIterations;

    private CGUnoShell shell;
    private HashMap<String, ExecutorCommandModel> commands;

    public SistemaUnoShell() {
        this.sendOutput = true;
        this.maxIterations = 10;
        this.commands = new HashMap<String, ExecutorCommandModel>();
    }

    public SistemaUnoShell(CGUnoShell shell) {
        this.sendOutput = true;
        this.shell = shell;
        this.maxIterations = 10;
        this.commands = new HashMap<String, ExecutorCommandModel>();
    }

    /**
     * Ejecuta los comandos y guarda en el log hora de inicio y finalizacion
     */
    @Override
    public void run() {
        Main.LOG.info("Iniciando ejecuci\u00f3n de comandos...");
        List<ExecutorCommandModel> commandsOut = new ArrayList<>();
        try {
            if(this.shell != null && this.commands.size() > 0){
                // Ya esta conectada la Shell, conectar el Channel
                this.shell.connectChannel();

                String nextCmd = "INIT";
                String outTerminalText = "";
                this.emptyLog(null);

                int iterations = 0;
                int waitIterations = 0;

                // Ejecutar comandos
                do {
                    ExecutorCommandModel cmd = this.commands.get(nextCmd);

                    // Validar comando de salida (While Break)
                    if(nextCmd.equals("END")){
                        break;
                    }else if(cmd == null){
                        Main.LOG.warn("El comando con llave '"+nextCmd+"' no fue encontrado");
                        break;
                    }

                    // En caso de sobrepasar la cantidad de iteraciones maximas, salir
                    else if(iterations >= this.maxIterations){
                        Main.LOG.warn("Cantidad m\u00e1xima de iteraciones superada (" + iterations + ")");
                        cmd.setOutput("");
                        cmd.setStatus("OVERFLOW");
                        // Agregar el comando, que no se pudo ejecutar, en el array
                        commandsOut.add(cmd);
                        break;
                    }

                    iterations++;

                    // Si es una ejecucion normal, ejecutar el comando
                    if(cmd.getType().equals("normal")){
                        if(cmd.getCommand() != null) {
                            Main.LOG.info("Ejecutar comando (pos='"+nextCmd+"'): '"+cmd.getCommand()+"'");
                            outTerminalText = this.shell.runCommand(cmd.getCommand());
                            this.addToLog2(outTerminalText,null);
    //                        outTerminalText = outTerminalText.substring(outTerminalText.lastIndexOf("\n"));
                            outTerminalText = StringCleaner.cleanSistemaUnoOutput(outTerminalText);
                            this.addToLog(outTerminalText,null);
                        }else{
                            Main.LOG.warn("Comando tipo 'normal' no se ejecuta correctamente, comando NULL");
                        }

                    // Si es una ejecucion key, es necesario enviar una tecla de escape
                    }else if(cmd.getType().equals("key")){
                        if(cmd.getCommand() != null) {
                            Main.LOG.info("Tecla de escape: " + cmd.getCommand());
                            String charx = null;
                            switch (cmd.getCommand()){
                                case "KEY_INTRO":
                                    charx = "" + (char) 10; // INTRO
                                    break;
                                case "KEY_ESC":
                                    charx = "" + (char) 27; // ESC
                                    break;
                                case "KEY_F2":
                                    charx = "" + (char) 113; // F2
                                    break;
                            }

                            if(charx != null){
                                outTerminalText = this.shell.runCommand(charx);
                                this.addToLog2(outTerminalText,null);
                                outTerminalText = StringCleaner.cleanSistemaUnoOutput(outTerminalText);
                                this.addToLog(outTerminalText,null);
                            }
                        }else{
                            Main.LOG.warn("Comando tipo 'key' no se ejecuta correctamente, comando NULL");
                        }


                    // Si es una ejecucion key, es necesario enviar una tecla de escape
                    }else if(cmd.getType().equals("wait")){
                        try {
                            int loops = Integer.parseInt(cmd.getProperties().get(0));
                            Main.LOG.info("Wait (" + (loops - waitIterations) + ")...");
                            Thread.sleep(1000);
                            if(waitIterations < loops){
                                waitIterations++;
                                continue;
                            }
                        }
                        catch (NumberFormatException e){
                            Main.LOG.error("Propiedad de comando WAIT debe ser un n\u00famero");
                        }
                        catch (InterruptedException e){}
                        catch (IndexOutOfBoundsException e){
                            Main.LOG.error("Propiedades de comando WAIT insuficientes");
                        }

                    }

                    waitIterations = 0;
                    nextCmd = cmd.getNext(outTerminalText, this.sendOutput);
                    Main.LOG.info("Next es: " + nextCmd);

                    // Agregar el comando, que se acaba de ejecutar, en el array
                    commandsOut.add(cmd);
                }while (true);


                this.shell.disConnectChannel();
                this.shell.disconnect();

                Main.LOG.info("Ejecuci\u00f3n de comandos terminado, finaliza.");
            }else if(this.shell == null){
                Main.LOG.info("Shell no inicializada, finaliza.");
            }else{
                Main.LOG.info("Lista de comandos vac\u00eda, finaliza.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Main.LOG.info("Operacion interrumpida e: " + e);
        } finally {
            this.shell.disConnectChannel();
            this.shell.disconnect();
        }

        Main.LOG.info("Finalizado.");
        Main.LOG.info("Commandos: " + commandsOut.size());
        Gson g = new Gson();
        Main.LOG.info(g.toJson(commandsOut));

        // Enviar resultados al dashboard
        ExecutorModel bodyData = new ExecutorModel();
        bodyData.setHost(this.host);
        bodyData.setUser(this.username);
        bodyData.setPassword(this.password);
        bodyData.setPid(this.pid);
        bodyData.setIdPending(this.idPending);
        bodyData.setCommands(commandsOut);
        bodyData.setSendOutput(this.sendOutput);

        DashboardRequest request = new DashboardRequest(Main.APP_DASHBOARD_HOST, Main.APP_DASHBOARD_USER, Main.APP_DASHBOARD_PASS);
        Main.LOG.info("Enviando datos al Dashboard...");
        request.sendData(this.ruteResponse, g.toJson(bodyData));
        Main.LOG.info("Proceso finalizado.");

    }

    /**
     * Agrega todos los comandos enlistados en un hashmap indexado por llave
     * @param commands Listado de comandos
     */
    public void setCommands(List<ExecutorCommandModel> commands) {
        for (ExecutorCommandModel cmd : commands) {
            this.commands.put(cmd.getId(), cmd);
        }
    }

    /**
     * Agrega al final del archivo logName el texto
     * @param text Texto a agregar en el archivo logName
     * @param logName Nombre del archivo en el cual se agregara el texto (Si es NULL, se guarda en output.log)
     */
    private void addToLog(String text, String logName){
        if(logName == null || logName.equals("")){
            logName = "output.log";
        }
        try{
            FileWriter fw = new FileWriter(logName, true);
            fw.write(text);
            fw.close();
        }catch(Exception e){System.out.println(e);}
    }
    private void addToLog2(String text, String logName){
        if(logName == null || logName.equals("")){
            logName = "output2.log";
        }
        try{
            FileWriter fw = new FileWriter(logName, true);
            fw.write(text);
            fw.close();
        }catch(Exception e){System.out.println(e);}
    }

    /**
     * Crea un archivo en blanco con el nombre especificado, si ya existe reemplaza el anterior
     * @param logName Nombre del archivo en el cual se agregara el texto (Si es NULL, se guarda en output.log)
     */
    private void emptyLog(String logName){
        if(logName == null || logName.equals("")){
            logName = "output.log";
        }
        try{

            FileWriter fw = new FileWriter(logName);
            fw.write("");
            fw.close();
        }catch(IOException e){System.out.println(e);}
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public boolean isSendOutput() {
        return sendOutput;
    }

    public void setSendOutput(boolean sendOutput) {
        this.sendOutput = sendOutput;
    }
    public int getIdPending() {
        return idPending;
    }
    public void setIdPending(int idPending) {
        this.idPending = idPending;
    }
    public int getMaxIterations() {
        return maxIterations;
    }
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getRuteResponse() {
        return ruteResponse;
    }

    public void setRuteResponse(String ruteResponse) {
        this.ruteResponse = ruteResponse;
    }
    
}
