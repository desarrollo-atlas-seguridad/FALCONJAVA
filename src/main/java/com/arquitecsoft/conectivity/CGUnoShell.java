package com.arquitecsoft.conectivity;

import com.jcraft.jsch.*;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class CGUnoShell {
    private Session session;
    private final JSch jsch;
    private final String host;
    private final String username;
    private final String password;
    private String status;
    private Channel channel;
    private PrintStream shellStream;
    private ByteArrayOutputStream baos;

    public CGUnoShell(String host, String username, String password) {
        System.out.println("Inicia JSchShell(host='"+host+"', username='"+username+"', password='"+password+"')");

        this.jsch = new JSch();
        this.host = host;
        this.username = username;
        this.password = password;
        this.status = "disconnected";
        this.initSession();
    }

    /**
     * Inicializar la sesion
     */
    private void initSession() {
        try {
            session = jsch.getSession(username, host, 22);
            session.setPassword(password);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setPassword(password);
            System.out.println("Intentando conectar a "+host+":22");
            session.connect();
            this.status = "connected";
        } catch (JSchException e) {
            if(e.getMessage().contains("Auth cancel")){
                this.status = "fail_password";
            } else if(e.getMessage().contains("Connection timed out")){
                this.status = "fail_reach";
            }
            System.out.println("No es posible conectarse: " + e.getMessage());
        }

    }

    /**
     * Informa si la conxion fue existosa con la session
     *
     * @return true si hay conexion remota
     */
    public boolean isConnected() {
        if (session != null) {
            return session.isConnected();
        }

        this.status = "disconnected";
        return false;
    }

    /**
     * Desconecta la session
     */
    public void disconnect() {
        if(this.isConnected()){
            this.session.disconnect();
        }
        this.status = "disconnected";
    }

    /**
     * Conecta el canal
     */
    public void connectChannel() {
        try {
            this.channel = this.session.openChannel("shell");
            ((ChannelShell) channel).setPtyType("linux");
            this.shellStream = new PrintStream(channel.getOutputStream());
            this.baos = new ByteArrayOutputStream();
            channel.setOutputStream(baos);
            channel.connect();
        } catch (JSchException ex) {
            System.out.println("No es posible conectarse al canal: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("No es posible mostrar la salida del shell: " + ex.getMessage());
        }
    }

    /**
     * Desconecta el canal
     */
    public void disConnectChannel() {
        try {
            this.channel.disconnect();
            this.session.disconnect();
        } catch (Exception ex) {
            System.out.println("No es posible cerrar el canal: " + ex.getMessage());
        }
    }

    /**
     * Ejecuta una lista de comandos cada 5 segundos
     *
     * @param commands
     */
    public List<String> runCommands(List<String> commands) {
        System.out.println("Inicia runCommands(comandos="+commands+")");
        List<String> outText = new ArrayList<>();
        try {
            Channel channel = this.session.openChannel("shell");
            ((ChannelShell) channel).setPtyType("linux");
            PrintStream shellStream = new PrintStream(channel.getOutputStream());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            channel.setOutputStream(baos);
            channel.connect();
            for (String cmd : commands) {
                shellStream.println(cmd);
                shellStream.flush();
                System.out.println("Siguiente comando: " + cmd);
                Charset outString = StandardCharsets.UTF_8;
                String giantString = baos.toString(String.valueOf(outString));
                outText.add(giantString);
                Thread.sleep(2000);
            }
            Thread.sleep(2000);
            channel.disconnect();
            session.disconnect();
        } catch (InterruptedException ex) {
            System.out.println("Hilo interrumpido: " + ex.getMessage());
        } catch (JSchException ex) {
            System.out.println("No es posible conectarse al canal: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("No es posible mostrar la salida del shell: " + ex.getMessage());
        }

        return outText;
    }

    /**
     * Ejecuta un comando individualmente, agregando la tecla enter
     * @param cmd Comando a ejecutar
     */
    public String runCommandLn(String cmd) {
//        System.out.println("Inicia runCommand(comando="+cmd+")");
        String outText = "";
        try {

            shellStream.println(cmd);
            shellStream.flush();
            Thread.sleep(2000);
            outText = baos.toString(String.valueOf(StandardCharsets.US_ASCII));

        } catch (InterruptedException ex) {
            System.out.println("Hilo interrumpido: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("No es posible mostrar la salida del shell: " + ex.getMessage());
//        } catch (JSchException ex) {
//            System.out.println("No es posible conectarse al canal: " + ex.getMessage());
        }

        return outText;
    }

    /**
     * Ejecuta un comando individualmente
     * @param cmd Comando a ejecutar
     */
    public String runCommand(String cmd) {
//        System.out.println("Inicia runCommand(comando="+cmd+")");
        String outText = "";
        try {
            baos.reset();
            shellStream.print(cmd);
            shellStream.flush();
            Thread.sleep(1000);
            outText = baos.toString(String.valueOf(StandardCharsets.US_ASCII));

        } catch (InterruptedException ex) {
            System.out.println("Hilo interrumpido: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("No es posible mostrar la salida del shell: " + ex.getMessage());
//        } catch (JSchException ex) {
//            System.out.println("No es posible conectarse al canal: " + ex.getMessage());
        }

        return outText;
    }

    /**
     * Ejecuta una prueba con una serie de comandos para procesar un archivo
     * llamado TERCEROS
     */
    public void runCommandsTest() {
        CGUnoShell shell = new CGUnoShell("10.1.1.7", "jcaldero", "2014.atlas123.");
//        shell.initSession();
        List<String> commands = new ArrayList<>();
        commands.add("");
        commands.add("PRUEBAS");
        commands.add("ATLAS123");
        commands.add("M");
        commands.add("M");
        commands.add("N");
        commands.add("E");
        commands.add("E");
//        commands.add("TERCEROS");
//        commands.add("");
//        commands.add("1");
//        commands.add("");
//        commands.add("");
//        commands.add("");
//        commands.add("");
//        commands.add("S");
//        commands.add("");
//        commands.add("");
//        commands.add("");
        List<String> output = shell.runCommands(commands);

        try{
            FileWriter fw=new FileWriter("output.log");
            for (String t:output) {
                fw.write(t);
            }
            fw.close();
        }catch(Exception e){
            System.out.println(e);}
        System.out.println("Success...");
        System.out.println("Termina runCommandsTest()");
    }

    /**
     * Devuelve el estado de la conexion
     * "connected" Cuando la conexion se encuentra activa
     * "disconnected" Antes del proceso de conexion / Despues de desconectar la sesion
     * "fail_password" Cuando la conexion es rechazada por autenticacion
     * "fail_reach" Cuando no es posible alcanzar el destino
     * @return String
     */
    public String getStatus(){
        return this.status;
    }

}
