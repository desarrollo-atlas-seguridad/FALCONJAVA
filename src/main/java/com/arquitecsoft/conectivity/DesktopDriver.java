package com.arquitecsoft.conectivity;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.arquitecsoft.connector.DashboardRequest;
import com.arquitecsoft.controller.Main;
import com.arquitecsoft.model.ExecutorCommandModel;
import com.arquitecsoft.model.ExecutorDesktopModel;
import com.arquitecsoft.util.ReadImage;
import com.arquitecsoft.util.SendMail;
import com.arquitecsoft.util.WriteAndReadFile;
import com.google.gson.Gson;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.io.Zip;
import org.openqa.selenium.winium.DesktopOptions;
import org.openqa.selenium.winium.WiniumDriver;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.imageio.ImageIO;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.arquitecsoft.data.Data.rdp;

/**
 * Clase tipo Hilo que se encarga de ejecutar los comandos de un driver desktop
 *
 * @author JValencia
 */
public class DesktopDriver implements Runnable {

    public static String output = "";
    private int pid;
    private int idPending;
    private boolean sendOutput;
    private int maxIterations;
    private WiniumDriver driver;
    private DesktopOptions options;
    private String driverLocation;
    private Robot robot;
    ExecutorCommandModel cmd;
    private final HashMap<String, ExecutorCommandModel> commands;
    private final HashMap<String, String> variables;
    private String timeSleep;

    public DesktopDriver() throws AWTException {
        this.robot = new Robot();
        this.sendOutput = true;
        this.maxIterations = 10;
        this.driver = null;
        this.robot = null;
        this.commands = new HashMap<>();
        this.variables = new HashMap<>();
    }

    /**
     * Ejecuta los comandos y guarda en el log hora de inicio y finalizacion
     */
    @Override
    public void run() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
        if (this.getDriverLocation() != null) {
            try {
                //Iniciamos el controlador
                /*File objetofile = new File(this.getDriverLocation());
                Desktop.getDesktop().open(objetofile);

                Runtime.getRuntime().exec(
                                         new String[]{"cmd","/c", "start", "/min", this.getDriverLocation()});

                 */
                System.out.println("Winium se espera ya inicilizado");
            } catch (Exception e) {
            }
        } else {
            Main.LOG.error("El comando esta vacio y por ende no se pudo abrir el controlador");
            System.exit(0);
        }

        List<ExecutorCommandModel> commandsOut = new ArrayList<>();
        try {
            if (this.commands.size() > 0) {

                String nextCmd = "INIT";
                String outTerminalText = "";

                int iterations = 0;
                int waitIterations = 0;

                // Ejecutar comandos
                do {
                    cmd = new ExecutorCommandModel(this.commands.get(nextCmd));

                    // Validar comando de salida (While Break)
                    if (nextCmd.equals("END")) {
                        break;
                    } else if (this.commands.get(nextCmd) == null) {
                        Main.LOG.warn("El comando con llave '" + nextCmd + "' no fue encontrado");
                        break;
                    } // En caso de sobrepasar la cantidad de iteraciones maximas, salir
                    else if (iterations >= this.maxIterations) {
                        Main.LOG.warn("Cantidad m\u00e1xima de iteraciones superada (" + iterations + ")");
                        cmd.setOutput("");
                        cmd.setStatus("OVERFLOW");
                        // Agregar el comando, que no se pudo ejecutar, en el array
                        commandsOut.add(cmd);
                        break;
                    }

                    iterations++;

                    // Si es un WAIT, esperar por cada iteracion
                    if (cmd.getType().equals("wait")) {
                        try {
                            int loops = Integer.parseInt(cmd.getProperties().get(0));
                            Main.LOG.info("Wait (" + (loops - waitIterations) + ")...");
                            Thread.sleep(1000);
                            if (waitIterations < loops) {
                                waitIterations++;
                                continue;
                            }
                        } catch (NumberFormatException e) {
                            Main.LOG.error("Propiedad de comando WAIT debe ser un n\u00famero");
                        } catch (InterruptedException e) {
                        } catch (IndexOutOfBoundsException e) {
                            Main.LOG.error("Propiedades de comando WAIT insuficientes");
                        }

                    } else {
                        try {
                            //Ejecucion Principal, si no hay que esperar; ejecutar el comando del driver
                            outTerminalText = this.runCommandByType(cmd);

                            // Luego de ejecutar el comando, se traducen las variables
                            cmd.setCommand(this.replaceCommand(cmd.getCommand()));
                            cmd.setProperties(this.replaceProperties(cmd.getProperties()));
                        } catch (Exception e) {
                            e.printStackTrace();
                            cmd.setStatus("FAIL");
                            List<String> exceptionList = new ArrayList<>();
                            exceptionList.add("Exeption");
                            int lenght = e.getMessage().length() > 100 ? 100 : e.getMessage().length();
                            exceptionList.add(e.getMessage().substring(0, lenght));
                            cmd.setProperties(exceptionList);
                            commandsOut.add(cmd);
                            Main.LOG.error("Exception", e);
                            break;
                        }
                    }

                    waitIterations = 0;
                    nextCmd = cmd.getNext(outTerminalText, this.sendOutput);
                    Main.LOG.info("Next es: " + nextCmd);

                    // Agregar el comando, que se acaba de ejecutar, en el array
                    commandsOut.add(cmd);
                    try {
                        Thread.sleep(Integer.valueOf(this.getTimeSleep()));
                    } catch (Exception e) {
                        Main.LOG.info("Error realizando time sleep " + e.getMessage());
                    }
                } while (true);

                Main.LOG.info("Ejecuci\u00f3n de comandos terminado, finaliza.");
            } else {
                Main.LOG.info("Lista de comandos vac\u00eda, finaliza.");
            }
        } catch (Exception e) {
            Main.LOG.info("Operacion interrumpida e: " + e);
            e.printStackTrace();
        }

        Main.LOG.info("Finalizado.");
        Main.LOG.info("Commandos: " + commandsOut.size());
        Gson g = new Gson();
        //Main.LOG.info(g.toJson(commandsOut));

        // Enviar resultados al dashboard
        ExecutorDesktopModel bodyData = new ExecutorDesktopModel();
        bodyData.setPid(this.pid);
        bodyData.setIdPending(this.idPending);
        bodyData.setCommands(commandsOut);
        bodyData.setSendOutput(this.sendOutput);
        bodyData.setMaxIterations(this.maxIterations);
        bodyData.setDriverLocation(this.driverLocation);

        Main.LOG.info(g.toJson(bodyData));
        DashboardRequest request = new DashboardRequest(Main.APP_DASHBOARD_HOST, Main.APP_DASHBOARD_USER, Main.APP_DASHBOARD_PASS);
        Main.LOG.info("Enviando datos al Dashboard...");
        request.sendData("/api/saveData", g.toJson(bodyData));
        Main.LOG.info("Proceso finalizado.");

    }

    /**
     * Ejecuta el Driver de Chrome dependiendo del tipo de comando
     */
    private String runCommandByType(ExecutorCommandModel cmd) throws Exception {

        if (cmd.getType() != null && !cmd.getType().trim().equals("")) {

            switch (cmd.getType()) {

                //Se encarga de crear archivo de conexion de tipo rdp
                case "createcon":
                    if (cmd.getProperties().get(0) != null) {
                        WriteAndReadFile c = new WriteAndReadFile();
                        try {
                            c.Write(cmd.getProperties().get(0), rdp);
                        } catch (IOException ex) {
                            Logger.getLogger(DesktopDriver.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' asigne la ruta de creacion");
                    }
                    break;
                case "eliminar_archivo":
                        if (cmd.getProperties().get(0) != null) {
                            String documentoEliminar = cmd.getProperties().get(0);

                            try {
                                File fichero = new File(documentoEliminar);
                                fichero.delete();
                                Main.LOG.info("Archivo borrado con exito : "+documentoEliminar);
                            } catch (Exception ex) {
                                Logger.getLogger(DesktopDriver.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } else if (cmd.getProperties().get(0) == null){
                            Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad INPUT_ID");

                        }  else{
                            Main.LOG.error("El comando '" + cmd.getType() + "' asigne la ruta del archivo a eliminar ..");
                        }
                    break;
                case "capturar_texto":
                    if(cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null){
                            try {
                                //Robot robot = new Robot();
                                String x = cmd.getProperties().get(0),y = cmd.getProperties().get(1);
                                // Tamaña de la pantalla
                                int width=167,height=80;
                                // tomamos una captura de pantalla( screenshot )
                                BufferedImage captura = new Robot().createScreenCapture(
                                        new Rectangle(Integer.parseInt(x),Integer.parseInt(y),width,height) );
                                // Metodo radom para poner el nombre del archivo.
                                int leftLimit = 97; // letter 'a'
                                int rightLimit = 122; // letter 'z'
                                int targetStringLength = 10;
                                Random random = new Random();

                                String nombrecaptura = random.ints(leftLimit, rightLimit + 1)
                                        .limit(targetStringLength)
                                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                        .toString();
                                System.out.println("Nombre captura : "+nombrecaptura);
                                File file = new File(nombrecaptura+ ".png");
                                ImageIO.write(captura, "png", file);

                                //VARIABLES DE USO DE AWS
                                String bucketName = "bucketatlasid3";
                                String stringObjKeyName = nombrecaptura+".png";
                                String fileObjKeyName = nombrecaptura+".png";
                                String fileName = "C:\\RPAService\\"+nombrecaptura+".png";
                                //String fileName = "D:\\RPA\\Codigo\\Motor\\"+nombrecaptura+".png";
                                String accesskey = "AKIAUO45GKMDO5C2JDUC";
                                String secretkeyid="q281dxHz+Q3vyS7vYwP1XqyLi1XoyYr3E39ZOW9i";

                                BasicAWSCredentials awsCreds = new BasicAWSCredentials(accesskey, secretkeyid);

                                AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                                        .withRegion("us-east-1")
                                        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                                        .build();

                                // Upload a text string as a new object.
                                s3Client.putObject(bucketName, stringObjKeyName, "Uploaded String Object");

                                // Upload a file as a new object with ContentType and title specified.
                                PutObjectRequest request = new PutObjectRequest(bucketName, fileObjKeyName, new File(fileName));
                                ObjectMetadata metadata = new ObjectMetadata();
                                metadata.setContentType("plain/text");
                                metadata.addUserMetadata("title", "someTitle");
                                request.setMetadata(metadata);
                                s3Client.putObject(request);

                                Main.LOG.info("NOMBRE DE LA VARIABLE DE LA CAPTURA : "+stringObjKeyName);
                                String detectid="1",categoriaid="3";

                                URL url;
                                URLConnection uc;
                                StringBuilder parsedContentFromUrl = new StringBuilder();
                                String urlString="https://53l8r4bx42.execute-api.us-east-1.amazonaws.com/Version1/rekognition?detectid="+detectid+"&imageid="+stringObjKeyName+"&categoriaid="+categoriaid;
                                System.out.println("Getting content for URl : " + urlString);
                                url = new URL(urlString);
                                uc = url.openConnection();
                                uc.connect();
                                uc.getInputStream();
                                BufferedInputStream in = new BufferedInputStream(uc.getInputStream());
                                int ch;
                                while ((ch = in.read()) != -1) {
                                    parsedContentFromUrl.append((char) ch);

                                }

                                    try {
                                        StringBuilder Text =parsedContentFromUrl;
                                        String[] part1 = Text.toString().split("[,.:{}\\''\\[\\]]+");
                                        String palabraDelArreglo ="";
                                        for(int i =0 ; i< part1.length; i++){
                                            System.out.println("Partes : "+part1[i]+" i = "+i);
                                            palabraDelArreglo = part1[i];
                                        }
                                        String[] palabraDelArreglo2 = palabraDelArreglo.split("\"");
                                        //System.out.println("Nueva palabra : "+palabraDelArreglo2[1]);

                                        StringSelection stringSelection = new StringSelection(palabraDelArreglo2[1]);
                                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                                        clipboard.setContents(stringSelection, stringSelection);

                                        Robot robot = new Robot();
                                        robot.keyPress(KeyEvent.VK_CONTROL);
                                        robot.keyPress(KeyEvent.VK_V);
                                        robot.keyRelease(KeyEvent.VK_V);
                                        robot.keyRelease(KeyEvent.VK_CONTROL);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                File fichero = new File(fileName);
                                fichero.delete();
                                System.out.println("ELEMENTO ELIMINADO "+fileName);

                            } catch (AWTException e) {
                                e.printStackTrace();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if (cmd.getProperties().get(0) == null || cmd.getProperties().get(1) == null){
                            Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad INPUT_ID");

                        }  else{
                            Main.LOG.error("El comando '" + cmd.getType() + "' Asigne los parametros X y Y a capturar...");
                        }


                    break;

                case "envio_correo-documento":
                    if(cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null && cmd.getProperties().get(2) != null
                            && cmd.getProperties().get(3) != null && cmd.getProperties().get(4) != null
                                && cmd.getProperties().get(5) != null && cmd.getProperties().get(6) != null){
                        // variables tomadas de las propiedades del front
                        final String email =  cmd.getProperties().get(0);
                        final String  password =  cmd.getProperties().get(1);
                        String corre_destino = cmd.getProperties().get(2);
                        // Capturar Asunto del correo y el cuerpo
                        String asunto = cmd.getProperties().get(3);
                        String cuerpo = cmd.getProperties().get(4);
                        // NOMBRE DEL DOCUMENTO
                        String NombreDocumento = cmd.getProperties().get(5);
                        String documento = cmd.getProperties().get(6);

                        // Configuración correo SMTP
                        Properties props = new Properties();
                        props.put("mail.smtp.auth", "true");
                        props.put("mail.smtp.starttls.enable", "true");
                        props.put("mail.smtp.host", "smtp.office365.com");
                        props.put("mail.smtp.port", "587");

                        Session session = Session.getInstance(props,
                                new javax.mail.Authenticator() {
                                    protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                                        return new PasswordAuthentication(email,password);
                                    }
                                });

                        try {
                            // Define message
                            MimeMessage message = new MimeMessage(session);
                            message.setFrom(new InternetAddress(email));
                            message.setSubject("FALCON "+ asunto);
                            message.addRecipient(Message.RecipientType.TO,new InternetAddress(corre_destino));
                            BodyPart texto = new MimeBodyPart();
                            texto.setText(cuerpo+" \n" +
                                    "\n" +
                                    "\nCordialmente FALCON RPA," +
                                    "\n" +
                                    "\n" +
                                    "\nDesarrollado por Seguridad Atlas LTDA.");
                            MimeMultipart multiParte = new MimeMultipart();

                            BodyPart adjunto = new MimeBodyPart();
                            adjunto.setDataHandler(new DataHandler(new FileDataSource(documento)));
                            adjunto.setFileName(NombreDocumento);

                            multiParte.addBodyPart(texto);
                            multiParte.addBodyPart(adjunto);

                            message.setContent(multiParte);

                            Transport t = session.getTransport("smtp");
                            t.connect(email,password);
                            t.sendMessage(message,message.getAllRecipients());

                            File fichero = new File(documento);
                            fichero.delete();

                            System.out.println("Enviando correo "+ message);
                        } catch (Exception e) {
                            System.out.println("ERROR AL ENVIAR EL MENSAJE "+e);
                        }

                    } else if (cmd.getProperties().get(0) == null) {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad INPUT_ID");
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command'");
                    }
                break;

                //Obtiene la ubicacion de la aplicacion para asi ejecutarla
                case "get":
                    if (cmd.getProperties().get(0) != null) {
                        options = new DesktopOptions();
                        options.setApplicationPath(cmd.getProperties().get(0));

                        if (cmd.getProperties().size() > 1 && "OPENED".equalsIgnoreCase(cmd.getProperties().get(1))) {
                            options.setDebugConnectToRunningApp(true);
                        }
                        driver = new WiniumDriver(new URL("http://localhost:9999"), options);
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' asigne la ruta de la aplicacion");
                    }
                    break;
                //Cerrar los procesos de la app forzadamente
                case "taskill":
                    if (cmd.getProperties().get(0) != null) {
                        try {
                            Process process = Runtime.getRuntime().exec("taskkill /f /im " + cmd.getProperties().get(0));
                            Thread.sleep(2000);
                        } catch (IOException | InterruptedException e) {
                        }
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' debe asignar una ruta");
                    }
                    break;

                // Escribe el 'command' en un Input de tipo texto
                case "normal":
                    if (cmd.getProperties().get(0) != null && cmd.getCommand() != null) {
                        try {
                            System.out.println("Tamaño "+cmd.getProperties().size());
                            if(cmd.getProperties().size()>1) {
                                WebElement element=this.driver.findElement(By.name(cmd.getProperties().get(0)));
                                for (int i = 1; i < cmd.getProperties().size(); i++) {
                                    element = element.findElement(By.name(cmd.getProperties().get(i)));
                                }
                                element.click();
                                element.sendKeys(cmd.getCommand());
                            }else{
                                this.driver.findElement(By.name(cmd.getProperties().get(0))).sendKeys(cmd.getCommand());
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } else if (cmd.getProperties().get(0) == null) {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad INPUT_ID");
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command'");
                    }
                    break;


                // Presiona Click en el elemento encontrado por ID
                case "click":
                    if (cmd.getProperties().get(0) != null) {
                        //driver.findElement(By.name(cmd.getProperties().get(0))).click();

                        try {
                            System.out.println("Tamaño "+cmd.getProperties().size());
                            if(cmd.getProperties().size()>1) {
                                WebElement element=this.driver.findElement(By.name(cmd.getProperties().get(0)));
                                for (int i = 1; i < cmd.getProperties().size(); i++) {
                                    element = element.findElement(By.name(cmd.getProperties().get(i)));
                                }
                                element.click();
                            }else{
                                this.driver.findElement(By.name(cmd.getProperties().get(0))).click();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                    }
                    break;

                // Presiona Click en el elemento encontrado por ID
                case "clickxy":
                    if (cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null) {
                        //driver.findElement(By.name(cmd.getProperties().get(0))).click();
                        try {
                            robot.mouseMove(Integer.valueOf(cmd.getProperties().get(0)), Integer.valueOf(cmd.getProperties().get(1)));
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                            robot.mouseMove(Integer.valueOf(cmd.getProperties().get(0)), Integer.valueOf(cmd.getProperties().get(1)));
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                            Main.LOG.info("Clic en el boton");
                        } catch (Exception e) {
                            Main.LOG.info("No se pudo hacer clic en el boton");
                        }

                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                    }
                    break;
                // Presiona Click en el elemento encontrado por ID
                case "clickxy1":
                    if (cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null) {
                        //driver.findElement(By.name(cmd.getProperties().get(0))).click();
                        try {
                            robot.mouseMove(Integer.valueOf(cmd.getProperties().get(0)), Integer.valueOf(cmd.getProperties().get(1)));
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                            Main.LOG.info("Clic en el boton");
                        } catch (Exception e) {
                            Main.LOG.info("No se pudo hacer clic en el boton");
                        }

                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                    }
                    break;
                // Presiona Click en el elemento encontrado por ID
                case "dobleclickxy":
                    if (cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null) {
                        try {
                            for (int i = 0; i <= 1; i++) {
                                robot.mouseMove(Integer.valueOf(cmd.getProperties().get(0)), Integer.valueOf(cmd.getProperties().get(1)));
                                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                            }
                            Main.LOG.info("DobleClic en el boton");
                        } catch (Exception e) {
                            Main.LOG.info("No se pudo hacer clic en el boton");
                        }

                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                    }
                    break;
                // Presiona Click en el elemento encontrado por ID
                case "write_text":
                    try {
                        if (cmd.getCommand() != null) {
                            String text = cmd.getCommand();
                            StringSelection stringSelection = new StringSelection(text);
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(stringSelection, stringSelection);

                            Robot robot = new Robot();
                            robot.keyPress(KeyEvent.VK_CONTROL);
                            robot.keyPress(KeyEvent.VK_V);
                            robot.keyRelease(KeyEvent.VK_V);
                            robot.keyRelease(KeyEvent.VK_CONTROL);
                        } else {
                            Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    break;
                //Presionar tecla de tab
                case "tab":
                    robot.keyPress(java.awt.event.KeyEvent.VK_TAB);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_TAB);
                    Main.LOG.info("Presiona tab");
                    break;

                //Presionar tecla de espacio
                case "space":
                    robot.keyPress(java.awt.event.KeyEvent.VK_SPACE);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_SPACE);
                    Main.LOG.info("Presiona espacio");
                    break;

                //Retroceso de etiqueta de texto
                case "shifttab":
                    robot.keyPress(java.awt.event.KeyEvent.VK_ALT);
                    robot.keyPress(java.awt.event.KeyEvent.VK_TAB);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_TAB);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_ALT);
                    Main.LOG.info("Presiona tab");
                    break;

                //Presionar tecla derecha de los cursores
                case "right":
                    robot.keyPress(java.awt.event.KeyEvent.VK_RIGHT);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_RIGHT);
                    Main.LOG.info("Presiona al lado derecho");
                    break;

                //Presionar tecla abajo de los cursores
                case "down":
                    robot.keyPress(java.awt.event.KeyEvent.VK_DOWN);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_DOWN);
                    Main.LOG.info("Presiona flecha abajo");
                    break;

                //Presionar tecla arriba de los cursores
                case "up":
                    robot.keyPress(java.awt.event.KeyEvent.VK_UP);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_UP);
                    Main.LOG.info("Presiona flecha ariba");
                    break;

                //Presionar tecla izquierda de los cursores
                case "left":
                    robot.keyPress(java.awt.event.KeyEvent.VK_LEFT);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_LEFT);
                    Main.LOG.info("Presiona flecha al lado izquierdo");
                    break;

                //Presionar tecla enter
                case "enter":
                    robot.keyPress(java.awt.event.KeyEvent.VK_ENTER);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
                    Main.LOG.info("Presiona enter");
                    break;

                //Presionar control + b para acceder a motor de busquedad dentro de un formulario
                case "buscar":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_B);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_B);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona control + b");
                    break;

                //Acceder directamente desde un formulario a la tabla
                case "acctabdir":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_F7);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_F7);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona control + f7");
                    break;

                //En cualquier etiqueta de un formulario que contenga una tabla asociada,
                // se puede realizar una busqueda por contenido de la siguiente manera:
                // Inicio palabra: %dato ; Fin plabra: dato% ; Cualquier parte de la palabra: %dato%
                case "busquedadporcont":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_F9);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_F9);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona control + f9");
                    break;

                //Formulario asociado a a tabla
                case "formasociado":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_F8);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_F8);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona control + f8");
                    break;

                //Limpia el formulario actual
                case "nuevo":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_U);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_U);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona control + u");
                    break;

                //Borrar registro selecionado
                case "borrado":
                    robot.keyPress(java.awt.event.KeyEvent.VK_F8);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_F8);
                    Main.LOG.info("Presiona f8");
                    break;
                //Borrar registro selecionado
                case "suprimir":
                    robot.keyPress(KeyEvent.VK_DELETE);
                    robot.keyRelease(KeyEvent.VK_DELETE);
                    Main.LOG.info("Presiona f8");
                    break;
                //Ayuda con el formulario
                case "ayuda":
                    robot.keyPress(java.awt.event.KeyEvent.VK_F1);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_F1);
                    Main.LOG.info("Presiona f1");
                    break;

                //Ayuda con el formulario
                case "AvPag":
                    robot.keyPress(java.awt.event.KeyEvent.VK_PAGE_UP);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_PAGE_UP);
                    Main.LOG.info("Presiona AvPag");

                    break;

                //Ayuda con el formulario
                case "RePag":
                    robot.keyPress(java.awt.event.KeyEvent.VK_PAGE_DOWN);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_PAGE_DOWN);
                    Main.LOG.info("Presiona RePag");
                    break;

                //Insertar linea
                case "insertline":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_ENTER);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona ctrl + enter");
                    break;

                //Copiar texto
                case "copiar":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_C);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_C);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona ctrl + c");
                    break;

                //Cortar texto
                case "cortar":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_X);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_X);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona ctrl + x");
                    break;

                //Pegar texto
                case "pegar":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_V);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_V);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona ctrl + v");
                    break;

                //Guardar formulario
                case "guardar":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_S);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_S);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona ctrl + s");
                    break;

                //Crear carpetas
                case "crear_carpeta":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_SHIFT);
                    robot.keyPress(java.awt.event.KeyEvent.VK_N);

                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_SHIFT);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_N);
                    Main.LOG.info("Presiona crtl + shift + n");

                    break;
                //Cierre crear carpeta

                //Crear carpetas con el Nombre
                case "crear_carpeta_Nombre":
                    String rutadeldirectorio, nombreDirectorio, valorComman;
                        try {
                          /*  if(cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null
                                    && cmd.getCommand() == "1"){
                                // Nombre de la carpeta
                                nombreDirectorio= cmd.getProperties().get(1);
                                // Capturo el valor que me trae en la propiedad y nos da la ruta donde se desea crear la carpeta.
                                rutadeldirectorio = cmd.getProperties().get(0);
                                // Creo una carpeta con el nombre que me pasa de la propiedad.
                                File directorio = new File(rutadeldirectorio+"/"+nombreDirectorio);
                                if (!directorio.exists()) {
                                    if (directorio.mkdirs()) {
                                        System.out.println("Directorio creado");
                                    } else {
                                        System.out.println("Error al crear directorio");
                                    }
                                }
                            }else*/
                            if (cmd.getProperties() != null ) {
                                // Uso el formato de fecha para luego pasarlo a la variable NombreDirectorio
                                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                                nombreDirectorio= dtf.format(LocalDateTime.now());
                                // Capturo el valor que me trae en la propiedad y nos da la ruta donde se desea crear la carpeta.
                                rutadeldirectorio = cmd.getProperties().get(0);
                                // Creo una carpeta con el nombre que me pasa de la propiedad.
                                File directorio = new File(rutadeldirectorio+"/"+nombreDirectorio);
                                if (!directorio.exists()) {
                                    if (directorio.mkdirs()) {
                                        System.out.println("Directorio creado");
                                    } else {
                                        System.out.println("Error al crear directorio");
                                    }
                                }
                                // obtenemos el tamaño del rectangulo
                                Rectangle rectangleTam = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                                try {
                                    Robot robot = new Robot();
                                    // tomamos una captura de pantalla( screenshot )
                                    BufferedImage bufferedImage = robot.createScreenCapture(rectangleTam);

                                    String nombreFichero=rutadeldirectorio+"/"+nombreDirectorio+File.separatorChar+"caputura.png";
                                    System.out.println("Generando el fichero: "+nombreFichero );
                                    FileOutputStream out = new FileOutputStream(nombreFichero);

                                    // esbribe la imagen a fichero
                                    ImageIO.write(bufferedImage, "png", out);

                                } catch (AWTException e) {
                                    e.printStackTrace();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            } else {
                                Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    break;
                //Cierre crear carpeta

                case "cerrar":
                    robot.keyPress(java.awt.event.KeyEvent.VK_ALT);
                    robot.keyPress(java.awt.event.KeyEvent.VK_F4);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_F4);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_ALT);
                    Main.LOG.info("Presiona alt + f4");
                    break;

                //Retorna al inico de una consulta
                case "inicio":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_HOME);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_HOME);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona alt + f4");
                    break;

                //Retorna al final de una consulta
                case "fin":
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_END);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_END);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona alt + f4");

                    break;

                //Funcion consumo de API AWS
                case "aws":
                    try {

                        if (cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null && cmd.getProperties().get(2) != null) {
                                String detectid= cmd.getProperties().get(0);
                                String imageid= cmd.getProperties().get(1);
                                String categoriaid= cmd.getProperties().get(2);

                                URL url;
                                URLConnection uc;
                                StringBuilder parsedContentFromUrl = new StringBuilder();
                                String urlString="https://53l8r4bx42.execute-api.us-east-1.amazonaws.com/Version1/rekognition?"+detectid+"&"+imageid+"&"+categoriaid;
                                System.out.println("Getting content for URl : " + urlString);
                                url = new URL(urlString);
                                uc = url.openConnection();
                                uc.connect();
                                uc.getInputStream();
                                BufferedInputStream in = new BufferedInputStream(uc.getInputStream());
                                int ch;
                                while ((ch = in.read()) != -1) {
                                    parsedContentFromUrl.append((char) ch);

                                }
                                StringBuilder Text =parsedContentFromUrl;
                                System.out.println("Texto : "+Text);
                                StringSelection stringSelection = new StringSelection(Text.toString());
                                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                                clipboard.setContents(stringSelection, stringSelection);

                                Robot robot = new Robot();
                                robot.keyPress(KeyEvent.VK_CONTROL);
                                robot.keyPress(KeyEvent.VK_V);
                                robot.keyRelease(KeyEvent.VK_V);
                                robot.keyRelease(KeyEvent.VK_CONTROL);


                        } else {
                            Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        Main.LOG.error("ERROR :" + e);
                    }
                break;

                case "searchtextimg":
                    try {
                        ReadImage read = new ReadImage();
                        output = read.readImage(cmd.getCommand(), Integer.parseInt(cmd.getProperties().get(0)), Integer.parseInt(cmd.getProperties().get(1)), Integer.parseInt(cmd.getProperties().get(2)), Integer.parseInt(cmd.getProperties().get(3)));
                    } catch (Exception e) {
                        output = "No";
                    }
                    break;

                // Devuelve el contenido en "value" del componente con id que trae el comando
                case "contains":
                case "no_contains":
                    Main.LOG.info("No constains");
                    if (cmd.getCommand() != null) {
                        try {
                            Main.LOG.info("Se pego");
                            this.driver.findElement(By.name(cmd.getCommand())).getAttribute("name");
                            Main.LOG.info("Se pego");

                            output = "Ok";
                        } catch (Exception e) {
                            output = "Vacio";
                        }
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command'");
                    }
                    break;

                case "validate_element":

                    if (cmd.getCommand() != null) {
                        try {

                            this.driver.manage().timeouts().implicitlyWait(4, TimeUnit.SECONDS);
                            try {
                                this.driver.findElement(By.name(cmd.getCommand()));
                                output = "Ok";
                            } catch (Exception e) {

                                output = "No se encontrò el elemento";
                                Main.LOG.error("No se encontrò el elemento");
                            } finally {
                                this.driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
                            }

                            /* WebDriverWait wait = new WebDriverWait(driver, 4);
                            wait.until(ExpectedConditions.elementToBeClickable(By.name(cmd.getCommand())));
                            //                   this.driver.findElement(By.name(cmd.getCommand()));
                            output = "Ok";*/
                        } catch (Exception e) {
                            output = "Vacio";
                            Main.LOG.error(output);
                        }

                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command'");
                    }
                    break;
                case "comprimir":
                        if(cmd.getProperties().get(0) != null){
                            String ruta = cmd.getProperties().get(0);

                            Zip zip = new Zip();
                            try {
                                zip.zip(new File(ruta), new File(ruta+".zip"));
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("ERROR AL COMPRIMIR EL ARCHIVO FUNCIONALIDAD COMPRIMIR " + e);
                            }
                        } else if (cmd.getProperties().get(0) == null) {
                            Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad INPUT_ID");
                        } else {
                            Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command'");
                        }
                    break;

                case "sendemail":
                    SendMail env = new SendMail();
                    if (cmd.getProperties().size() < 8) {
                        output = "N";
                    } else {
                        try {
                            env.send(cmd.getProperties().get(0),
                                    cmd.getProperties().get(1), cmd.getProperties().get(2), cmd.getProperties().get(3),
                                    cmd.getProperties().get(4), cmd.getProperties().get(5),cmd.getProperties().get(6),cmd.getProperties().get(7), null);
                            output = "S";
                        } catch (Exception e) {
                            output = "N";
                        }
                    }

                    break;
                //Funcion click_texto de API AWS
                case "click_texto":
                    BufferedImage captura = new Robot().createScreenCapture(
                            new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()) );

                    int leftLimit = 97; // letter 'a'
                    int rightLimit = 122; // letter 'z'
                    int targetStringLength = 10;
                    Random random = new Random();

                    String nombrecaptura = random.ints(leftLimit, rightLimit + 1)
                            .limit(targetStringLength)
                            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                            .toString();
                    System.out.println("Nombre captura : "+nombrecaptura);
                    File file = new File(nombrecaptura+ ".png");
                    ImageIO.write(captura, "png", file);

                    String bucketName = "bucketatlasid3";
                    String stringObjKeyName = nombrecaptura+".png";
                    String fileObjKeyName = nombrecaptura+".png";
                    String fileName = "C:\\RPAService\\"+nombrecaptura+".png";
                    String accesskey = "AKIAUO45GKMDO5C2JDUC";
                    String secretkeyid="q281dxHz+Q3vyS7vYwP1XqyLi1XoyYr3E39ZOW9i";

                    if (cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null && cmd.getProperties().get(2) != null ) {
                        String detectid= cmd.getProperties().get(0);
                        String palabraid= cmd.getProperties().get(1);
                        String categoriaid= cmd.getProperties().get(2);

                        try {

                            BasicAWSCredentials awsCreds = new BasicAWSCredentials(accesskey, secretkeyid);

                            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                                    .withRegion("us-east-1")
                                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                                    .build();

                            // Upload a text string as a new object.
                            s3Client.putObject(bucketName, stringObjKeyName, "Uploaded String Object");

                            // Upload a file as a new object with ContentType and title specified.
                            PutObjectRequest request = new PutObjectRequest(bucketName, fileObjKeyName, new File(fileName));
                            ObjectMetadata metadata = new ObjectMetadata();
                            metadata.setContentType("plain/text");
                            metadata.addUserMetadata("title", "someTitle");
                            request.setMetadata(metadata);
                            s3Client.putObject(request);

                            } catch (AmazonServiceException e) {
                                // The call was transmitted successfully, but Amazon S3 couldn't process
                                // it, so it returned an error response.
                                e.printStackTrace();
                                System.out.println("SUBIO EL ARCHIVO : "+e);
                            } catch (SdkClientException e) {
                                // Amazon S3 couldn't be contacted for a response, or the client
                                // couldn't parse the response from Amazon S3.
                                e.printStackTrace();
                                System.out.println("ERROR INTENTANDO SUBIR ARCHIVO2 : "+e);
                            }

                        try{
                            Main.LOG.info("NOMBRE DE LA VARIABLE DE LA CAPTURA : "+stringObjKeyName);
                            URL url;
                            URLConnection uc;
                            StringBuilder parsedContentFromUrl = new StringBuilder();
                            String urlString="https://53l8r4bx42.execute-api.us-east-1.amazonaws.com/Version1/rekognition?"+detectid+"&imageid="+stringObjKeyName+"&"+palabraid+"&"+categoriaid;
                            System.out.println("Getting content for URl : " + urlString);
                            url = new URL(urlString);
                            uc = url.openConnection();
                            uc.connect();
                            uc.getInputStream();
                            BufferedInputStream in = new BufferedInputStream(uc.getInputStream());
                            int ch;
                                while ((ch = in.read()) != -1) {
                                    parsedContentFromUrl.append((char) ch);

                                }
                            StringBuilder Text =parsedContentFromUrl;
                            String[] part1 = Text.toString().split("[,.:{}\\''\\[\\]]+");
                            String ejex="", ejey="", mostrar="";

                                    for(int i =0 ; i< part1.length; i++){
                                        if(part1[i] == part1[7]){
                                            String valorx = part1[i].substring(0,3);

                                            if(Integer.valueOf(valorx) > 889){
                                                double datox1 = Integer.parseInt(valorx)+(Integer.parseInt(valorx)*0.22);
                                                int datox = (int) datox1;
                                                ejex = String.valueOf(datox);
                                            }else{
                                                double datox1 = Integer.parseInt(valorx)+(Integer.parseInt(valorx)*0.35);
                                                int datox = (int) datox1;
                                                ejex = String.valueOf(datox);
                                            }

                                        }else if(part1[i] == part1[10]) {
                                            String valory = part1[i].substring(0,3);
                                            if(Integer.valueOf(valory)>800){
                                                double datoy1 = Integer.parseInt(valory)-(Integer.parseInt(valory)*0.20);
                                                int datoy = (int) datoy1;
                                                ejey = String.valueOf(datoy);
                                            }else {
                                                double datoy1 = Integer.parseInt(valory)-(Integer.parseInt(valory)*0.18);
                                                int datoy = (int) datoy1;
                                                ejey = String.valueOf(datoy);
                                            }
                                        }
                                    }
                                    Main.LOG.info("Eje x :" + ejex+" Eje Y :" + ejey);
                                    Main.LOG.info("Este es el valor de la api " + Text);

                                    StringSelection stringSelection = new StringSelection("Eje X : "+ejex+" Eje Y : "+ejey+" Palabra a buscar : "+palabraid);
                                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                                    clipboard.setContents(stringSelection, stringSelection);

                                    Robot robot = new Robot();
                                    robot.keyPress(KeyEvent.VK_CONTROL);
                                    robot.keyPress(KeyEvent.VK_V);
                                    robot.keyRelease(KeyEvent.VK_V);
                                    robot.keyRelease(KeyEvent.VK_CONTROL);

                                    if (ejex != null && ejey != null) {
                                        try {
                                            robot.mouseMove(Integer.valueOf(ejex), Integer.valueOf(ejey));
                                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                                            Main.LOG.info("Se realizo clic en el botón : "+palabraid);
                                        } catch (Exception e) {
                                            Main.LOG.info("No se pudo hacer clic en el botón : "+ palabraid);
                                        }

                                    } else {
                                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                                    }

                        }catch (AmazonServiceException e ){
                            Main.LOG.error("ERROR AL INTENTAR USAR EL API DE AWS VALIDAR EL SIGUIENTE ERROR : "+ e);
                        }

                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                    }
                    break;

                // Configura una nueva variable o reemplaza el valor de una existente             
                case "get_text":
                    Main.LOG.debug("Comando get_text");
                    if (cmd.getCommand() != null) {
                        try {
                            String location = this.replaceCommand(cmd.getCommand());
                            String text = this.driver.findElement(By.xpath(location)).getText();
                            Main.LOG.debug("location: " + location);
                            Main.LOG.debug("text: " + text);

                            if (cmd.getProperties().size() > 0) {
                                if (cmd.getProperties().size() > 1) {
                                    try {
                                        int posBegin = Integer.parseInt(cmd.getProperties().get(0));
                                        int posEnd = Integer.parseInt(cmd.getProperties().get(1));
                                        Main.LOG.debug("posBegin: " + posBegin);
                                        Main.LOG.debug("posEnd: " + posEnd);
                                        text = text.substring(posBegin, posEnd);

                                    } catch (NumberFormatException e) {
                                    }
                                } else {
                                    try {
                                        int pos = Integer.parseInt(cmd.getProperties().get(0));
                                        Main.LOG.debug("pos: " + pos);
                                        text = text.substring(pos);

                                    } catch (NumberFormatException e) {
                                    }
                                }
                            }

                            List<String> prop = new ArrayList<>();
                            prop.add("Descargar");
                            prop.add(text);
                            cmd.setProperties(prop);
                        } catch (Exception e) {
                            Main.LOG.error("No se puede obtener texto: " + e.getMessage());
                        }
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command' como nombre de la variable a incrementar");
                    }
                    break;
                case "mover_archivos":
                    if(cmd.getProperties().get(0) != null) {
                        String comando = cmd.getCommand();
                        String rutaOrigen = cmd.getProperties().get(0);
                        String rutaDestino = cmd.getProperties().get(1);

                        Path origenPath = FileSystems.getDefault().getPath(rutaOrigen);
                        Path destinoPath = FileSystems.getDefault().getPath(rutaDestino);

                        if (comando.equalsIgnoreCase("1")) {
                            // Uso el formato de fecha para luego pasarlo a la variable fechaActual
                            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                            String fechaActual = dtf.format(LocalDateTime.now());

                            destinoPath = FileSystems.getDefault().getPath(rutaDestino + fechaActual);
                        }

                        try {
                            Files.move(origenPath, destinoPath, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            System.err.println(e);
                        }
                    }else{
                        Main.LOG.error("El comando '" + cmd.getType() + "' asigne la ruta de origen");
                    }
                    break;
                case "fecha_actual":
                    //LocalDate fecha = LocalDate.now();
                    //cmd.setProperties(fecha);
                    robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
                    robot.keyPress(java.awt.event.KeyEvent.VK_COMMA);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_COMMA);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
                    Main.LOG.info("Presiona ctrl + ,");
                    break;
                    // Notificar en caso de no encontrar el comando
                default:
                    Main.LOG.warn("Comando '" + cmd.getType() + "' no se reconoce como un comando predefinido");
                    break;
            }
        } else {
            Main.LOG.info("Tipo de comando NULL, no es posible la ejecuci\u00f3n");
        }

        return output;
    }

    /**
     * Reemplaza todas las variables que logre encontrar en el comando
     *
     * @param cmd Texto en el que se reemplazan los datos
     * @return Texto con las variables reemplazadas
     */
    private String replaceCommand(String cmd) {
        try {
            while (cmd.contains("#!#")) {
                int varBegin = cmd.indexOf("#!#");
                int varEnd = cmd.indexOf("##") + 2;
                String variableName = cmd.substring(varBegin, varEnd);
                if (this.variables.containsKey(variableName)) {
                    cmd = cmd.replace(variableName, this.variables.get(variableName));
                } else {
                    cmd = cmd.replace(variableName, "");
                }
            }
        } catch (Exception e) {
            Main.LOG.error("No es posible reemplazar, variables malformadas", e);
        }

        return cmd;
    }

    /**
     * Reemplaza todas las variables que logre encontrar en las propiedades
     *
     * @param properties Listado de propiedades a reemplazar en el comando
     * @return Lista con las variables reemplazadas
     */
    private List<String> replaceProperties(List<String> properties) {
        List<String> output = new ArrayList<>();

        properties.forEach((prop) -> {
            output.add(this.replaceCommand(prop));
        });

        return output;
    }

    /**
     * Agrega todos los comandos enlistados en un hashmap indexado por llave
     *
     * @param commands Listado de comandos
     */
    public void setCommands(List<ExecutorCommandModel> commands) {
        for (ExecutorCommandModel cmd : commands) {
            this.commands.put(cmd.getId(), cmd);
        }
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

    public String getDriverLocation() {
        return driverLocation;
    }

    public void setDriverLocation(String driverLocation) {
        this.driverLocation = driverLocation;
    }

    public String getTimeSleep() {
        return timeSleep;
    }

    public void setTimeSleep(String timeSleep) {
        this.timeSleep = timeSleep;
    }
}
