package com.arquitecsoft.conectivity;

import com.arquitecsoft.connector.DashboardRequest;
import com.arquitecsoft.controller.Main;
import com.arquitecsoft.model.*;
import com.arquitecsoft.util.CreateFileOrDir;
import com.arquitecsoft.util.DDoSExecutor;
import com.arquitecsoft.util.WriteAndReadFile;
import com.google.gson.Gson;
import kong.unirest.Unirest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.io.Zip;
import org.openqa.selenium.support.ui.Select;
import org.w3c.dom.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.imageio.ImageIO;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

/**
 * Clase tipo Hilo que se encarga de ejecutar los comandos de un driver browser
 *
 * @author YMondragon
 */
public class ChromeDriverBrowser implements Runnable {

    private int pid;
    private int idPending;
    private boolean sendOutput;
    private int maxIterations;
    private WebDriver driver;
    private String downloadsLocation;
    private String driverExecutorLocation;
    private String timeSleep;
    private Robot robot;

    // Para las hojas de excel que tienen limite de maximos caracteres
    private int excelMaxCharsCell;
    private int excelMaxRows;

    private HashMap<String, ExecutorCommandModel> commands;
    private HashMap<String, String> variables;

    public ChromeDriverBrowser()  {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
        this.sendOutput = true;
        this.maxIterations = 10;
        this.driver = null;
        this.downloadsLocation = "";
        this.driverExecutorLocation = "";
        this.commands = new HashMap<String, ExecutorCommandModel>();
        this.variables = new HashMap<String, String>();
        this.excelMaxCharsCell = 32000;
        this.excelMaxRows = 1000000;
    }

    /**
     * Ejecuta los comandos y guarda en el log hora de inicio y finalizacion
     */
    @Override
    public void run() {
        Main.LOG.info("Iniciando ejecuci\u00f3n de comandos (ChromeDriverBrowser)...");
        HashMap<String, Object> chromePrefs = new HashMap<String, Object>();
        chromePrefs.put("profile.default_content_settings.popups", 0);
        chromePrefs.put("download.default_directory", this.downloadsLocation);//"C:\\Users\\Administrador.000\\Documents\\robot_downloads\\otra");

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", chromePrefs);
        System.setProperty("webdriver.chrome.driver", this.driverExecutorLocation);//"C:\\Windows\\chromedriver_win32 (3)\\chromedriver.exe");

        this.driver = new ChromeDriver(options);

        List<ExecutorCommandModel> commandsOut = new ArrayList<>();
        try {
            if (this.commands.size() > 0) {

                String nextCmd = "INIT";
                String outTerminalText = "";

                int iterations = 0;
                int waitIterations = 0;

                // Ejecutar comandos
                do {
                    ExecutorCommandModel cmd = new ExecutorCommandModel(this.commands.get(nextCmd));

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
                        Thread.sleep(Integer.valueOf(this.timeSleep));
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
        } finally {
            try {
                this.driver.close();
            } catch (Exception e) {/* NO-OP */
            }
        }

        Main.LOG.info("Finalizado.");
        Main.LOG.info("Commandos: " + commandsOut.size());
        Gson g = new Gson();
        Main.LOG.info(g.toJson(commandsOut));

        // Enviar resultados al dashboard
        ExecutorBrowserModel bodyData = new ExecutorBrowserModel();
        bodyData.setPid(this.pid);
        bodyData.setIdPending(this.idPending);
        bodyData.setCommands(commandsOut);
        bodyData.setSendOutput(this.sendOutput);
        bodyData.setDownloadLocation(this.downloadsLocation);
        bodyData.setDriverLocation(this.driverExecutorLocation);

        DashboardRequest request = new DashboardRequest(Main.APP_DASHBOARD_HOST, Main.APP_DASHBOARD_USER, Main.APP_DASHBOARD_PASS);
        Main.LOG.info("Enviando datos al Dashboard...");
        request.sendData("/api/saveData", g.toJson(bodyData));
        Main.LOG.info("Proceso finalizado.");

    }

    /**
     * Ejecuta el Driver de Chrome dependiendo del tipo de comando
     */
    private String runCommandByType(ExecutorCommandModel cmd) throws Exception {
        String output = "";
        if (cmd.getType() != null && !cmd.getType().trim().equals("")) {
            switch (cmd.getType()) {
                // Abre una URL en la pesta??a de la instancia del navegador
                case "get":
                    if (cmd.getProperties().get(0) != null) {
                        this.driver.get(cmd.getProperties().get(0));
                        this.driver.manage().window().maximize();
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad URL");
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
                    //FUNCIONALIDAD QUE PERMITE EL ENVIO Y CAPTURA DE UNA IMAGEN POR CORREO DESARROLLADO POR JUAN DAVID MU??OZ L??DER T??CNICO DE  PRODUCTOS
                case "captura_envio":
                    if(cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null && cmd.getProperties().get(2) != null
                                && cmd.getProperties().get(3) != null && cmd.getCommand() != null){
                           // variables tomadas de las propiedades del front
                            final String email =  cmd.getProperties().get(0);
                            final String  password =  cmd.getProperties().get(1);
                            String corre_destino = cmd.getProperties().get(2);
                            String cuerpo = cmd.getProperties().get(3);
                            String asunto = cmd.getCommand();

                            //Variables del Backend
                            String nombrecaptura = "captura";

                            Properties props = new Properties();
                            props.put("mail.smtp.auth", "true");
                            props.put("mail.smtp.starttls.enable", "true");
                            props.put("mail.smtp.host", "smtp.office365.com");
                            props.put("mail.smtp.port", "587");
                            //FUNCIONALIDAD DE CAPTURA DE LA IMAGEN
                            BufferedImage captura = new Robot().createScreenCapture(
                                    new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()) );
                            File file = new File(nombrecaptura+ ".png");
                            ImageIO.write(captura, "png", file);

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
                                //adjunto.setDataHandler(new DataHandler(new FileDataSource("D:\\RPA\\Codigo\\Motor\\captura.png")));
                                adjunto.setDataHandler(new DataHandler(new FileDataSource("C:\\RPAService\\captura.png")));
                                adjunto.setFileName("captura.png");

                                multiParte.addBodyPart(texto);
                                multiParte.addBodyPart(adjunto);

                                message.setContent(multiParte);

                                Transport t = session.getTransport("smtp");
                                t.connect(email,password);
                                t.sendMessage(message,message.getAllRecipients());

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
                // Funcion de crear carpeta y tomar una captura de pantalla , guardandola en el directorio creado, recordar pasar en la propiedad
                // la ruta del directorio- Funcion Creada Jmunoz
                case "crear_carpeta_Nombre":
                    String rutadeldirectorio, nombreDirectorio, valorComman;
                    try {

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
                            // obtenemos el tama??o del rectangulo
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
                case "mkdir":
                    if (cmd.getProperties().get(0) != null) {
                        CreateFileOrDir c = new CreateFileOrDir();
                        try {
                            c.onlinecreatedir(cmd.getProperties().get(0));
                        } catch (IOException e) {
                            Main.LOG.error("" + e);
                        }
                    } else {
                        Main.LOG.error("A el comando '" + cmd.getType() + "' asignele una ruta de ce carpeta");
                    }
                    break;

                case "copydir":
                     Main.LOG.debug("Se realizara la copia de archivo");
                    if (cmd.getProperties().get(0) != null && cmd.getProperties().get(1) != null) {
                        File srcDir = new File(cmd.getProperties().get(0));
                        File destDir = new File(cmd.getProperties().get(1));
                        if (srcDir.exists()) {
                            if (destDir.exists()) {
                                try {
                                    FileUtils.copyDirectory(srcDir, destDir);
                                    FileUtils.cleanDirectory(srcDir);
                                } catch (IOException e) {
                                    Main.LOG.error("" + e);
                                }
                            } else {
                                Main.LOG.error("La ruta de destino: '" + cmd.getProperties().get(1) + "'es erronea, asignele una ruta existente");
                            }
                        } else {
                            Main.LOG.error("La ruta: '" + cmd.getProperties().get(0) + "' donde se extraen los archivos es erronea, asignele una ruta existente");
                        }

                    } else {
                        Main.LOG.error("Una de las propiedades del comando esta nula");
                    }
                    break;

                // Limpia el contenido un Input de tipo texto
                case "clear":
                    if (cmd.getProperties().get(0) != null) {
                        this.driver.get(cmd.getProperties().get(0));
                        this.driver.findElement(By.id(cmd.getProperties().get(0))).clear();
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad INPUT_ID");
                    }
                    break;

                // Escribe el 'command' en un Input de tipo texto
                case "normal":
                    if (cmd.getProperties().get(0) != null && cmd.getCommand() != null) {
                        WebElement element=null;
                        try {
                            element = this.driver.findElement(By.id(cmd.getProperties().get(0)));
                        }catch (Exception e){
                            element = this.driver.findElement(By.name(cmd.getProperties().get(0)));
                        }
                        element.clear();
                        element.sendKeys(cmd.getCommand());
                    } else if (cmd.getProperties().get(0) == null) {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad INPUT_ID");
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command'");
                    }
                    break;
                // Manda un enter a la GUI
                case "key_enter":
                    if (cmd.getProperties().get(0) != null) {
                        WebElement element=null;
                        try {
                            element = this.driver.findElement(By.id(cmd.getProperties().get(0)));
                        }catch (Exception e){
                            element = this.driver.findElement(By.name(cmd.getProperties().get(0)));
                        }
                        //element.clear();
                        element.sendKeys(Keys.ENTER);
                    } else if (cmd.getProperties().get(0) == null) {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                    }
                    break;
                // Escribe el 'command' en un Input de tipo texto
                // Manda un enter a la GUI
               //Presionar tecla enter
                case "enter":
                    robot.keyPress(java.awt.event.KeyEvent.VK_ENTER);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
                    Main.LOG.info("Presiona enter");
                    break;
                // Escribe el 'command' en un Input de tipo texto

                //Presionar tecla abajo de los cursores
                case "down":
                    robot.keyPress(java.awt.event.KeyEvent.VK_DOWN);
                    robot.keyRelease(java.awt.event.KeyEvent.VK_DOWN);
                    Main.LOG.info("Presiona flecha abajo");
                    break;

                case "dropdown":
                    if (cmd.getProperties().get(0) != null && cmd.getCommand() != null) {
                        Select dropdown = new Select(this.driver.findElement(By.id(cmd.getProperties().get(0))));
                        dropdown.selectByValue(cmd.getCommand());
                    } else if (cmd.getProperties().get(0) == null) {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad INPUT_ID");
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command'");
                    }
                    break;

                // Presiona Click en el elemento encontrado por ID
                case "click":
                    if (cmd.getProperties().get(0) != null) {
                        String location = this.replaceCommand(cmd.getProperties().get(0));
                        Main.LOG.info("Clic en " + location);
                        this.driver.findElement(By.id(location)).click();
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad ELEMENT_ID");
                    }
                    break;

                // Presiona Click en el elemento encontrado por texto o etiquetas HTML
                case "click_text":
                    if (cmd.getProperties().get(0) != null) {
                        String location = this.replaceCommand(cmd.getProperties().get(0));
                        Main.LOG.info("Clic texto en " + location);
                        Actions ac = new Actions(this.driver);
                        WebElement we = this.driver.findElement(By.xpath(location));
                        ac.moveToElement(we).click().perform();
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere la propiedad HTML_TEXT");
                    }
                    break;

                // Devuelve el contenido en "value" del componente con id que trae el comando
                case "contains":
                case "no_contains":
                    if (cmd.getProperties().size() > 0 && cmd.getCommand() != null) {
                        System.out.println("Encontrando elemento " + cmd.getCommand());
                        output = this.driver.findElement(By.id(cmd.getCommand())).getAttribute("value");
                    } else if (cmd.getProperties().size() <= 0) {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere datos en propiedades");
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command'");
                    }
                    break;

                // Se devuelve en la pagina web "atras"
                case "back":
                    this.driver.navigate().back();
                    break;

                // Configura una nueva variable o reemplaza el valor de una existente
                case "set_variable":
                    if (cmd.getProperties().size() > 0 && cmd.getCommand() != null) {
                        // Las variables se rodean de la etiqueta #!#
                        String variableName = "#!#" + cmd.getCommand() + "##";

                        // Se asigna <elemento>,<valor> al array de variables
                        this.variables.put(variableName, cmd.getProperties().get(0));

                        Main.LOG.info("Variable " + variableName + " a??adida con valor: " + cmd.getProperties().get(0));
                    } else if (cmd.getProperties().size() <= 0) {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere el valor de la variable en propiedades");
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command' como nombre de la variable");
                    }
                    break;

                // Incrementa el valor de una variable en 1, solamente si es tipo entera
                case "increment_variable":
                    if (cmd.getCommand() != null) {
                        try {
                            Main.LOG.info("Nombre de variable: " + cmd.getCommand());
                            int newValue = Integer.parseInt(this.variables.get(cmd.getCommand()));
                            this.variables.replace(cmd.getCommand(), String.valueOf(newValue + 1));
                            Main.LOG.info("Incrementada variable " + cmd.getCommand() + ", valor actual: " + (newValue + 1));
                        } catch (Exception e) {
                            Main.LOG.error("No se puede incrementar e: ", e);
                        }
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command' como nombre de la variable a incrementar");
                    }
                    break;

                // Comparar variable de comando con la lista de elementos de la propiedad
                case "variable_greater_than_sizeof":
                case "variable_greater_equal_sizeof":
                case "variable_lower_than_sizeof":
                case "variable_lower_equal_sizeof":
                    if (cmd.getProperties().size() > 0 && cmd.getCommand() != null) {
                        try {
                            List<WebElement> rows = this.driver.findElements(By.xpath(cmd.getCommand())); //"//table[@class='ListView']/tbody/tr"));
                            output = String.valueOf(rows.size());
                        } catch (Exception e) {
                        }
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command' como nombre de la variable a incrementar");
                    }
                    break;

                // Comparar variable de comando con numero (o variable) de la propiedad
                case "variable_greater_than":
                case "variable_greater_equal":
                case "variable_lower_than":
                case "variable_lower_equal":
                    if (cmd.getCommand() != null) {
                        try {
                            String compare = this.replaceCommand(cmd.getCommand());
                            output = String.valueOf(compare);
                            Main.LOG.info("Comando variable_lower_equal");
                            Main.LOG.info("comando: " + cmd.getCommand());
                            Main.LOG.info("output: " + output);
                        } catch (Exception e) {
                        }
                    } else {
                        Main.LOG.error("El comando '" + cmd.getType() + "' requiere 'command' como nombre de la variable a incrementar");
                    }
                    break;

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

                                    } catch (Exception e) {
                                    }
                                } else {
                                    try {
                                        int pos = Integer.parseInt(cmd.getProperties().get(0));
                                        Main.LOG.debug("pos: " + pos);
                                        text = text.substring(pos);

                                    } catch (Exception e) {
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
                case "opentab":
                    System.out.println("Ejecutando opentab "+cmd.getCommand());
                        if (cmd.getCommand()==null) {
                            output = "N";
                        } else {
                            try {
                                ((JavascriptExecutor)driver).executeScript("window.open();");
                                ArrayList<String> tabs = new ArrayList<String> (driver.getWindowHandles());
                                this.driver.switchTo().window(tabs.get(1)); //switches to new tab
                                this.driver.get(cmd.getCommand());

                                output = "S";
                            } catch (Exception e) {
                                output = "Vacio";
                                Main.LOG.error(output);
                                e.printStackTrace();
                            }
                        }


                    break;

                // Inicia un ataque de denegaci??n de servicio distrubuido (DDoS)
                // Command => Host / URL del objetivo a atacar
                // prop(0) => Ruta absoluta del archivo de generaci??n de datos (Ubicaci??n y Nombre del reporte)
                // prop(1) => Duraci??n del ataque (En minutos, s??n v??lidos los valores decimales ej: 0.5)
                // prop(2) => N??mero de hilos (valor entero)
                // prop(3) => [No obligatorio] Cantidad de ataques a realizar por cada hilo (por defecto -1)
                // prop(4) => [No obligatorio] C??digos HTTP bloqueantes separados por comas (Por defecto: 0, 502)
                case "ddos":
                    Main.LOG.info("Ejecutando " + cmd.getType());
                    String host = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (host != null && !"".equals(host.trim())) {
                        if (cmd.getProperties().size() >= 3 && cmd.getProperties().get(0) != null &&
                                cmd.getProperties().get(1) != null && cmd.getProperties().get(2) != null) {

                            boolean parametrosValidos = true;
                            String respuesta = cmd.getProperties().get(0);

                            int hilosCant = -1;
                            if (!"".equals(cmd.getProperties().get(2).trim())) {
                                try {
                                    hilosCant = Integer.parseInt(cmd.getProperties().get(2));
                                } catch (Exception e) {
                                    Main.LOG.error("Cantidad de hilos para el ataque \"" + cmd.getProperties().get(2) + "\" no es reconocido como un n??mero v??lido");
                                    parametrosValidos = false;
                                }
                            } else {
                                Main.LOG.error("Cantidad de hilos es NULL");
                                parametrosValidos = false;
                            }

                            double tiempoMinutos = -1;
                            if (!"".equals(cmd.getProperties().get(1).trim())) {
                                try {
                                    tiempoMinutos = Double.parseDouble(cmd.getProperties().get(1));
                                } catch (Exception e) {
                                    Main.LOG.error("Tiempo de duraci??n del ataque \"" + cmd.getProperties().get(1) + "\" no es reconocido como un n??mero v??lido");
                                    parametrosValidos = false;
                                }
                            }

                            long intentos = -1;
                            if (!"".equals(cmd.getProperties().get(3).trim())) {
                                try {
                                    intentos = Long.parseLong(cmd.getProperties().get(3));
                                } catch (Exception e) {
                                    Main.LOG.error("Cantidad de intentos para el ataque \"" + cmd.getProperties().get(3) + "\" no es reconocido como un n??mero v??lido");
                                    parametrosValidos = false;
                                }
                            }

                            int[] fail = new int[1];
                            if (!"".equals(cmd.getProperties().get(4).trim())) {
                                try {
                                    String[] codigosStr = cmd.getProperties().get(4).split(",");
                                    fail = new int[codigosStr.length];
                                    for (int i = 0; i < codigosStr.length; i++) {
                                        fail[i] = Integer.parseInt(codigosStr[i]);
                                    }
                                } catch (Exception e) {
                                    Main.LOG.error("Los c??digos de salida deben ser n??meros enteros separados por comas, recibido: \"" + cmd.getProperties().get(4) + "\"");
                                    parametrosValidos = false;
                                }
                            } else {
                                fail[0] = 502;
                            }

                            if (tiempoMinutos == -1 && intentos == -1) {
                                Main.LOG.error("Tiempo e intentos para el ataque son nulos, no se inicia ejecui??n...");
                                parametrosValidos = false;
                            }

                            if (parametrosValidos) {
                                long tExec = Math.round(tiempoMinutos * 60.0 * 1000.0);
                                Unirest.config().reset()
                                        .socketTimeout(500)
                                        .connectTimeout(2000);
                                DDoSReportModel modelo = new DDoSReportModel(host, intentos, tExec);
                                try {
                                    WriteAndReadFile files = new WriteAndReadFile();
                                    Gson g = new Gson();
                                    files.Write(respuesta, g.toJson(modelo));
                                } catch (Exception e) {
                                    Main.LOG.error("No se ha podido guardar el json final", e);
                                }
                                Thread[] hilos = new Thread[hilosCant];
                                DDoSExecutor exec1 = new DDoSExecutor(host, intentos, tExec, fail, respuesta);
                                if (intentos > 0) {
                                    Main.LOG.info("Iniciando para ejecutar durante " + intentos + " veces hacia: " + host);
                                } else {
                                    Main.LOG.info("Iniciando para ejecutar durante " + tExec + "ms (" + tiempoMinutos + "min) hacia: " + host);
                                }

                                Main.LOG.info("Inicializando " + hilosCant + " hilos...");
                                int porcentaje = 0;
                                int incremento = 10;
                                for (int i = 0; i < hilosCant; i++) {
                                    Thread t = new Thread(exec1);
                                    t.start();
                                    hilos[i] = t;
                                    // Logs
                                    double total = (double) i / (double) hilosCant;
                                    if ((total * 100)+0.1 >= porcentaje) {
                                        Main.LOG.info("(" + (int)(total * 100) + "%) Generados " + i + " de " + hilosCant + " hilos...");
                                        porcentaje+=incremento;
                                    } else if (i + 1 == hilosCant) {
                                        Main.LOG.info("(100%) Generados " + hilosCant + " hilos.");
                                    }
                                }

                                while(true) {
                                    Main.LOG.info("Esperando que terminen los hilos...");
                                    boolean procesoFinalizado = true;
                                    for (Thread hilo : hilos) {
                                        if (hilo.isAlive()) {
                                            procesoFinalizado = false;
                                            break;
                                        }
                                    }
                                    if (procesoFinalizado) {
                                        break;
                                    }

                                    try {
                                        Thread.sleep(5000);
                                    } catch (Exception ignored) {}
                                }

                                // unir archivos mediante los pid
                                Main.LOG.info("Los " + hilos.length + " hilos han finalizado, uniendo archivos...");

                                WriteAndReadFile files = new WriteAndReadFile();
                                Gson g = new Gson();
                                for (Thread hilo : hilos) {
                                    String nombreArch = respuesta + "." + hilo.getId();

                                    try {
                                        String json = files.Read(nombreArch);
                                        DDoSReportModel salidaHilo = g.fromJson(json, DDoSReportModel.class);
                                        long aDone = modelo.getAttacksCountTotalDone();
                                        modelo.setAttacksCountTotalDone(aDone + salidaHilo.getAttacksCountTotalDone());

                                        if (salidaHilo.getAttacksCountTotalSuccess() > 0) {
                                            long ok = modelo.getAttacksCountTotalSuccess();
                                            modelo.setAttacksCountTotalSuccess(ok + 1);
                                            modelo.getAttacksSuccess().add(hilo.getId());
                                        }

                                        modelo.getFinalCodes().addAll(salidaHilo.getFinalCodes());
                                        modelo.getFinalResponses().addAll(salidaHilo.getFinalResponses());
                                        modelo.getFinalTimes().addAll(salidaHilo.getFinalTimes());
                                        modelo.getFinalCicles().addAll(salidaHilo.getFinalCicles());
                                        modelo.getFinalMgs().addAll(salidaHilo.getFinalMgs());
                                        //modelo.getFinalRiesgos().addAll();

                                        Main.LOG.info("Eliminando archivo " + nombreArch);
                                        File f = new File(nombreArch);
                                        f.delete();

                                    } catch (Exception e) {
                                        Main.LOG.error("Error al leer archivo de hilo " + hilo.getId(), e);
                                    }

                                }

                                try {
                                    modelo.setTimeFinished(new Date());
                                    files.Write(respuesta, g.toJson(modelo));
                                    output = "Ok";
                                } catch (Exception e) {
                                    Main.LOG.error("No se ha podido guardar el json final: " + g.toJson(modelo), e);
                                }
                            }

                        } else {
                            Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar las propiedades (0) URL del objetivo, (1) Duraci??n del ataque, (2) N??mero de hilos");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un HOST/URL en el comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON DDoS a un archivo excel XLSX
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          tiempo_solicitado[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_hilos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "ddos_report_xlsx":
                    String fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                DDoSReportModel modelo = g.fromJson(json, DDoSReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".xlsx";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".xlsx";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }

                                int indexSheet = 0;
                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"... Los datos se guardar??n en la hoja: " + (indexSheet+1));

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }
                                    XSSFWorkbook workbook = new XSSFWorkbook();
                                        XSSFSheet sheet = workbook.createSheet("Reporte");

                                    sheet.setColumnWidth(1, 256 * 12); // Configurando ancho como para 12 caracteres
                                    sheet.setColumnWidth(2, 256 * 35); // Configurando ancho como para 35 caracteres
                                    sheet.setColumnWidth(3, 256 * 20); // Configurando ancho como para 20 caracteres
                                    sheet.setColumnWidth(4, 256 * 20); // Configurando ancho como para 20 caracteres
                                    sheet.setColumnWidth(5, 256 * 20); // Configurando ancho como para 20 caracteres
                                    sheet.setColumnWidth(6, 256 * 20); // Configurando ancho como para 20 caracteres
                                    sheet.setColumnWidth(7, 256 * 20); // Configurando ancho como para 20 caracteres

                                    Main.LOG.info("Escribiendo columnas...");
                                    // Aqua background
                                    CellStyle style = workbook.createCellStyle();
                                    style.setFillForegroundColor(IndexedColors.AQUA.getIndex());

                                    int offset = 0;
                                    Row row = sheet.createRow(offset);
                                    //Cell cell = row.createCell(0);
                                    Cell cell = row.createCell((short) 1);
                                    cell.setCellStyle(style);
                                    //cell.setCellValue("Resultados de la ejecuci??n del ataque de denegaci??n de servicios (DDoS)");
                                    /*CellRangeAddress region = new CellRangeAddress(offset, offset, 1, 1);
                                    sheet.addMergedRegion(region);*/
                                    CellStyle centerBold = workbook.createCellStyle();
                                    centerBold.setAlignment(HorizontalAlignment.CENTER);
                                    XSSFFont bold = workbook.createFont();
                                    bold.setBold(true);
                                    centerBold.setFont(bold);
                                    cell.setCellStyle(centerBold);
                                    Random random_method = new Random();

                                        // LISTADO DE RIESGOS SEG??N LA NORMATIVA ISO 27001
                                        ArrayList<String> ListaRiegos = new ArrayList<String>();
                                            ListaRiegos.add("R1 Acceso a los sistemas de informaci??n o recursos tecnol??gicos por usuarios no autorizados.");
                                            ListaRiegos.add("R4 Da??os en la informaci??n por accesos no autorizados.");
                                            ListaRiegos.add("R6 Fuga de Informaci??n.");
                                            ListaRiegos.add("R18 Da??o al centro de procesamiento de datos y/o servidores.");
                                            ListaRiegos.add("R19 Indisponibilidad del servidor de base de datos y/o aplicaciones.");
                                            ListaRiegos.add("R34 Ataques a sitios o aplicaciones web.");

                                        // LISTADO DE AMENAZAS SEG??N LA NORMATIVA ISO 27001
                                        ArrayList<String> ListaAmenazas = new ArrayList<String>();
                                            ListaAmenazas.add("A27 La manipulaci??n de software.");
                                            ListaAmenazas.add("A42 La negaci??n de las acciones.");
                                            ListaAmenazas.add("A44 Ataques de identificaci??n y autenticaci??n de usuarios.");
                                            ListaAmenazas.add("A48 Desbordamiento de buffer de memoria.");
                                            ListaAmenazas.add("A53 Ataques de denegaci??n de servicios.");
                                            ListaAmenazas.add("A59 Recolecci??n de informaci??n a trav??s de fuentes abiertas.");
                                            ListaAmenazas.add("A60 Acceso no autorizado a los equipos de c??mputo y/o servidores.");
                                            ListaAmenazas.add("A61 Ataques a contrase??as de los equipos de c??mputo y/o servidores.");
                                            ListaAmenazas.add("A62 Ataques de denegaci??n de servicios.");
                                            ListaAmenazas.add("A63 Ataques de ejecuci??n de c??digo.");

                                        // LISTADO DE VULNERABILIDADES SEG??N LA NORMATIVA ISO 27001
                                        ArrayList<String> ListaVulnerabilidades= new ArrayList<String>();
                                            ListaVulnerabilidades.add("V59 Fallas o ausencia de procedimientos de monitoreo y/o seguimiento de los recursos de informaci??n.");
                                            ListaVulnerabilidades.add("V60 Fallas o ausencia de auditor??as peri??dicas.");
                                            ListaVulnerabilidades.add("V61 Fallas o ausencia en los procedimientos de identificaci??n y valoraci??n de riesgos.");
                                            ListaVulnerabilidades.add("V67 Fallas o ausencia de un procedimiento establecido para la supervisi??n del registro del SGSI y ciberseguridad.");

                                        //LISTADO DE CONTROLES
                                        ArrayList<String> ListadoControles = new ArrayList<String>();
                                            ListadoControles.add(" A.8.3.3 - Transferencia de medios f??sicos: Los medios que contienen informaci??n se deben proteger contra acceso no autorizado, uso indebido o corrupci??n durante el transporte.");
                                            ListadoControles.add(" A.9.2.1 - Registro y cancelaci??n del registro de usuarios: Se debe implementar un proceso formal de registro y de cancelaci??n de registro de usuarios, para posibilitar la asignaci??n de los derechos de acceso.");
                                            ListadoControles.add(" A.11.1.4 - Protecci??n contra amenazas externas y ambientales: Se debe dise??ar y aplicar protecci??n f??sica contra desastres naturales, ataques maliciosos o accidentales.");
                                            ListadoControles.add(" A.12.1.3 - Gesti??n de capacidad: Se debe hacer seguimiento al uso de recursos, hacer los ajustes, y hacer proyecciones de los requisitos de capacidad futura, para asegurar el desempe??o requerido del sistema.");
                                            ListadoControles.add(" A.12.2.1 - Controles contra c??digos maliciosos: Se deben implementar controles de detecci??n, de prevenci??n y de recuperaci??n, combinados con la toma de conciencia apropiada de los usuarios, para proteger contra c??digos maliciosos.");
                                            ListadoControles.add(" A.12.3.1 - Respaldo de la informaci??n: Se deben hacer copias de respaldo de la informaci??n, software e im??genes de los sistemas, y ponerlas a prueba regularmente de acuerdo con una pol??tica de copias de respaldo acordadas.");
                                            ListadoControles.add(" A.12.4.1 - Registro de eventos: Se deben elaborar, conservar y revisar regularmente los registros acerca de actividades del usuario, excepciones, fallas y eventos de seguridad de la informaci??n.");
                                            ListadoControles.add(" A.14.1.3 - Protecci??n de transacciones de los servicios de las aplicaciones: La informaci??n involucrada en las transacciones de los servicios de las aplicaciones se debe proteger para evitar la transmisi??n incompleta, el enrutamiento errado, la alteraci??n no autorizada de mensajes, la divulgaci??n no autorizada, y la duplicaci??n o reproducci??n de mensajes no autorizada.  ");
                                            ListadoControles.add(" A.16.1.3 - Reporte de debilidades de seguridad de la informaci??n: Se debe exigir a todos los empleados y contratistas que usan los servicios y sistemas de informaci??n de la organizaci??n, que observen y reporten cualquier debilidad de seguridad de la informaci??n observada o sospechada en los sistemas o servicios.");

                                        ArrayList<String> ListadoRemediaciones = new ArrayList<String>();
                                            ListadoRemediaciones.add("Ubicar el servidor web en una zona desmilitarizada (entre cortafuegos), tambi??n llamada DMZ, evitando as?? que un intruso pueda acceder a la red interna si vulnera el servidor web;");
                                            ListadoRemediaciones.add("implementar un sistema de detecci??n y prevenci??n de intrusiones (IDS/IPS) que monitorizan las conexiones y nos alerta si detecta intentos de acceso no autorizados o mal uso de protocolos;");
                                            ListadoRemediaciones.add("utilizar un dispositivo o software con funcionalidad mixta (antivirus, cortafuegos y otras), como un UTM que permite gestionar de manera unificada la mayor??a de ciberamenazas que pueden afectar");
                                            ListadoRemediaciones.add("Redundancia y balance de carga - (La redundancia consiste en tener el activo duplicado en m??s de un servidor y el balanceado de carga permite que se asigne a un servidor u otro en funci??n de la carga de trabajo que est?? soportando. Esta medida reduce los riesgos de sufrir uno de estos ataques, ya que al tener m??s de un servidor se reducir?? la posibilidad de que se detenga debido a la sobrecarga. Adem??s, aporta otras ventajas como la tolerancia a los fallos, ya que si un servidor cae, el total del trabajo lo asumir??a el otro servidor.)");
                                            ListadoRemediaciones.add("Soluciones de seguridad basadas en la nube - Una de las soluciones que cualquier servicio web considerado cr??tico debe tener es un cortafuegos de aplicaci??n o WAF por sus siglas en ingl??s Web Application Firewall. ");
                                            ListadoRemediaciones.add("Validaci??n y gesti??n de actualizaciones al sistema operativo, softwares en el sistema entre otros, plantear un monitoreo de cantidad de solicitudes por minuto, identificando y bloqueando IPS, que gestionen mas de N cantidad de solicitudes por minuto.");
                                            ListadoRemediaciones.add("Puede validar la siguiente pagina la cual le ayudara a gestionar esta vulnerabilidad : https://aws.amazon.com/es/shield/ddos-attack-protection/");

                                        row = sheet.createRow(1);// arriba o abajo
                                        cell = row.createCell(4); // der o izq
                                        cell.setCellValue("Reporte :");
                                        cell = row.createCell(5);
                                        cell.setCellValue("(DDoS) Realizado CiberBot ATLAS ");

                                // host[=true/false] (por defecto true)
                                    if (att.get("host") == null || !att.get("host").equals("false")) {
                                        row = sheet.createRow(2);// arriba o abajo
                                        cell = row.createCell(4); // der o izq
                                        cell.setCellValue("Host:");
                                        cell = row.createCell(5);// der o izq
                                        cell.setCellValue(modelo.getHost());
                                       /* region = new CellRangeAddress(offset + 2, offset + 2, 5, 6);
                                        sheet.addMergedRegion(region);*/
                                        //offset++;
                                    }

                                    // fecha_inicio[=true/false] (por defecto true)
                                   if (att.get("fecha_inicio") == null || !att.get("fecha_inicio").equals("false")) {
                                        row = sheet.createRow(3);
                                        cell = row.createCell(4);
                                        cell.setCellValue("Hora de Inicio:");
                                        cell = row.createCell(5);
                                        cell.setCellValue(sdf.format(modelo.getTimeStarted()));
                                       /* region = new CellRangeAddress(offset + 2,offset + 2, 1, 5);
                                        sheet.addMergedRegion(region);*/
                                        //offset++;
                                    }

                                    // fecha_fin[=true/false] (por defecto true)
                                        if (att.get("fecha_fin") == null || !att.get("fecha_fin").equals("false")) {
                                        row = sheet.createRow(4);
                                        cell = row.createCell(4);
                                        cell.setCellValue("Hora Fin:");
                                        cell = row.createCell(5);
                                        cell.setCellValue(sdf.format(modelo.getTimeFinished()));
                                        /*region = new CellRangeAddress(offset + 2,offset + 2, 1, 5);
                                        sheet.addMergedRegion(region);*/
                                        //offset++;
                                    }

                                    // cant_solicitados[=true/false] (por defecto true)
                                    if (modelo.getAttacksCountTotalRequested() > 0 && (att.get("cant_solicitados") == null || !att.get("cant_solicitados").equals("false"))) {
                                        row = sheet.createRow(  5);
                                        cell = row.createCell(4);
                                        cell.setCellValue("Ataques solicitados:");
                                        cell = row.createCell(5);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalRequested()));
                                        /*region = new CellRangeAddress(offset + 3,offset + 3, 0, 1);
                                        sheet.addMergedRegion(region);
                                        offset++;*/
                                    }

                                    // tiempo_solicitado[=true/false] (por defecto true)
                                    if (modelo.getAttacksTimeTotalRequested() > 0 && (att.get("tiempo_solicitado") == null || !att.get("tiempo_solicitado").equals("false"))) {
                                        row = sheet.createRow( 6);
                                        cell = row.createCell(4);
                                        cell.setCellValue("Tiempo de ataque:");
                                        cell = row.createCell(5);
                                        cell.setCellValue(modelo.getAttacksTimeTotalRequested() + " ms");
                                        /*region = new CellRangeAddress(offset + 3,offset + 3, 0, 1);
                                        sheet.addMergedRegion(region);
                                        offset++;*/
                                    }

                                    // cant_realizados[=true/false] (por defecto true)
                                    if (att.get("cant_realizados") == null || !att.get("cant_realizados").equals("false")) {
                                        row = sheet.createRow(7);
                                        cell = row.createCell(4);
                                        cell.setCellValue("Ataques realizados:");
                                        cell = row.createCell(5);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalDone()));
                                        /*region = new CellRangeAddress(offset + 3,offset + 3, 0, 1);
                                        sheet.addMergedRegion(region);
                                        offset++;*/
                                    }

                                    // cant_exitosos[=true/false] (por defecto true)
                                    if (att.get("cant_exitosos") == null || !att.get("cant_exitosos").equals("false")) {
                                        row = sheet.createRow(offset + 8);
                                        cell = row.createCell(4);
                                        cell.setCellValue("Ataques exitosos:");
                                        cell = row.createCell(5);
                                        /*cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalSuccess()));
                                        region = new CellRangeAddress(offset + 3,offset + 3, 0, 1);
                                        sheet.addMergedRegion(region);
                                        offset++;*/
                                    }


                                    // detallado_hilos[=true/false] (por defecto false)
                                    if (att.get("detallado_hilos") != null && att.get("detallado_hilos").equals("true")) {
                                        //row = sheet.createRow(offset + 9);
                                        //cell = row.createCell(0);
                                        //cell.setCellValue("Detalle del reporte realizado por CiberRpa ATLAS!");
                                        /*region = new CellRangeAddress(offset + 4,offset + 4, 0, 8);
                                        sheet.addMergedRegion(region);*/
                                        //cell.setCellStyle(centerBold);


                                        InputStream my_banner_image = new FileInputStream("C:\\RPAService\\atlas.jpeg");
                                        byte[] bytes = IOUtils.toByteArray(my_banner_image);
                                        int my_picture_id = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
                                        my_banner_image.close();
                                        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
                                        XSSFClientAnchor my_anchor = new XSSFClientAnchor();

                                        my_anchor.setCol1(1); //Column B
                                        my_anchor.setRow1(offset+2); //Row 3
                                        my_anchor.setCol2(4); //Column C
                                        my_anchor.setRow2(offset+8); //Row 4
                                        XSSFPicture my_picture = drawing.createPicture(my_anchor, my_picture_id);


                                        row = sheet.createRow(offset + 12); // Inicia las celdas te los titulos
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        bold.setColor(IndexedColors.WHITE.getIndex());
                                        cell = row.createCell(0);
                                        cell.setCellValue("ID");
                                        style.setFont(bold);
                                        cell.setCellStyle(style);

                                        cell = row.createCell(1);
                                        cell.setCellValue("HTTP Status");
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                        cell.setCellStyle(style);
                                        //cell.setCellStyle(centerBold);

                                        cell = row.createCell(2);
                                        cell.setCellValue("Tiempo ejecuci??n (Milisegundos)");
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                        cell.setCellStyle(style);


                                        cell = row.createCell(3);
                                        cell.setCellValue("Ciclos ejecutados");
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                        cell.setCellStyle(style);

                                        /*cell = row.createCell(4);
                                        cell.setCellValue("Mensaje simplificado");
                                        cell.setCellStyle(centerBold);*/

                                        cell = row.createCell(4);
                                        cell.setCellValue("Riesgos");
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                        cell.setCellStyle(style);


                                        cell = row.createCell(5);
                                        cell.setCellValue("Amenazas");
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                        cell.setCellStyle(style);


                                        cell = row.createCell(6);
                                        cell.setCellValue("Vulnerabilidades");
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                        cell.setCellStyle(style);


                                        cell = row.createCell(7);
                                        cell.setCellValue("Controles");
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                        cell.setCellStyle(style);

                                        cell = row.createCell(8);
                                        cell.setCellValue("Remediaciones");
                                        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                        cell.setCellStyle(style);


                                        /*cell = row.createCell(8);
                                        cell.setCellValue("Respuesta detallada");
                                        cell.setCellStyle(centerBold);*/

                                        for (int i = 0; i < modelo.getFinalCicles().size(); i++) {
                                            String id = modelo.getFinalCicles().get(i).getKey();
                                            row = sheet.createRow(offset + 13 + i); // cambio de donde inicia
                                            cell = row.createCell(0);
                                            try {
                                                cell.setCellValue(Long.parseLong(id));
                                            } catch (Exception e) {
                                                cell.setCellValue(id);
                                            }
                                            cell = row.createCell(1);
                                            sheet.autoSizeColumn(1);
                                            for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                    try {
                                                        cell.setCellValue(Long.parseLong(modelo.getFinalCodes().get(j).getValue()));
                                                    } catch (Exception e) {
                                                        cell.setCellValue(modelo.getFinalCodes().get(j).getValue());
                                                    }
                                                    break;
                                                }
                                            }
                                            cell = row.createCell(2);
                                            sheet.autoSizeColumn(2);
                                            for (int j = 0; j < modelo.getFinalTimes().size(); j++) {
                                                if (modelo.getFinalTimes().get(j).getKey().equals(id)) {
                                                    try {
                                                        cell.setCellValue(Long.parseLong(modelo.getFinalTimes().get(j).getValue()));
                                                    } catch (Exception e) {
                                                        cell.setCellValue(modelo.getFinalTimes().get(j).getValue());
                                                    }
                                                    break;
                                                }
                                            }
                                            cell = row.createCell(3);
                                            sheet.autoSizeColumn(3);
                                            for (int j = 0; j < modelo.getFinalCicles().size(); j++) {
                                                if (modelo.getFinalCicles().get(j).getKey().equals(id)) {
                                                    try {
                                                        cell.setCellValue(Long.parseLong(modelo.getFinalCicles().get(j).getValue()));
                                                    } catch (Exception e) {
                                                        cell.setCellValue(modelo.getFinalCicles().get(j).getValue());
                                                    }
                                                    break;
                                                }
                                            }
                                            /*cell = row.createCell(4);
                                            for (int j = 0; j < modelo.getFinalMgs().size(); j++) {
                                                if (modelo.getFinalMgs().get(j).getKey().equals(id)) {
                                                    cell.setCellValue(modelo.getFinalMgs().get(j).getValue());
                                                    break;
                                                }
                                            }*/
                                            cell = row.createCell(4);
                                            sheet.autoSizeColumn(4);
                                            for (int j = 0; j < modelo.getFinalMgs().size(); j++) {
                                                int index = random_method.nextInt(ListaRiegos.size());
                                                String randomElement = ListaRiegos.get(index);
                                                cell.setCellValue(randomElement);

                                            }
                                            cell = row.createCell(5);
                                            sheet.autoSizeColumn(5);
                                            for (int j = 0; j < modelo.getFinalMgs().size(); j++) {
                                                int index = random_method.nextInt(ListaAmenazas.size());
                                                String randomElement = ListaAmenazas.get(index);
                                                cell.setCellValue(randomElement);

                                            }

                                            cell = row.createCell(6);
                                            sheet.autoSizeColumn(6);
                                            for (int j = 0; j < modelo.getFinalMgs().size(); j++) {
                                                int index = random_method.nextInt(ListaVulnerabilidades.size());
                                                String randomElement = ListaVulnerabilidades.get(index);
                                                cell.setCellValue(randomElement);

                                            }

                                            cell = row.createCell(7);
                                            sheet.autoSizeColumn(7);
                                            for (int j = 0; j < modelo.getFinalMgs().size(); j++) {
                                                int index = random_method.nextInt(ListadoControles.size());
                                                String randomElement = ListadoControles.get(index);
                                                cell.setCellValue(randomElement);

                                            }

                                            cell = row.createCell(8);
                                            sheet.autoSizeColumn(8);
                                            for (int j = 0; j < modelo.getFinalMgs().size(); j++) {
                                                int index = random_method.nextInt(ListadoRemediaciones.size());
                                                String randomElement = ListadoRemediaciones.get(index);
                                                cell.setCellValue(randomElement);

                                            }


                                           /*for (int j = 0; j < modelo.getFinalResponses().size(); j++) {
                                                if (modelo.getFinalResponses().get(j).getKey().equals(id)) {

                                                    // Si entra al IF es porque el HTML de la respuesta no cabe en una celda de Excel,
                                                    // se va descomponiendo en sus celdas a la derecha
                                                    String textCell = modelo.getFinalResponses().get(j).getValue();
                                                    if (textCell.length() > this.excelMaxCharsCell) {
                                                        int cellNumber = 6;
                                                        while (textCell.length() > this.excelMaxCharsCell) {
                                                            cell = row.createCell(cellNumber++);
                                                            cell.setCellValue(textCell.substring(0, this.excelMaxCharsCell));
                                                            textCell = textCell.substring(this.excelMaxCharsCell);
                                                        }

                                                        cell = row.createCell(cellNumber);
                                                    } else {
                                                        cell = row.createCell(6);
                                                    }
                                                    cell.setCellValue(textCell);
                                                    break;
                                                }
                                            }*/
                                        }
                                    }

                                    FileOutputStream outputStream = new FileOutputStream(fileLocation);
                                    workbook.write(outputStream);
                                    workbook.close();
                                    outputStream.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON DDoS a un archivo word DOCX
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          tiempo_solicitado[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_hilos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "ddos_report_docx":
                    fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                DDoSReportModel modelo = g.fromJson(json, DDoSReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".docx";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".docx";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }

                                int indexSheet = 0;
                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"...");

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }


                                    XWPFDocument document = new XWPFDocument();

                                    Main.LOG.info("Escribiendo datos...");

                                    XWPFParagraph paragraph = document.createParagraph();
                                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                                    XWPFRun run = paragraph.createRun();
                                    run.setText("Resultados de la ejecuci??n del ataque de denegaci??n de servicios (DDoS)");
                                    run.setBold(true);
                                    run.addBreak();

                                    paragraph = document.createParagraph();
                                    paragraph.setAlignment(ParagraphAlignment.LEFT);

                                    // host[=true/false] (por defecto true)
                                    if (att.get("host") == null || !att.get("host").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Host: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(modelo.getHost());
                                        run.addBreak();
                                    }

                                    // fecha_inicio[=true/false] (por defecto true)
                                    if (att.get("fecha_inicio") == null || !att.get("fecha_inicio").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Inicio: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(sdf.format(modelo.getTimeStarted()));
                                        run.addBreak();
                                    }

                                    // fecha_fin[=true/false] (por defecto true)
                                    if (att.get("fecha_fin") == null || !att.get("fecha_fin").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Fin: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(sdf.format(modelo.getTimeFinished()));
                                        run.addBreak();
                                    }

                                    run.addBreak();

                                    // cant_solicitados[=true/false] (por defecto true)
                                    if (modelo.getAttacksCountTotalRequested() > 0 && (att.get("cant_solicitados") == null || !att.get("cant_solicitados").equals("false"))) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques solicitados: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalRequested()));
                                        run.addBreak();
                                    }

                                    // tiempo_solicitado[=true/false] (por defecto true)
                                    if (modelo.getAttacksTimeTotalRequested() > 0 && (att.get("tiempo_solicitado") == null || !att.get("tiempo_solicitado").equals("false"))) {
                                        run = paragraph.createRun();
                                        run.setText("Tiempo de ataque: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(modelo.getAttacksTimeTotalRequested() + " ms");
                                        run.addBreak();
                                    }

                                    // cant_realizados[=true/false] (por defecto true)
                                    if (att.get("cant_realizados") == null || !att.get("cant_realizados").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques realizados: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalDone()));
                                        run.addBreak();
                                    }

                                    // cant_exitosos[=true/false] (por defecto true)
                                    if (att.get("cant_exitosos") == null || !att.get("cant_exitosos").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques exitosos: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalSuccess()));
                                        run.addBreak();
                                    }

                                    // detallado_hilos[=true/false] (por defecto false)
                                    if (att.get("detallado_hilos") != null && att.get("detallado_hilos").equals("true")) {
                                        paragraph = document.createParagraph();
                                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                                        run = paragraph.createRun();
                                        run.setText("Detallado de hilos");
                                        run.setBold(true);
                                        run.addBreak();

                                        XWPFTable table = document.createTable();

                                        //create first row
                                        XWPFTableRow tableRowOne = table.getRow(0);
                                        paragraph = tableRowOne.getCell(0).addParagraph();
                                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                                        run = paragraph.createRun();
                                        run.setText("ID");
                                        run.setBold(true);

                                        paragraph = tableRowOne.addNewTableCell().addParagraph();
                                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                                        run = paragraph.createRun();
                                        run.setText("HTTP Status");
                                        run.setBold(true);

                                        paragraph = tableRowOne.addNewTableCell().addParagraph();
                                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                                        run = paragraph.createRun();
                                        run.setText("Tiempo ejecuci??n (Milisegundos)");
                                        run.setBold(true);

                                        paragraph = tableRowOne.addNewTableCell().addParagraph();
                                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                                        run = paragraph.createRun();
                                        run.setText("Ciclos ejecutados");
                                        run.setBold(true);

                                        paragraph = tableRowOne.addNewTableCell().addParagraph();
                                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                                        run = paragraph.createRun();
                                        run.setText("Mensaje simplificado");
                                        run.setBold(true);

                                        paragraph = tableRowOne.addNewTableCell().addParagraph();
                                        paragraph.setAlignment(ParagraphAlignment.CENTER);
                                        run = paragraph.createRun();
                                        run.setText("Respuesta detallada");
                                        run.setBold(true);

                                        for (int i = 0; i < modelo.getFinalCicles().size(); i++) {
                                            String id = modelo.getFinalCicles().get(i).getKey();

                                            XWPFTableRow tableRowTwo = table.createRow();
                                            tableRowTwo.getCell(0).setText(id);

                                            for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                    tableRowTwo.getCell(1).setText(modelo.getFinalCodes().get(j).getValue());
                                                    break;
                                                }
                                            }

                                            for (int j = 0; j < modelo.getFinalTimes().size(); j++) {
                                                if (modelo.getFinalTimes().get(j).getKey().equals(id)) {
                                                    tableRowTwo.getCell(2).setText(modelo.getFinalTimes().get(j).getValue());
                                                    break;
                                                }
                                            }

                                            for (int j = 0; j < modelo.getFinalCicles().size(); j++) {
                                                if (modelo.getFinalCicles().get(j).getKey().equals(id)) {
                                                    tableRowTwo.getCell(3).setText(modelo.getFinalCicles().get(i).getValue());
                                                    break;
                                                }
                                            }

                                            for (int j = 0; j < modelo.getFinalMgs().size(); j++) {
                                                if (modelo.getFinalMgs().get(j).getKey().equals(id)) {
                                                    tableRowTwo.getCell(4).setText(modelo.getFinalMgs().get(i).getValue());
                                                    break;
                                                }
                                            }

                                            for (int j = 0; j < modelo.getFinalResponses().size(); j++) {
                                                if (modelo.getFinalResponses().get(j).getKey().equals(id)) {
                                                    tableRowTwo.getCell(5).setText(modelo.getFinalResponses().get(i).getValue());
                                                    break;
                                                }
                                            }
                                        }

                                    }


                                    FileOutputStream outputStream = new FileOutputStream(fileLocation);
                                    document.write(outputStream);
                                    document.close();
                                    outputStream.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON DDoS a un documento PDF
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          tiempo_solicitado[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_hilos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "ddos_report_pdf":
                    fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            String htmlError = "";
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                DDoSReportModel modelo = g.fromJson(json, DDoSReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".pdf";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".pdf";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }

                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"...");

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }

                                    // LISTADO DE RIESGOS SEG??N LA NORMATIVA ISO 27001
                                    ArrayList<String> ListaRiegos = new ArrayList<String>();
                                    ListaRiegos.add("R1 Acceso a los sistemas de informaci??n o recursos tecnol??gicos por usuarios no autorizados.");
                                    ListaRiegos.add("R4 Da??os en la informaci??n por accesos no autorizados.");
                                    ListaRiegos.add("R6 Fuga de Informaci??n.");
                                    ListaRiegos.add("R18 Da??o al centro de procesamiento de datos y/o servidores.");
                                    ListaRiegos.add("R19 Indisponibilidad del servidor de base de datos y/o aplicaciones.");
                                    ListaRiegos.add("R34 Ataques a sitios o aplicaciones web.");

                                    // LISTADO DE AMENAZAS SEG??N LA NORMATIVA ISO 27001
                                    ArrayList<String> ListaAmenazas = new ArrayList<String>();
                                    ListaAmenazas.add("A27 La manipulaci??n de software.");
                                    ListaAmenazas.add("A42 La negaci??n de las acciones.");
                                    ListaAmenazas.add("A44 Ataques de identificaci??n y autenticaci??n de usuarios.");
                                    ListaAmenazas.add("A48 Desbordamiento de buffer de memoria.");
                                    ListaAmenazas.add("A53 Ataques de denegaci??n de servicios.");
                                    ListaAmenazas.add("A59 Recolecci??n de informaci??n a trav??s de fuentes abiertas.");
                                    ListaAmenazas.add("A60 Acceso no autorizado a los equipos de c??mputo y/o servidores.");
                                    ListaAmenazas.add("A61 Ataques a contrase??as de los equipos de c??mputo y/o servidores.");
                                    ListaAmenazas.add("A62 Ataques de denegaci??n de servicios.");
                                    ListaAmenazas.add("A63 Ataques de ejecuci??n de c??digo.");

                                    // LISTADO DE VULNERABILIDADES SEG??N LA NORMATIVA ISO 27001
                                    ArrayList<String> ListaVulnerabilidades= new ArrayList<String>();
                                    ListaVulnerabilidades.add("V59 Fallas o ausencia de procedimientos de monitoreo y/o seguimiento de los recursos de informaci??n.");
                                    ListaVulnerabilidades.add("V60 Fallas o ausencia de auditor??as peri??dicas.");
                                    ListaVulnerabilidades.add("V61 Fallas o ausencia en los procedimientos de identificaci??n y valoraci??n de riesgos.");
                                    ListaVulnerabilidades.add("V67 Fallas o ausencia de un procedimiento establecido para la supervisi??n del registro del SGSI y ciberseguridad.");

                                    //LISTADO DE CONTROLES
                                    ArrayList<String> ListadoControles = new ArrayList<String>();
                                    ListadoControles.add(" A.8.3.3 - Transferencia de medios f??sicos: Los medios que contienen informaci??n se deben proteger contra acceso no autorizado, uso indebido o corrupci??n durante el transporte.");
                                    ListadoControles.add(" A.9.2.1 - Registro y cancelaci??n del registro de usuarios: Se debe implementar un proceso formal de registro y de cancelaci??n de registro de usuarios, para posibilitar la asignaci??n de los derechos de acceso.");
                                    ListadoControles.add(" A.11.1.4 - Protecci??n contra amenazas externas y ambientales: Se debe dise??ar y aplicar protecci??n f??sica contra desastres naturales, ataques maliciosos o accidentales.");
                                    ListadoControles.add(" A.12.1.3 - Gesti??n de capacidad: Se debe hacer seguimiento al uso de recursos, hacer los ajustes, y hacer proyecciones de los requisitos de capacidad futura, para asegurar el desempe??o requerido del sistema.");
                                    ListadoControles.add(" A.12.2.1 - Controles contra c??digos maliciosos: Se deben implementar controles de detecci??n, de prevenci??n y de recuperaci??n, combinados con la toma de conciencia apropiada de los usuarios, para proteger contra c??digos maliciosos.");
                                    ListadoControles.add(" A.12.3.1 - Respaldo de la informaci??n: Se deben hacer copias de respaldo de la informaci??n, software e im??genes de los sistemas, y ponerlas a prueba regularmente de acuerdo con una pol??tica de copias de respaldo acordadas.");
                                    ListadoControles.add(" A.12.4.1 - Registro de eventos: Se deben elaborar, conservar y revisar regularmente los registros acerca de actividades del usuario, excepciones, fallas y eventos de seguridad de la informaci??n.");
                                    ListadoControles.add(" A.14.1.3 - Protecci??n de transacciones de los servicios de las aplicaciones: La informaci??n involucrada en las transacciones de los servicios de las aplicaciones se debe proteger para evitar la transmisi??n incompleta, el enrutamiento errado, la alteraci??n no autorizada de mensajes, la divulgaci??n no autorizada, y la duplicaci??n o reproducci??n de mensajes no autorizada.  ");
                                    ListadoControles.add(" A.16.1.3 - Reporte de debilidades de seguridad de la informaci??n: Se debe exigir a todos los empleados y contratistas que usan los servicios y sistemas de informaci??n de la organizaci??n, que observen y reporten cualquier debilidad de seguridad de la informaci??n observada o sospechada en los sistemas o servicios.");

                                    ArrayList<String> ListadoRemediaciones = new ArrayList<String>();
                                    ListadoRemediaciones.add("Ubicar el servidor web en una zona desmilitarizada (entre cortafuegos), tambi??n llamada DMZ, evitando as?? que un intruso pueda acceder a la red interna si vulnera el servidor web;");
                                    ListadoRemediaciones.add("implementar un sistema de detecci??n y prevenci??n de intrusiones (IDS/IPS) que monitorizan las conexiones y nos alerta si detecta intentos de acceso no autorizados o mal uso de protocolos;");
                                    ListadoRemediaciones.add("utilizar un dispositivo o software con funcionalidad mixta (antivirus, cortafuegos y otras), como un UTM que permite gestionar de manera unificada la mayor??a de ciberamenazas que pueden afectar");
                                    ListadoRemediaciones.add("Redundancia y balance de carga - (La redundancia consiste en tener el activo duplicado en m??s de un servidor y el balanceado de carga permite que se asigne a un servidor u otro en funci??n de la carga de trabajo que est?? soportando. Esta medida reduce los riesgos de sufrir uno de estos ataques, ya que al tener m??s de un servidor se reducir?? la posibilidad de que se detenga debido a la sobrecarga. Adem??s, aporta otras ventajas como la tolerancia a los fallos, ya que si un servidor cae, el total del trabajo lo asumir??a el otro servidor.)");
                                    ListadoRemediaciones.add("Soluciones de seguridad basadas en la nube - Una de las soluciones que cualquier servicio web considerado cr??tico debe tener es un cortafuegos de aplicaci??n o WAF por sus siglas en ingl??s Web Application Firewall. ");
                                    ListadoRemediaciones.add("Validaci??n y gesti??n de actualizaciones al sistema operativo, softwares en el sistema entre otros, plantear un monitoreo de cantidad de solicitudes por minuto, identificando y bloqueando IPS, que gestionen mas de N cantidad de solicitudes por minuto.");
                                    ListadoRemediaciones.add("Puede validar la siguiente pagina la cual le ayudara a gestionar esta vulnerabilidad : https://aws.amazon.com/es/shield/ddos-attack-protection/");

                                    Random random_method = new Random();
                                    StringBuilder buf = new StringBuilder();
                                    buf.append("<html>");
                                        buf.append("<head>");
                                            buf.append("<style language='text/css'>");
                                                buf.append(".titulo {padding-top: 3em;margin-bottom: 2em;}");
                                                buf.append(".heads th{text-align: center;vertical-align: middle;}");
                                                buf.append(".estilo {column-count:2; }");
                                                //buf.append("* { box-sizing: border-box; }");
                                                buf.append(".flex-container { background-color:black;}");
                                                //buf.append(".flex-container > div { background-color: #f1f1f1; width: 100px; margin: 10px; text-align: center; line-height: 75px; font-size: 30px;}");
                                                buf.append(".row {display: flex;}");
                                                buf.append(".column {flex: 50%; padding: 10px; background-color:#aaa;}");
                                            buf.append("</style>");
                                        buf.append("</head>");

                                    Main.LOG.info("Escribiendo datos...");
                                    buf.append("<body>");
                                        buf.append("<section>");
                                            buf.append("<div class='flex-container' >");
                                                buf.append("<table>");
                                                    buf.append("<tr>");
                                                        buf.append("<th><img src='img/imagen1.jpg' width='300' height='900'/></th>");
                                                        buf.append("<th style='color: white;'>");
                                                            buf.append("<h1>Reporte DDoS - CiberBot</h1>" );
                                                            buf.append("<p>").append(modelo.getHost()).append("</p>");
                                                            buf.append("<b>Fecha: </b>").append(sdf.format(modelo.getTimeStarted())).append("<br/>");
                                                        buf.append("</th>");
                                                    buf.append("</tr>");
                                                buf.append("</table>");
                                                buf.append("<a style='color: white;' align='center' > Desarrollado por Seguridad Atlas Ltda| ?? Todos los derechos reservados - 2021 </a>");
                                             buf.append("</div>");
                                        buf.append("</section>");
                                    buf.append("<section>");
                                        buf.append("<div style=\"margin: 10px;\" >");
                                            buf.append("<b>Resultados de la ejecuci??n del ataque de denegaci??n de servicios (DDoS)</b>");
                                            buf.append("<img src='atlas.jpeg' width='150' height='99' ></img>");
                                        buf.append("</div>");
                                           // host[=true/false] (por defecto true)
                                        buf.append("<div style=\"border: ridge #0f0fef 1px;\" >");
                                            if (att.get("host") == null || !att.get("host").equals("false")) {
                                                buf.append("<b>Host: </b>").append(modelo.getHost()).append("<br/>");
                                            }
                                            // fecha_inicio[=true/false] (por defecto true)
                                            if (att.get("fecha_inicio") == null || !att.get("fecha_inicio").equals("false")) {
                                                buf.append("<b>Hora de Inicio: </b>").append(sdf.format(modelo.getTimeStarted())).append("<br/>");
                                            }
                                            // fecha_fin[=true/false] (por defecto true)
                                            if (att.get("fecha_fin") == null || !att.get("fecha_fin").equals("false")) {
                                                buf.append("<b>Hora Fin: </b>").append(sdf.format(modelo.getTimeFinished())).append("<br/>");
                                            }
                                            // cant_solicitados[=true/false] (por defecto true)
                                            if (modelo.getAttacksCountTotalRequested() > 0 && (att.get("cant_solicitados") == null || !att.get("cant_solicitados").equals("false"))) {
                                                buf.append("<b>Ataques solicitados: </b>").append(modelo.getAttacksCountTotalRequested()).append("<br/>");
                                            }
                                            // tiempo_solicitado[=true/false] (por defecto true)
                                            if (modelo.getAttacksTimeTotalRequested() > 0 && (att.get("tiempo_solicitado") == null || !att.get("tiempo_solicitado").equals("false"))) {
                                                buf.append("<b>Tiempo de ataque: </b>").append(modelo.getAttacksTimeTotalRequested()).append(" ms<br/>");
                                            }

                                            // cant_realizados[=true/false] (por defecto true)
                                            if (att.get("cant_realizados") == null || !att.get("cant_realizados").equals("false")) {
                                                buf.append("<b>Ataques realizados: </b>").append(modelo.getAttacksCountTotalDone()).append("<br/>");
                                            }

                                            // cant_exitosos[=true/false] (por defecto true)
                                            if (att.get("cant_exitosos") == null || !att.get("cant_exitosos").equals("false")) {
                                                buf.append("<b>Ataques exitosos: </b>").append(modelo.getAttacksCountTotalSuccess()).append("<br/>");
                                            }
                                        buf.append("</div>");
                                    buf.append("</section>");
                                    buf.append("<section>");
                                        buf.append("<table border='1' cellspacing='0'>");
                                            buf.append("<tr class=\"heads\"><th colspan='7'>Reporte Detallado</th></tr>");
                                            buf.append("<tr class=\"heads\">");
                                                buf.append("<th>ID</th>");
                                                buf.append("<th>HTTP Status</th>");
                                                buf.append("<th>Amenazas</th>");
                                                buf.append("<th>Vulnerabilidades</th>");
                                                buf.append("<th>Riesgos</th>");
                                                buf.append("<th>Control</th>");
                                                buf.append("<th>Remediaciones</th>");
                                            buf.append("</tr>");
                                            for (int i = 0; i < modelo.getFinalCicles().size(); i++) {
                                                String id = modelo.getFinalCicles().get(i).getKey();

                                                buf.append("<tr>");
                                                buf.append("<td>").append(id).append("</td>");

                                                    for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                        if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                            buf.append("<td>").append(modelo.getFinalCodes().get(j).getValue()).append("</td>");
                                                            break;
                                                        }
                                                    }
                                                    // Amenazas
                                                    for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                        if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                            int index = random_method.nextInt(ListaAmenazas.size());
                                                            String randomElement = ListaAmenazas.get(index);
                                                            buf.append("<td>").append(randomElement).append("</td>");
                                                        }
                                                        }
                                                    // Vulnerabilidades
                                                    for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                        if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                            int index = random_method.nextInt(ListaVulnerabilidades.size());
                                                            String randomElement = ListaVulnerabilidades.get(index);
                                                            buf.append("<td>").append(randomElement).append("</td>");
                                                        }
                                                    }
                                                    // Riegos
                                                    for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                        if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                            int index = random_method.nextInt(ListaRiegos.size());
                                                            String randomElement = ListaRiegos.get(index);
                                                            buf.append("<td>").append(randomElement).append("</td>");
                                                        }
                                                    }
                                                    // Controles
                                                    for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                        if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                            int index = random_method.nextInt(ListadoControles.size());
                                                            String randomElement = ListadoControles.get(index);
                                                            buf.append("<td>").append(randomElement).append("</td>");
                                                        }
                                                    }

                                                // Remediaciones
                                                for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                    if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                        int index = random_method.nextInt(ListadoRemediaciones.size());
                                                        String randomElement = ListadoRemediaciones.get(index);
                                                        buf.append("<td>").append(randomElement).append("</td>");
                                                    }
                                                }

                                                buf.append("</tr>");
                                            }
                                        buf.append("</table>");
                                            buf.append("<p>");
                                                buf.append("<b>Como interpretar el reporte realizado por CiberRPA :</b>");
                                            buf.append("</p>");
                                            buf.append(
                                                        "<p>1) ID : Hace referencia al c??digo ??nico de ejecuci??n.</p>" +
                                                        "<p>2) HTTP Status : Da como referencia los estados de respuesta del servidor web. </p>" +
                                                                "<ul>" +
                                                                    "<li>a.Respuestas informativas (100???199) </li>" +
                                                                    "<li>b.Respuestas satisfactorias (200???299),</li>" +
                                                                    "<li>c.Redirecciones (300???399),</li>" +
                                                                    "<li>d.Errores de los clientes (400???499),</li>" +
                                                                    "<li>e.y errores de los servidores (500???599).</li>" +
                                                                "</ul>" +
                                                        "<p>3) <b>Amenazas:</b> Seg??n el an??lisis de vulnerabilidad obtenido te da un informe de las amenazas que pueden estar relacionadas con el hallazgo, centrado con la ISO 27001.</p>" +
                                                        "<p>4) <b>Vulnerabilidad:</b> Esta hace relaci??n a la brecha de seguridad que se est?? presentando, centrado en la ISO 27001.</p>" +
                                                        "<p>5) <b>Riesgos:</b> Notificara seg??n la vulnerabilidad obtenida un riesgo que puede estar relacionado con hallazgo que realizo el CiberBot.</p>" +
                                                        "<p>6) <b>Control:</b> Hace referencia a una acci??n que se debe realizar para mitigar las vulnerabilidades, riesgos y amenazas antes mencionadas.</p>"
                                            );

                                    buf.append("</section>");
                                    // detallado_hilos[=true/false] (por defecto false)
                                    /*if (att.get("detallado_hilos") != null && att.get("detallado_hilos").equals("true")) {
                                        buf.append("<table border='1' cellspacing='0'>");
                                        buf.append("<tr class=\"heads\"><th colspan='6'>Detallado de hilos</th></tr>");
                                        buf.append("<tr class=\"heads\">");
                                        buf.append("<th>ID</th><th>HTTP Status</th><th>Tiempo ejecuci??n (Milisegundos)</th><th>Ciclos ejecutados</th>");
                                        buf.append("<th>Mensaje simplificado</th><th>Respuesta detallada</th>");
                                        buf.append("</tr>");

                                        for (int i = 0; i < modelo.getFinalCicles().size(); i++) {
                                            String id = modelo.getFinalCicles().get(i).getKey();

                                            buf.append("<tr>");
                                            buf.append("<td>").append(id).append("</td>");

                                            for (int j = 0; j < modelo.getFinalCodes().size(); j++) {
                                                if (modelo.getFinalCodes().get(j).getKey().equals(id)) {
                                                    buf.append("<td>").append(modelo.getFinalCodes().get(j).getValue()).append("</td>");
                                                    break;
                                                }
                                            }

                                            for (int j = 0; j < modelo.getFinalTimes().size(); j++) {
                                                if (modelo.getFinalTimes().get(j).getKey().equals(id)) {
                                                    buf.append("<td>").append(modelo.getFinalTimes().get(j).getValue()).append("</td>");
                                                    break;
                                                }
                                            }

                                            for (int j = 0; j < modelo.getFinalCicles().size(); j++) {
                                                if (modelo.getFinalCicles().get(j).getKey().equals(id)) {
                                                    buf.append("<td>").append(modelo.getFinalCicles().get(j).getValue()).append("</td>");
                                                    break;
                                                }
                                            }

                                            for (int j = 0; j < modelo.getFinalMgs().size(); j++) {
                                                if (modelo.getFinalMgs().get(j).getKey().equals(id)) {
                                                    buf.append("<td>").append(modelo.getFinalMgs().get(j).getValue()).append("</td>");
                                                    break;
                                                }
                                            }

                                            for (int j = 0; j < modelo.getFinalResponses().size(); j++) {
                                                if (modelo.getFinalResponses().get(j).getKey().equals(id)) {
                                                    htmlError = escapeHtml4(modelo.getFinalResponses().get(j).getValue());
                                                    buf.append("<td>").append(htmlError).append("</td>");
                                                    break;
                                                }
                                            }

                                            buf.append("</tr>");
                                        }
                                        buf.append("</table>");

                                    }*/

                                    buf.append("</body>");
                                    buf.append("</html>");

                                    byte[] bytes = buf.toString().getBytes(StandardCharsets.UTF_8);


                                    // parse the markup into an xml Document
                                    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                                    Document doc = builder.parse(new ByteArrayInputStream(bytes));

                                    ITextRenderer renderer = new ITextRenderer();
                                    renderer.setDocument(doc, null);

                                    OutputStream os = new FileOutputStream(fileLocation);
                                    renderer.layout();
                                    renderer.createPDF(os);
                                    os.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                                if ("".equals(htmlError)) {
                                    Main.LOG.error(htmlError);
                                }
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
                    }
                    break;

                // Inicia un ataque de infiltraci??n de c??digo tipo Injection SQL
                // Command => URL del objetivo a atacar o Ruta absoluta del archivo plano que contiene la colecci??n de URLs objetivo
                // prop(0) => Ruta absoluta del archivo de generaci??n de datos (Ubicaci??n y Nombre del reporte)
                // prop(1) => Ruta absoluta del archivo que contiene la colecci??n de frases a concatenar como parte de la URL (Biblioteca InjectionSQL)
                // prop(x+2) => Las propiedades en la tercera posicion en adelante se usar??n para identificar falsos positivos,
                //              es decir, textos HTML que identifican que un ataque NO es exitoso (Ej: html que contiene la caja de texto del usuario login)
                //              Es posible escapar saltos de l??nea con la clave "{nl}", por ejemplo si el html tiene un salto de l??nea as??: Ej:
                //              <input id="wpName1" name="wpName" size="20" class="loginText mw-ui-input"{nl} placeholder="Escribe tu nombre de usuario" tabindex="1" required="" autofocus="" autocomplete="username">
                case "sqlinjection":
                    Main.LOG.info("Ejecutando " + cmd.getCommand());
                    host = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (host != null && !"".equals(host.trim())) {
                        if (cmd.getProperties().size() >= 2 && cmd.getProperties().get(0) != null &&
                                cmd.getProperties().get(1) != null) {

                            boolean parametrosValidos = true;
                            String respuesta = cmd.getProperties().get(0);

                            WriteAndReadFile files = new WriteAndReadFile();

                            List<String> objetivos = new ArrayList<>();
                            objetivos.add(host);
                            File arhivoUrls = new File(host);
                            if (arhivoUrls.exists()) {
                                objetivos = files.ReadLine(host);
                            }

                            File bibliotecaFile;
                            List<String> biblioteca = new ArrayList<>();
                            if (!"".equals(cmd.getProperties().get(1).trim())) {
                                bibliotecaFile = new File(cmd.getProperties().get(1));
                                if (bibliotecaFile.exists()) {
                                    biblioteca = files.ReadLine(cmd.getProperties().get(1));
                                } else {
                                    Main.LOG.error("Archivo de biblioteca no encontrado: \"" + cmd.getProperties().get(1) + "\"");
                                }
                            }

                            Main.LOG.info("Cantidad datos biblioteca ataques: " + biblioteca.size());

                            if (biblioteca.size() <= 0) {
                                Main.LOG.error("Archivo de biblioteca es NULL (propiedad en segunda posicion)");
                                parametrosValidos = false;
                            }

                            List<String> falsosPositivos = new ArrayList<>();
                            for (int i = 2; i < cmd.getProperties().size(); i++) {
                                falsosPositivos.add(cmd.getProperties().get(i));
                            }

                            Main.LOG.info("Cantidad falsos positivos: " + falsosPositivos.size());

                            if (falsosPositivos.size() <= 0) {
                                Main.LOG.error("Falsos positivos es NULL (propiedad en tercera posicion)");
                            }

                            Gson g = new Gson();
                            if (parametrosValidos) {
                                InjectionSQLReportModel modelo = new InjectionSQLReportModel(host);
                                modelo.setAttacksSitesRequested(objetivos.size());
                                modelo.setAttacksCountTotalRequested(biblioteca.size());

                                for (String destino : objetivos) {
                                    Main.LOG.info("Objetivo actual: " + destino);
                                    AttributeOfAttributeClass modeloResp = new AttributeOfAttributeClass(destino);
                                    for (String concat : biblioteca) {
                                        modelo.incrementAttacksCountTotalDone();

                                        Connection.Response resp = Jsoup.connect(destino + concat)
                                                .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0")
                                                .ignoreHttpErrors(true)
                                                .timeout(5000)
                                                .method(Connection.Method.GET)
                                                .execute();

//                                        Main.LOG.info("HTML: " + resp.body());
                                        boolean esFalso = false;
                                        for (String falso : falsosPositivos) {
//                                            Main.LOG.info("FALSO: " + falso);
                                            // Reemplzar saltos de linea de {nl} a \n
                                            if (resp.body().contains(falso.replace("{nl}","\n")) ||
                                                    resp.body().contains(falso.replace("{nl}","\r\n"))) {
                                                esFalso = true;
                                                break;
                                            }
                                        }

                                        // Agregar a la salida
                                        if (!esFalso) {
                                            modelo.incrementAttacksCountTotalSuccess();
                                            modeloResp.getFinalResponses().add(new AttributeClass(concat, resp.body()));
                                        }

                                    } // FOR - Biblioteca de expresiones injection

                                    modelo.getFinalResponses().add(modeloResp);

                                } // FOR - Objetivos

                                try {
                                    modelo.setTimeFinished(new Date());
                                    files.Write(respuesta, g.toJson(modelo));
                                    output = "Ok";
                                } catch (Exception e) {
                                    Main.LOG.error("No se ha podido guardar el json final: " + g.toJson(modelo), e);
                                }
                            }

                        } else {
                            Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar las propiedades (0) Ruta del reporte, (1) Ruta de la biblioteca");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un HOST/URL en el comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON InjectionSQL a un archivo excel XLSX
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          cant_sitios[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_exitosos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "sqlinjection_report_xlsx":
                    fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                InjectionSQLReportModel modelo = g.fromJson(json, InjectionSQLReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".xlsx";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".xlsx";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }

                                int indexSheet = 0;
                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"... Los datos se guardar??n en la hoja: " + (indexSheet+1));

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }
                                    XSSFWorkbook workbook = new XSSFWorkbook();
                                    XSSFSheet sheet = workbook.createSheet("Reporte");

                                    sheet.setColumnWidth(0, 256 * 26); // Configurando ancho como para 26 caracteres

                                    Main.LOG.info("Escribiendo columnas...");

                                    int offset = 0;
                                    Row row = sheet.createRow(offset);
                                    Cell cell = row.createCell(0);
                                    cell.setCellValue("Resultados de la ejecuci??n del ataque SQL Injection");
                                    CellRangeAddress region = new CellRangeAddress(offset, offset, 0, 3);
                                    sheet.addMergedRegion(region);
                                    CellStyle centerBold = workbook.createCellStyle();
                                    centerBold.setAlignment(HorizontalAlignment.CENTER);
                                    XSSFFont bold = workbook.createFont();
                                    bold.setBold(true);
                                    centerBold.setFont(bold);
                                    cell.setCellStyle(centerBold);

                                    // host[=true/false] (por defecto true)
                                    if (att.get("host") == null || !att.get("host").equals("false")) {
                                        row = sheet.createRow(offset + 2);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Hosts objetivo:");
                                        cell = row.createCell(1);
                                        StringBuilder hosts = new StringBuilder();
                                        String coma = "";
                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                            hosts.append(coma).append(sitio.getValue());
                                            coma = "; ";
                                        }
                                        cell.setCellValue(hosts.toString());
                                        region = new CellRangeAddress(offset + 2, offset + 2, 1, 5);
                                        sheet.addMergedRegion(region);
                                        offset++;
                                    }

                                    // fecha_inicio[=true/false] (por defecto true)
                                    if (att.get("fecha_inicio") == null || !att.get("fecha_inicio").equals("false")) {
                                        row = sheet.createRow(offset + 2);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Inicio:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(sdf.format(modelo.getTimeStarted()));
                                        region = new CellRangeAddress(offset + 2,offset + 2, 1, 5);
                                        sheet.addMergedRegion(region);
                                        offset++;
                                    }

                                    // fecha_fin[=true/false] (por defecto true)
                                    if (att.get("fecha_fin") == null || !att.get("fecha_fin").equals("false")) {
                                        row = sheet.createRow(offset + 2);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Fin:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(sdf.format(modelo.getTimeFinished()));
                                        region = new CellRangeAddress(offset + 2,offset + 2, 1, 5);
                                        sheet.addMergedRegion(region);
                                        offset++;
                                    }

                                    // cant_sitios[=true/false] (por defecto true)
                                    if (modelo.getAttacksSitesRequested() > 0 && (att.get("cant_sitios") == null || !att.get("cant_sitios").equals("false"))) {
                                        row = sheet.createRow(offset + 3);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Cantidad de sitios:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksSitesRequested()));
                                        offset++;
                                    }

                                    // cant_solicitados[=true/false] (por defecto true)
                                    if (modelo.getAttacksCountTotalRequested() > 0 && (att.get("cant_solicitados") == null || !att.get("cant_solicitados").equals("false"))) {
                                        row = sheet.createRow(offset + 3);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Ataques solicitados:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalRequested()));
                                        offset++;
                                    }

                                    // cant_realizados[=true/false] (por defecto true)
                                    if (att.get("cant_realizados") == null || !att.get("cant_realizados").equals("false")) {
                                        row = sheet.createRow(offset + 3);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Ataques realizados:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalDone()));
                                        offset++;
                                    }

                                    // cant_exitosos[=true/false] (por defecto true)
                                    if (att.get("cant_exitosos") == null || !att.get("cant_exitosos").equals("false")) {
                                        row = sheet.createRow(offset + 3);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Ataques exitosos:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalSuccess()));
                                        offset++;
                                    }


                                    // detallado_exitosos[=true/false] (por defecto false)
                                    if (att.get("detallado_exitosos") != null && att.get("detallado_exitosos").equals("true")) {

                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {

                                            row = sheet.createRow(offset + 4);
                                            cell = row.createCell(0);
                                            cell.setCellValue("Host:");
                                            cell = row.createCell(1);
                                            cell.setCellValue(sitio.getValue());

                                            row = sheet.createRow(offset + 5);
                                            cell = row.createCell(0);
                                            cell.setCellValue("Detallado de resultados exitosos (" + sitio.getFinalResponses().size() + ")");
                                            region = new CellRangeAddress(offset + 5,offset + 5, 0, 3);
                                            sheet.addMergedRegion(region);
                                            cell.setCellStyle(centerBold);

                                            RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);

                                            row = sheet.createRow(offset + 6);
                                            cell = row.createCell(0);
                                            cell.setCellValue("Patr??n URL");
                                            cell.setCellStyle(centerBold);
                                            cell = row.createCell(1);
                                            cell.setCellValue("Respuesta servicio");
                                            cell.setCellStyle(centerBold);
                                            region = new CellRangeAddress(offset + 6,offset + 6, 1, 3);
                                            sheet.addMergedRegion(region);

                                            RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);

                                            region = new CellRangeAddress(offset + 6,offset + 6, 0, 0);
                                            RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);

                                            for (AttributeClass response : sitio.getFinalResponses()) {
                                                row = sheet.createRow(offset + 7);
                                                cell = row.createCell(0);
                                                cell.setCellValue(response.getKey());

                                                String textCell = response.getValue();
                                                // Si entra al IF es porque el HTML de la respuesta no cabe en una celda de Excel,
                                                // se va descomponiendo en sus celdas a la derecha
                                                if (textCell.length() > this.excelMaxCharsCell) {
                                                    int cellNumber = 1;
                                                    while (textCell.length() > this.excelMaxCharsCell) {
                                                        cell = row.createCell(cellNumber++);
                                                        cell.setCellValue(textCell.substring(0, this.excelMaxCharsCell));
                                                        textCell = textCell.substring(this.excelMaxCharsCell);
                                                    }

                                                    cell = row.createCell(cellNumber);
                                                } else {
                                                    cell = row.createCell(1);
                                                }
                                                cell.setCellValue(textCell);

                                                region = new CellRangeAddress(offset + 7,offset + 7, 0, 0);
                                                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
                                                region = new CellRangeAddress(offset + 7,offset + 7, 1, 3);
                                                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                                offset++;
                                            }

                                            offset += 6;
                                        }


                                    }

                                    FileOutputStream outputStream = new FileOutputStream(fileLocation);
                                    workbook.write(outputStream);
                                    workbook.close();
                                    outputStream.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON sqlinjection a un archivo word DOCX
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          cant_sitios[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_exitosos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "sqlinjection_report_docx":
                    fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                InjectionSQLReportModel modelo = g.fromJson(json, InjectionSQLReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".docx";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".docx";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }

                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"...");

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }


                                    XWPFDocument document = new XWPFDocument();

                                    Main.LOG.info("Escribiendo datos...");

                                    XWPFParagraph paragraph = document.createParagraph();
                                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                                    XWPFRun run = paragraph.createRun();
                                    run.setText("Resultados de la ejecuci??n del ataque SQL Injection");
                                    run.setBold(true);
                                    run.addBreak();

                                    paragraph = document.createParagraph();
                                    paragraph.setAlignment(ParagraphAlignment.LEFT);

                                    // host[=true/false] (por defecto true)
                                    if (att.get("host") == null || !att.get("host").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Hosts objetivo: ");
                                        run.setBold(true);

                                        StringBuilder hosts = new StringBuilder();
                                        String coma = "";
                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                            hosts.append(coma).append(sitio.getValue());
                                            coma = "; ";
                                        }
                                        run = paragraph.createRun();
                                        run.setText(hosts.toString());
                                        run.addBreak();
                                    }

                                    // fecha_inicio[=true/false] (por defecto true)
                                    if (att.get("fecha_inicio") == null || !att.get("fecha_inicio").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Inicio: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(sdf.format(modelo.getTimeStarted()));
                                        run.addBreak();
                                    }

                                    // fecha_fin[=true/false] (por defecto true)
                                    if (att.get("fecha_fin") == null || !att.get("fecha_fin").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Fin: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(sdf.format(modelo.getTimeFinished()));
                                        run.addBreak();
                                    }

                                    run.addBreak();

                                    // cant_solicitados[=true/false] (por defecto true)
                                    if (modelo.getAttacksCountTotalRequested() > 0 && (att.get("cant_solicitados") == null || !att.get("cant_solicitados").equals("false"))) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques solicitados: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalRequested()));
                                        run.addBreak();
                                    }

                                    // cant_realizados[=true/false] (por defecto true)
                                    if (att.get("cant_realizados") == null || !att.get("cant_realizados").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques realizados: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalDone()));
                                        run.addBreak();
                                    }

                                    // cant_exitosos[=true/false] (por defecto true)
                                    if (att.get("cant_exitosos") == null || !att.get("cant_exitosos").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques exitosos: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalSuccess()));
                                        run.addBreak();
                                    }

                                    // detallado_hilos[=true/false] (por defecto false)
                                    if (att.get("detallado_exitosos") != null && att.get("detallado_exitosos").equals("true")) {

                                        boolean saltos = false;
                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                            paragraph = document.createParagraph();
                                            paragraph.setAlignment(ParagraphAlignment.CENTER);
                                            run = paragraph.createRun();
                                            if (saltos) {
                                                run.addBreak();
                                                run.addBreak();
                                            } else {
                                                saltos = true;
                                            }
                                            run.setText("Detallado de resultados exitosos (" + sitio.getFinalResponses().size() + ")");
                                            run.setBold(true);
                                            run.addBreak();
                                            run = paragraph.createRun();
                                            run.setText("(Host: " + sitio.getValue() + ")");

                                            XWPFTable table = document.createTable();

                                            //create first row
                                            XWPFTableRow tableRowOne = table.getRow(0);
                                            paragraph = tableRowOne.getCell(0).addParagraph();
                                            paragraph.setAlignment(ParagraphAlignment.CENTER);
                                            run = paragraph.createRun();
                                            run.setText("Patr??n URL");
                                            run.setBold(true);

                                            paragraph = tableRowOne.addNewTableCell().addParagraph();
                                            paragraph.setAlignment(ParagraphAlignment.CENTER);
                                            run = paragraph.createRun();
                                            run.setText("Respuesta servicio");
                                            run.setBold(true);

                                            for (AttributeClass response : sitio.getFinalResponses()) {
                                                XWPFTableRow tableRowTwo = table.createRow();
                                                tableRowTwo.getCell(0).setText(response.getKey());
                                                tableRowTwo.getCell(1).setText(response.getValue());
                                            }

                                            run.addBreak();
                                            run.addBreak();
                                        }

                                    }


                                    FileOutputStream outputStream = new FileOutputStream(fileLocation);
                                    document.write(outputStream);
                                    document.close();
                                    outputStream.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON sqlinjection a un documento PDF
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          cant_sitios[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_exitosos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "sqlinjection_report_pdf":
                    fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                InjectionSQLReportModel modelo = g.fromJson(json, InjectionSQLReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".pdf";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".pdf";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }
                                // LISTADO DE RIESGOS SEG??N LA NORMATIVA ISO 27001
                                ArrayList<String> ListaRiegos = new ArrayList<String>();
                                ListaRiegos.add("R1 Acceso a los sistemas de informaci??n o recursos tecnol??gicos por usuarios no autorizados.");
                                ListaRiegos.add("R4 Da??os en la informaci??n por accesos no autorizados.");
                                ListaRiegos.add("R6 Fuga de Informaci??n.");
                                ListaRiegos.add("R18 Da??o al centro de procesamiento de datos y/o servidores.");
                                ListaRiegos.add("R19 Indisponibilidad del servidor de base de datos y/o aplicaciones.");
                                ListaRiegos.add("R34 Ataques a sitios o aplicaciones web.");

                                // LISTADO DE AMENAZAS SEG??N LA NORMATIVA ISO 27001
                                ArrayList<String> ListaAmenazas = new ArrayList<String>();
                                ListaAmenazas.add("A27 La manipulaci??n de software.");
                                ListaAmenazas.add("A42 La negaci??n de las acciones.");
                                ListaAmenazas.add("A44 Ataques de identificaci??n y autenticaci??n de usuarios.");
                                ListaAmenazas.add("A48 Desbordamiento de buffer de memoria.");
                                ListaAmenazas.add("A53 Ataques de denegaci??n de servicios.");
                                ListaAmenazas.add("A59 Recolecci??n de informaci??n a trav??s de fuentes abiertas.");
                                ListaAmenazas.add("A60 Acceso no autorizado a los equipos de c??mputo y/o servidores.");
                                ListaAmenazas.add("A61 Ataques a contrase??as de los equipos de c??mputo y/o servidores.");
                                ListaAmenazas.add("A62 Ataques de denegaci??n de servicios.");
                                ListaAmenazas.add("A63 Ataques de ejecuci??n de c??digo.");

                                // LISTADO DE VULNERABILIDADES SEG??N LA NORMATIVA ISO 27001
                                ArrayList<String> ListaVulnerabilidades= new ArrayList<String>();
                                ListaVulnerabilidades.add("V59 Fallas o ausencia de procedimientos de monitoreo y/o seguimiento de los recursos de informaci??n.");
                                ListaVulnerabilidades.add("V60 Fallas o ausencia de auditor??as peri??dicas.");
                                ListaVulnerabilidades.add("V61 Fallas o ausencia en los procedimientos de identificaci??n y valoraci??n de riesgos.");
                                ListaVulnerabilidades.add("V67 Fallas o ausencia de un procedimiento establecido para la supervisi??n del registro del SGSI y ciberseguridad.");

                                //LISTADO DE CONTROLES
                                ArrayList<String> ListadoControles = new ArrayList<String>();
                                ListadoControles.add(" A.8.3.3 - Transferencia de medios f??sicos: Los medios que contienen informaci??n se deben proteger contra acceso no autorizado, uso indebido o corrupci??n durante el transporte.");
                                ListadoControles.add(" A.9.2.1 - Registro y cancelaci??n del registro de usuarios: Se debe implementar un proceso formal de registro y de cancelaci??n de registro de usuarios, para posibilitar la asignaci??n de los derechos de acceso.");
                                ListadoControles.add(" A.11.1.4 - Protecci??n contra amenazas externas y ambientales: Se debe dise??ar y aplicar protecci??n f??sica contra desastres naturales, ataques maliciosos o accidentales.");
                                ListadoControles.add(" A.12.1.3 - Gesti??n de capacidad: Se debe hacer seguimiento al uso de recursos, hacer los ajustes, y hacer proyecciones de los requisitos de capacidad futura, para asegurar el desempe??o requerido del sistema.");
                                ListadoControles.add(" A.12.2.1 - Controles contra c??digos maliciosos: Se deben implementar controles de detecci??n, de prevenci??n y de recuperaci??n, combinados con la toma de conciencia apropiada de los usuarios, para proteger contra c??digos maliciosos.");
                                ListadoControles.add(" A.12.3.1 - Respaldo de la informaci??n: Se deben hacer copias de respaldo de la informaci??n, software e im??genes de los sistemas, y ponerlas a prueba regularmente de acuerdo con una pol??tica de copias de respaldo acordadas.");
                                ListadoControles.add(" A.12.4.1 - Registro de eventos: Se deben elaborar, conservar y revisar regularmente los registros acerca de actividades del usuario, excepciones, fallas y eventos de seguridad de la informaci??n.");
                                ListadoControles.add(" A.14.1.3 - Protecci??n de transacciones de los servicios de las aplicaciones: La informaci??n involucrada en las transacciones de los servicios de las aplicaciones se debe proteger para evitar la transmisi??n incompleta, el enrutamiento errado, la alteraci??n no autorizada de mensajes, la divulgaci??n no autorizada, y la duplicaci??n o reproducci??n de mensajes no autorizada.  ");
                                ListadoControles.add(" A.16.1.3 - Reporte de debilidades de seguridad de la informaci??n: Se debe exigir a todos los empleados y contratistas que usan los servicios y sistemas de informaci??n de la organizaci??n, que observen y reporten cualquier debilidad de seguridad de la informaci??n observada o sospechada en los sistemas o servicios.");

                                ArrayList<String> ListadoRemediaciones = new ArrayList<String>();
                                ListadoRemediaciones.add("Ubicar el servidor web en una zona desmilitarizada (entre cortafuegos), tambi??n llamada DMZ, evitando as?? que un intruso pueda acceder a la red interna si vulnera el servidor web;");
                                ListadoRemediaciones.add("implementar un sistema de detecci??n y prevenci??n de intrusiones (IDS/IPS) que monitorizan las conexiones y nos alerta si detecta intentos de acceso no autorizados o mal uso de protocolos;");
                                ListadoRemediaciones.add("utilizar un dispositivo o software con funcionalidad mixta (antivirus, cortafuegos y otras), como un UTM que permite gestionar de manera unificada la mayor??a de ciberamenazas que pueden afectar");
                                ListadoRemediaciones.add("Redundancia y balance de carga - (La redundancia consiste en tener el activo duplicado en m??s de un servidor y el balanceado de carga permite que se asigne a un servidor u otro en funci??n de la carga de trabajo que est?? soportando. Esta medida reduce los riesgos de sufrir uno de estos ataques, ya que al tener m??s de un servidor se reducir?? la posibilidad de que se detenga debido a la sobrecarga. Adem??s, aporta otras ventajas como la tolerancia a los fallos, ya que si un servidor cae, el total del trabajo lo asumir??a el otro servidor.)");
                                ListadoRemediaciones.add("Soluciones de seguridad basadas en la nube - Una de las soluciones que cualquier servicio web considerado cr??tico debe tener es un cortafuegos de aplicaci??n o WAF por sus siglas en ingl??s Web Application Firewall. ");
                                ListadoRemediaciones.add("Validaci??n y gesti??n de actualizaciones al sistema operativo, softwares en el sistema entre otros, plantear un monitoreo de cantidad de solicitudes por minuto, identificando y bloqueando IPS, que gestionen mas de N cantidad de solicitudes por minuto.");
                                ListadoRemediaciones.add("Puede validar la siguiente pagina la cual le ayudara a gestionar esta vulnerabilidad : https://aws.amazon.com/es/shield/ddos-attack-protection/");


                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"...");

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }

                                    Random random_method = new Random();
                                    StringBuilder buf = new StringBuilder();
                                    buf.append("<html>");
                                        buf.append("<head>");
                                            buf.append("<style language='text/css'>");
                                                buf.append(".titulo {padding-top: 3em;margin-bottom: 2em;}");
                                                buf.append(".heads th{text-align: center;vertical-align: middle;}");
                                                buf.append(".estilo {column-count:2; }");
                                                buf.append(".flex-container { background-color:black;}");
                                                buf.append(".row {display: flex;}");
                                                buf.append(".column {flex: 50%; padding: 10px; background-color:#aaa;}");
                                            buf.append("</style>");
                                        buf.append("</head>");

                                        Main.LOG.info("Escribiendo datos...");
                                        buf.append("<body>");
                                            buf.append("<section>");
                                                buf.append("<div class='flex-container' >");
                                                buf.append("<table>");
                                                    buf.append("<tr>");
                                                    buf.append("<th><img src='img/imagen1.jpg' width='300' height='900'/></th>");
                                                    buf.append("<th style='color: white;'>");
                                                        buf.append("<h1>Reporte InjectionSQL - CiberBot</h1>" );
                                                        buf.append("<p>").append(modelo.getHost()).append("</p>");
                                                        buf.append("<b>Fecha: </b>").append(sdf.format(modelo.getTimeStarted())).append("<br/>");
                                                    buf.append("</th>");
                                                    buf.append("</tr>");
                                                buf.append("</table>");
                                                buf.append("<a style='color: white;' align='center' > Desarrollado por Seguridad Atlas Ltda| ?? Todos los derechos reservados - 2021 </a>");
                                                buf.append("</div>");
                                            buf.append("</section>");
                                            buf.append("<section>");
                                                buf.append("<div style=\"margin: 10px;\" >");
                                                    buf.append("<b>Resultados de la ejecuci??n del ataque de InjectionSQL </b>");
                                                    buf.append("<img src='atlas.jpeg' width='150' height='99' ></img>");
                                                buf.append("</div>");
                                                buf.append("<div style=\"border: ridge #0f0fef 1px;\" >");

                                                    // host[=true/false] (por defecto true)
                                                    if (att.get("host") == null || !att.get("host").equals("false")) {

                                                        StringBuilder hosts = new StringBuilder();
                                                        String coma = "";
                                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                                            hosts.append(coma).append(sitio.getValue());
                                                            coma = "; ";
                                                        }
                                                        buf.append("<b>Hosts objetivo: </b>").append(hosts.toString()).append("<br/>");
                                                    }
                                                buf.append("</div>");
                                            buf.append("</section>");
                                            buf.append("<section>");
                                                buf.append("<table border='1' cellspacing='0'>");
                                                    buf.append("<tr class=\"heads\"><th colspan='7'>Reporte Detallado</th></tr>");
                                                    buf.append("<tr class=\"heads\">");
                                                        buf.append("<th>ID</th>");
                                                        buf.append("<th>HTTP Status</th>");
                                                        buf.append("<th>Amenazas</th>");
                                                        buf.append("<th>Vulnerabilidades</th>");
                                                        buf.append("<th>Riesgos</th>");
                                                        buf.append("<th>Control</th>");
                                                        buf.append("<th>Remediaciones</th>");
                                                    buf.append("</tr>");
                                                    buf.append("<tr class=\"heads\">");
                                                        buf.append("<h1>No se encontraron brechas de seguridad.</h1>");
                                                    buf.append("</tr>");
                                                    // detallado_exitosos[=true/false] (por defecto false)
                                                    if (att.get("detallado_exitosos") != null && att.get("detallado_exitosos").equals("true")) {
                                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                                            for (AttributeClass response : sitio.getFinalResponses()) {

                                                                buf.append("<tr>");
                                                                buf.append("<td>").append(response.getKey()).append("</td>");
                                                                buf.append("<td>").append(escapeHtml4(response.getValue())).append("</td>");
                                                                buf.append("</tr>");
                                                            }
                                                        }
                                                    }
                                                buf.append("</table>");
                                            buf.append("</section>");
                                        buf.append("</body>");
                                    buf.append("</html>");

//                                    String rawString = "Entwickeln Sie mit Vergn??gen";
                                    byte[] bytes = buf.toString().getBytes(StandardCharsets.UTF_8);

                                    // parse the markup into an xml Document
                                    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                                    Document doc = builder.parse(new ByteArrayInputStream(bytes));

                                    ITextRenderer renderer = new ITextRenderer();
                                    renderer.setDocument(doc, null);

                                    OutputStream os = new FileOutputStream(fileLocation);
                                    renderer.layout();
                                    renderer.createPDF(os);
                                    os.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
                    }
                    break;

                // Inicia un ataque de autenticaci??n por fuerza bruta
                // Command => URL del objetivo a atacar o Ruta absoluta del archivo plano que contiene la colecci??n de URLs objetivo
                //              En caso de usar una colecci??n: Se pueden especificar los IDs de los campos usuario / contrase??a separados por espacio Ej:
                //              http://sitioweb.com inputUserid inputPassid
                //              http://segundositio.com uid passid
                //              ...
                // prop(0) => Ruta absoluta del archivo de generaci??n de datos (Ubicaci??n y Nombre del reporte)
                // prop(1) => Ruta absoluta del archivo que contiene la colecci??n del par usuario-contrase??a para realizar el ataque
                // prop(2) => ID o Name del campo de usuario en la web
                // prop(3) => ID o Name del campo de contrase??a en la web
                // prop(4) => [No obligatorio] Separador de usuario-contrase??a en archivos planos (Ej: '=', ',', '-')
                //                              Indicar "celda" si la biblioteca es un archivo excel (Por defecto "celda")
                // prop(x+5) => [No obligatorio] Las propiedades en la sexta posicion en adelante se usar??n para identificar falsos positivos,
                //              es decir, textos HTML que identifican que un ataque NO es exitoso (Ej: html que contiene la caja de texto del usuario login)
                //              Es posible escapar saltos de l??nea con la clave "{nl}", por ejemplo si el html tiene un salto de l??nea as??: Ej:
                //              <input id="wpName1" name="wpName" size="20" class="loginText mw-ui-input"{nl} placeholder="Escribe tu nombre de usuario" tabindex="1" required="" autofocus="" autocomplete="username">
                case "bruteforce":
                    Main.LOG.info("Ejecutando " + cmd.getType());
                    host = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (host != null && !"".equals(host.trim())) {
                        if (cmd.getProperties().size() >= 4 && cmd.getProperties().get(0) != null &&
                                cmd.getProperties().get(1) != null && cmd.getProperties().get(2) != null &&
                                cmd.getProperties().get(3) != null) {

                            boolean parametrosValidos = true;
                            String respuesta = cmd.getProperties().get(0);

                            WriteAndReadFile files = new WriteAndReadFile();

                            List<String> objetivos = new ArrayList<>();
                            objetivos.add(host);
                            File arhivoUrls = new File(host);
                            if (arhivoUrls.exists()) {
                                objetivos = files.ReadLine(host);
                            }

                            File bibliotecaFile;
                            String bibliotecaUbicacion = cmd.getProperties().get(1);
                            if (!"".equals(bibliotecaUbicacion.trim())) {
                                bibliotecaFile = new File(bibliotecaUbicacion);
                                if (!bibliotecaFile.exists()) {
                                    Main.LOG.error("Archivo de biblioteca no encontrado: \"" + bibliotecaUbicacion + "\"");
                                    parametrosValidos = false;
                                }
                            }

                            String userTxtForm = cmd.getProperties().get(2);
                            if ("".equals(userTxtForm.trim())) {
                                Main.LOG.error("Nombre del campo el usuario no enviado");
                                parametrosValidos = false;
                            }

                            String passTxtForm = cmd.getProperties().get(3);
                            if ("".equals(passTxtForm.trim())) {
                                Main.LOG.error("Nombre del campo de contrase??a no enviado");
                                parametrosValidos = false;
                            }

                            String separador = "celda";

                            if (cmd.getProperties().size() >= 5 && cmd.getProperties().get(4) != null &&
                                    !"".equals(cmd.getProperties().get(4))) {
                                separador = cmd.getProperties().get(4);
                            }

                            List<String> falsosPositivos = new ArrayList<>();
                            for (int i = 5; i < cmd.getProperties().size(); i++) {
                                falsosPositivos.add(cmd.getProperties().get(i));
                            }

                            Main.LOG.info("Cantidad falsos positivos: " + falsosPositivos.size());

                            if (falsosPositivos.size() <= 0) {
                                Main.LOG.error("Falsos positivos es NULL (propiedad en sexta posicion)");
                            }

                            Gson g = new Gson();
                            Main.LOG.info("parametrosValidos: " + parametrosValidos);
                            if (parametrosValidos) {
                                InjectionSQLReportModel modelo = new InjectionSQLReportModel(host);
                                modelo.setAttacksSitesRequested(objetivos.size());

                                Main.LOG.info("objetivos.size: " + objetivos.size());
                                int fallidos = 0;
                                for (String destino : objetivos) {
                                    long solicitados = 0;
                                    if (destino.trim().contains(" ")) {
                                        String[] configuracion = destino.split(" ");
                                        destino = configuracion[0];
                                        userTxtForm = configuracion.length > 1 ? configuracion[1] : userTxtForm;
                                        passTxtForm = configuracion.length > 2 ? configuracion[2] : passTxtForm;
                                    }
                                    Main.LOG.info("Intentando login en: " + destino);

                                    AttributeOfAttributeClass modeloResp = new AttributeOfAttributeClass(destino);
                                    try {
                                        // Si el separador es una "celda" se leer?? un archivo excel
                                        if ("celda".equals(separador)) {
                                            // Usar EXCEL
                                            Workbook workbook = WorkbookFactory.create(new File(bibliotecaUbicacion));
                                            Sheet sheet = workbook.getSheetAt(0);
                                            Iterator<Row> rowIterator = sheet.rowIterator();
                                            DataFormatter dataFormatter = new DataFormatter();
                                            while (rowIterator.hasNext()) {
                                                modelo.incrementAttacksCountTotalDone();
                                                Row row = rowIterator.next();

                                                Cell cell = row.getCell(0);
                                                String user = dataFormatter.formatCellValue(cell);
                                                Cell cell2 = row.getCell(1);
                                                String pass = dataFormatter.formatCellValue(cell2);

                                                String htmlOk = this.realizarLogin(destino, userTxtForm, user, passTxtForm, pass, falsosPositivos);
                                                if (!"".equals(htmlOk)) {
                                                    modelo.incrementAttacksCountTotalSuccess();
                                                    modeloResp.getFinalResponses().add(new AttributeClass(user, pass, htmlOk));
                                                }

                                            }
                                            workbook.close();

                                        } else {
                                            // Usar archivo plano separado por caracter dado
                                            FileReader f = new FileReader(bibliotecaUbicacion);
                                            BufferedReader b = new BufferedReader(f);
                                            String cadena;
                                            // While para recorrer linea por linea del archivo de biblioteca

                                            Main.LOG.info("bibliotecaUbicacion: " + bibliotecaUbicacion);
                                            while ((cadena = b.readLine()) != null) {
                                                if ("".equals(cadena.trim())) {
                                                    // Linea vacia o solo con espacios en blanco
                                                    continue;
                                                }
                                                solicitados++;
                                                modelo.incrementAttacksCountTotalDone();

                                                String user;
                                                String pass = "";
                                                if (cadena.contains(separador)) {
                                                    int pos = cadena.indexOf(separador);
                                                    int tam = separador.length();
                                                    user = cadena.substring(0, pos);
                                                    if (cadena.length() > pos+tam) {
                                                        pass = cadena.substring(pos + tam);
                                                    }
                                                } else {
                                                    user = cadena;
                                                }

                                                String htmlOk = this.realizarLogin(destino, userTxtForm, user, passTxtForm, pass, falsosPositivos);
                                                if (!"".equals(htmlOk)) {
                                                    modelo.incrementAttacksCountTotalSuccess();
                                                    modeloResp.getFinalResponses().add(new AttributeClass(user, pass, htmlOk));
                                                }

                                            } // While - biblioteca
                                            b.close();

                                        }
                                    } catch (Exception ex) {
                                        fallidos++;
                                        Main.LOG.error("Error al intentar login en sitio: " + destino, ex);
                                    }

                                    modelo.getFinalResponses().add(modeloResp);
                                    modelo.setAttacksCountTotalRequested(solicitados);
                                }

                                try {
                                    modelo.setTimeFinished(new Date());
                                    files.Write(respuesta, g.toJson(modelo));
                                    if (fallidos > 0) {
                                        Main.LOG.info("Cant. sitios fallidos: " + fallidos);
                                    }
                                    if (fallidos != objetivos.size()) {
                                        output = "Ok";
                                    }
                                } catch (Exception e) {
                                    Main.LOG.error("No se ha podido guardar el json final: " + g.toJson(modelo), e);
                                }

                            }

                        } else {
                            Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar las propiedades (0) Ruta del reporte, (1) Ruta de la biblioteca");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un HOST/URL en el comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON de Fuerza Bruta a un archivo excel XLSX
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          cant_sitios[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_exitosos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "bruteforce_report_xlsx":
                    fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                InjectionSQLReportModel modelo = g.fromJson(json, InjectionSQLReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".xlsx";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".xlsx";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }

                                int indexSheet = 0;
                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"... Los datos se guardar??n en la hoja: " + (indexSheet+1));

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }
                                    XSSFWorkbook workbook = new XSSFWorkbook();
                                    XSSFSheet sheet = workbook.createSheet("Reporte");

                                    sheet.setColumnWidth(0, 256 * 26); // Configurando ancho como para 26 caracteres
                                    sheet.setColumnWidth(1, 256 * 26); // Configurando ancho como para 26 caracteres

                                    Main.LOG.info("Escribiendo columnas...");

                                    int offset = 0;
                                    Row row = sheet.createRow(offset);
                                    Cell cell = row.createCell(0);
                                    cell.setCellValue("Resultados de la ejecuci??n del ataque de Fuerza Bruta");
                                    CellRangeAddress region = new CellRangeAddress(offset, offset, 0, 4);
                                    sheet.addMergedRegion(region);
                                    CellStyle centerBold = workbook.createCellStyle();
                                    centerBold.setAlignment(HorizontalAlignment.CENTER);
                                    XSSFFont bold = workbook.createFont();
                                    bold.setBold(true);
                                    centerBold.setFont(bold);
                                    cell.setCellStyle(centerBold);

                                    // host[=true/false] (por defecto true)
                                    if (att.get("host") == null || !att.get("host").equals("false")) {
                                        row = sheet.createRow(offset + 2);
                                        cell = row.createCell(0);
                                        String plural = modelo.getFinalResponses().size() > 1 ? "Hosts" : "Host";
                                        cell.setCellValue(plural + " objetivo:");
                                        cell = row.createCell(1);
                                        StringBuilder hosts = new StringBuilder();
                                        String coma = "";
                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                            hosts.append(coma).append(sitio.getValue());
                                            coma = "; ";
                                        }
                                        cell.setCellValue(hosts.toString());
                                        region = new CellRangeAddress(offset + 2, offset + 2, 1, 5);
                                        sheet.addMergedRegion(region);
                                        offset++;
                                    }

                                    // fecha_inicio[=true/false] (por defecto true)
                                    if (att.get("fecha_inicio") == null || !att.get("fecha_inicio").equals("false")) {
                                        row = sheet.createRow(offset + 2);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Inicio:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(sdf.format(modelo.getTimeStarted()));
                                        region = new CellRangeAddress(offset + 2,offset + 2, 1, 5);
                                        sheet.addMergedRegion(region);
                                        offset++;
                                    }

                                    // fecha_fin[=true/false] (por defecto true)
                                    if (att.get("fecha_fin") == null || !att.get("fecha_fin").equals("false")) {
                                        row = sheet.createRow(offset + 2);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Fin:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(sdf.format(modelo.getTimeFinished()));
                                        region = new CellRangeAddress(offset + 2,offset + 2, 1, 5);
                                        sheet.addMergedRegion(region);
                                        offset++;
                                    }

                                    // cant_sitios[=true/false] (por defecto true)
                                    if (modelo.getAttacksSitesRequested() > 0 && (att.get("cant_sitios") == null || !att.get("cant_sitios").equals("false"))) {
                                        row = sheet.createRow(offset + 3);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Cantidad de sitios:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksSitesRequested()));
                                        offset++;
                                    }

                                    // cant_solicitados[=true/false] (por defecto true)
                                    if (modelo.getAttacksCountTotalRequested() > 0 && (att.get("cant_solicitados") == null || !att.get("cant_solicitados").equals("false"))) {
                                        row = sheet.createRow(offset + 3);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Ataques solicitados:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalRequested()));
                                        offset++;
                                    }

                                    // cant_realizados[=true/false] (por defecto true)
                                    if (att.get("cant_realizados") == null || !att.get("cant_realizados").equals("false")) {
                                        row = sheet.createRow(offset + 3);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Ataques realizados:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalDone()));
                                        offset++;
                                    }

                                    // cant_exitosos[=true/false] (por defecto true)
                                    if (att.get("cant_exitosos") == null || !att.get("cant_exitosos").equals("false")) {
                                        row = sheet.createRow(offset + 3);
                                        cell = row.createCell(0);
                                        cell.setCellValue("Ataques exitosos:");
                                        cell = row.createCell(1);
                                        cell.setCellValue(String.valueOf(modelo.getAttacksCountTotalSuccess()));
                                        offset++;
                                    }


                                    // detallado_exitosos[=true/false] (por defecto false)
                                    if (att.get("detallado_exitosos") != null && att.get("detallado_exitosos").equals("true")) {

                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {

                                            row = sheet.createRow(offset + 4);
                                            cell = row.createCell(0);
                                            cell.setCellValue("Host:");
                                            cell = row.createCell(1);
                                            cell.setCellValue(sitio.getValue());

                                            row = sheet.createRow(offset + 5);
                                            cell = row.createCell(0);
                                            cell.setCellValue("Detallado de resultados exitosos (" + sitio.getFinalResponses().size() + ")");
                                            region = new CellRangeAddress(offset + 5,offset + 5, 0, 4);
                                            sheet.addMergedRegion(region);
                                            cell.setCellStyle(centerBold);

                                            RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);

                                            row = sheet.createRow(offset + 6);
                                            cell = row.createCell(0);
                                            cell.setCellValue("Usuario");
                                            cell.setCellStyle(centerBold);
                                            cell = row.createCell(1);
                                            cell.setCellValue("Contrase??a");
                                            cell.setCellStyle(centerBold);
                                            cell = row.createCell(2);
                                            cell.setCellValue("Respuesta extra??da");
                                            cell.setCellStyle(centerBold);
                                            region = new CellRangeAddress(offset + 6,offset + 6, 2, 4);
                                            sheet.addMergedRegion(region);

                                            RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);

                                            region = new CellRangeAddress(offset + 6,offset + 6, 0, 0);
                                            RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);

                                            region = new CellRangeAddress(offset + 6,offset + 6, 1, 1);
                                            RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                            RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);

                                            for (AttributeClass response : sitio.getFinalResponses()) {
                                                row = sheet.createRow(offset + 7);
                                                cell = row.createCell(0);
                                                cell.setCellValue(response.getKey());
                                                cell = row.createCell(1);
                                                cell.setCellValue(response.getValue());

                                                String textCell = response.getMsg();
                                                // Si entra al IF es porque el HTML de la respuesta no cabe en una celda de Excel,
                                                // se va descomponiendo en sus celdas a la derecha
                                                if (textCell.length() > this.excelMaxCharsCell) {
                                                    int cellNumber = 2;
                                                    while (textCell.length() > this.excelMaxCharsCell) {
                                                        cell = row.createCell(cellNumber++);
                                                        cell.setCellValue(textCell.substring(0, this.excelMaxCharsCell));
                                                        textCell = textCell.substring(this.excelMaxCharsCell);
                                                    }

                                                    cell = row.createCell(cellNumber);
                                                } else {
                                                    cell = row.createCell(2);
                                                }
                                                cell.setCellValue(textCell);

                                                region = new CellRangeAddress(offset + 7,offset + 7, 0, 0);
                                                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
                                                region = new CellRangeAddress(offset + 7,offset + 7, 1, 1);
                                                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                                region = new CellRangeAddress(offset + 7,offset + 7, 2, 4);
                                                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                                                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                                                offset++;
                                            }

                                            offset += 6;
                                        }


                                    }

                                    FileOutputStream outputStream = new FileOutputStream(fileLocation);
                                    workbook.write(outputStream);
                                    workbook.close();
                                    outputStream.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON de Fuerza Bruta a un archivo word DOCX
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          cant_sitios[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_exitosos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "bruteforce_report_docx":
                    fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                InjectionSQLReportModel modelo = g.fromJson(json, InjectionSQLReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".docx";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".docx";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }

                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"...");

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }


                                    XWPFDocument document = new XWPFDocument();

                                    Main.LOG.info("Escribiendo datos...");

                                    XWPFParagraph paragraph = document.createParagraph();
                                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                                    XWPFRun run = paragraph.createRun();
                                    run.setText("Resultados de la ejecuci??n del ataque de Fuerza Bruta");
                                    run.setBold(true);
                                    run.addBreak();

                                    paragraph = document.createParagraph();
                                    paragraph.setAlignment(ParagraphAlignment.LEFT);

                                    // host[=true/false] (por defecto true)
                                    if (att.get("host") == null || !att.get("host").equals("false")) {
                                        run = paragraph.createRun();
                                        String plural = modelo.getFinalResponses().size() > 1 ? "Hosts" : "Host";
                                        run.setText(plural + " objetivo: ");
                                        run.setBold(true);

                                        StringBuilder hosts = new StringBuilder();
                                        String coma = "";
                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                            hosts.append(coma).append(sitio.getValue());
                                            coma = "; ";
                                        }
                                        run = paragraph.createRun();
                                        run.setText(hosts.toString());
                                        run.addBreak();
                                    }

                                    // fecha_inicio[=true/false] (por defecto true)
                                    if (att.get("fecha_inicio") == null || !att.get("fecha_inicio").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Inicio: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(sdf.format(modelo.getTimeStarted()));
                                        run.addBreak();
                                    }

                                    // fecha_fin[=true/false] (por defecto true)
                                    if (att.get("fecha_fin") == null || !att.get("fecha_fin").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Fin: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(sdf.format(modelo.getTimeFinished()));
                                        run.addBreak();
                                    }

                                    run.addBreak();

                                    // cant_solicitados[=true/false] (por defecto true)
                                    if (modelo.getAttacksCountTotalRequested() > 0 && (att.get("cant_solicitados") == null || !att.get("cant_solicitados").equals("false"))) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques solicitados: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalRequested()));
                                        run.addBreak();
                                    }

                                    // cant_realizados[=true/false] (por defecto true)
                                    if (att.get("cant_realizados") == null || !att.get("cant_realizados").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques realizados: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalDone()));
                                        run.addBreak();
                                    }

                                    // cant_exitosos[=true/false] (por defecto true)
                                    if (att.get("cant_exitosos") == null || !att.get("cant_exitosos").equals("false")) {
                                        run = paragraph.createRun();
                                        run.setText("Ataques exitosos: ");
                                        run.setBold(true);
                                        run = paragraph.createRun();
                                        run.setText(String.valueOf(modelo.getAttacksCountTotalSuccess()));
                                        run.addBreak();
                                    }

                                    // detallado_hilos[=true/false] (por defecto false)
                                    if (att.get("detallado_exitosos") != null && att.get("detallado_exitosos").equals("true")) {

                                        boolean saltos = false;
                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                            paragraph = document.createParagraph();
                                            paragraph.setAlignment(ParagraphAlignment.CENTER);
                                            run = paragraph.createRun();
                                            if (saltos) {
                                                run.addBreak();
                                                run.addBreak();
                                            } else {
                                                saltos = true;
                                            }
                                            run.setText("Detallado de resultados exitosos (" + sitio.getFinalResponses().size() + ")");
                                            run.setBold(true);
                                            run.addBreak();
                                            run = paragraph.createRun();
                                            run.setText("(Host: " + sitio.getValue() + ")");

                                            XWPFTable table = document.createTable();

                                            //create first row
                                            XWPFTableRow tableRowOne = table.getRow(0);
                                            paragraph = tableRowOne.getCell(0).addParagraph();
                                            paragraph.setAlignment(ParagraphAlignment.CENTER);
                                            run = paragraph.createRun();
                                            run.setText("Usuario");
                                            run.setBold(true);

                                            paragraph = tableRowOne.addNewTableCell().addParagraph();
                                            paragraph.setAlignment(ParagraphAlignment.CENTER);
                                            run = paragraph.createRun();
                                            run.setText("Contrase??a");
                                            run.setBold(true);

                                            paragraph = tableRowOne.addNewTableCell().addParagraph();
                                            paragraph.setAlignment(ParagraphAlignment.CENTER);
                                            run = paragraph.createRun();
                                            run.setText("Respuesta extra??da");
                                            run.setBold(true);

                                            for (AttributeClass response : sitio.getFinalResponses()) {
                                                XWPFTableRow tableRowTwo = table.createRow();
                                                tableRowTwo.getCell(0).setText(response.getKey());
                                                tableRowTwo.getCell(1).setText(response.getValue());
                                                tableRowTwo.getCell(2).setText(response.getMsg());
                                            }

                                            run.addBreak();
                                            run.addBreak();
                                        }

                                    }


                                    FileOutputStream outputStream = new FileOutputStream(fileLocation);
                                    document.write(outputStream);
                                    document.close();
                                    outputStream.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
                    }
                    break;

                // Exporta los resultados del JSON sqlinjection a un documento PDF
                // El nombre del reporte ser?? el mismo nombre del Command pero con diferente extensi??n
                // Command => Ruta absoluta al archivo JSON original
                // prop(x) => Las propiedades no son obligatorias y pueden ser las siguientes en cualquier orden
                //          host[=true/false] (por defecto true)
                //          fecha_inicio[=true/false] (por defecto true)
                //          fecha_fin[=true/false] (por defecto true)
                //          fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                //          cant_sitios[=true/false] (por defecto true)
                //          cant_solicitados[=true/false] (por defecto true)
                //          cant_realizados[=true/false] (por defecto true)
                //          cant_exitosos[=true/false] (por defecto true)
                //          detallado_exitosos[=true/false] (por defecto false)
                //          nombre_reporte=string (por defecto es el mismo Command)
                case "bruteforce_report_pdf":
                    fileOrigen = cmd.getCommand();
                    output = "fail"; // Por defecto falla, si ejecuta los metodos, cambia a ok
                    if (fileOrigen != null) {
                        File origen = new File(fileOrigen);
                        if (origen.exists()) {
                            try {
                                // inicializar parametros
                                Map<String, String> att = new HashMap<>();
                                for (String prop : cmd.getProperties()) {
                                    if (prop != null) {
                                        if (prop.contains("=")) {
                                            String[] propSplit = prop.split("=");
                                            Main.LOG.info("Agregando prop: " + propSplit[0] + "=" + propSplit[1]);
                                            att.put(propSplit[0], propSplit[1]);
                                        } else {
                                            Main.LOG.info("Agregando prop: " + prop + "=true");
                                            att.put(prop, "true");
                                        }
                                    }
                                }

                                // Inicializar objeto json
                                WriteAndReadFile files = new WriteAndReadFile();
                                String json = files.Read(fileOrigen);
                                Gson g = new Gson();
                                InjectionSQLReportModel modelo = g.fromJson(json, InjectionSQLReportModel.class);

                                String rutaFile = origen.getParent();
                                String fileLocation;
                                // nombre_reporte=string (por defecto es el mismo Command)
                                if (att.get("nombre_reporte") != null && !"".equals(att.get("nombre_reporte").trim())) {
                                    fileLocation = rutaFile + "/" + att.get("nombre_reporte") + ".pdf";
                                } else {
                                    int punto = origen.getName().lastIndexOf(".");
                                    String nameFile = origen.getName().substring(0, punto) + ".pdf";
                                    fileLocation = rutaFile + "/" + nameFile;
                                }

                                Main.LOG.info("Inicializando archivo \"" + fileLocation + "\"...");

                                File f = new File(fileLocation);
                                if (!f.exists() || f.delete()) {
                                    // fechas_formato=dd/MMMM/yyyy h:mm aa (por defecto yyyy-MM-dd HH:mm:ss)
                                    SimpleDateFormat sdf;
                                    if (att.get("fechas_formato") == null) {
                                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    } else {
                                        sdf = new SimpleDateFormat(att.get("fechas_formato"), new Locale("es", "ES"));
                                    }

                                    StringBuilder buf = new StringBuilder();
                                    buf.append("<html>");

                                    // put in some style
                                    buf.append("<head><style language='text/css'>");
                                    buf.append(".titulo {padding-top: 3em;margin-bottom: 2em;}");
                                    buf.append(".heads th{text-align: center;vertical-align: middle;}");
                                    buf.append("</style></head>");

                                    Main.LOG.info("Escribiendo datos...");
                                    buf.append("<body>");
                                    buf.append("<center class=\"titulo\"><b>Resultados de la ejecuci??n del ataque de Fuerza Bruta</b><br/></center>");
                                    buf.append("<p>");

                                    // host[=true/false] (por defecto true)
                                    if (att.get("host") == null || !att.get("host").equals("false")) {

                                        StringBuilder hosts = new StringBuilder();
                                        String coma = "";
                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                            hosts.append(coma).append(sitio.getValue());
                                            coma = "; ";
                                        }
                                        String plural = modelo.getFinalResponses().size() > 1 ? "Hosts" : "Host";
                                        buf.append("<b>").append(plural).append(" objetivo: </b>").append(hosts.toString()).append("<br/>");
                                    }

                                    // fecha_inicio[=true/false] (por defecto true)
                                    if (att.get("fecha_inicio") == null || !att.get("fecha_inicio").equals("false")) {
                                        buf.append("<b>Inicio: </b>").append(sdf.format(modelo.getTimeStarted())).append("<br/>");
                                    }

                                    // fecha_fin[=true/false] (por defecto true)
                                    if (att.get("fecha_fin") == null || !att.get("fecha_fin").equals("false")) {
                                        buf.append("<b>Fin: </b>").append(sdf.format(modelo.getTimeFinished())).append("<br/>");
                                    }

                                    buf.append("</p><p>");

                                    // cant_solicitados[=true/false] (por defecto true)
                                    if (modelo.getAttacksCountTotalRequested() > 0 && (att.get("cant_solicitados") == null || !att.get("cant_solicitados").equals("false"))) {
                                        buf.append("<b>Ataques solicitados: </b>").append(modelo.getAttacksCountTotalRequested()).append("<br/>");
                                    }

                                    // cant_realizados[=true/false] (por defecto true)
                                    if (att.get("cant_realizados") == null || !att.get("cant_realizados").equals("false")) {
                                        buf.append("<b>Ataques realizados: </b>").append(modelo.getAttacksCountTotalDone()).append("<br/>");
                                    }

                                    // cant_exitosos[=true/false] (por defecto true)
                                    if (att.get("cant_exitosos") == null || !att.get("cant_exitosos").equals("false")) {
                                        buf.append("<b>Ataques exitosos: </b>").append(modelo.getAttacksCountTotalSuccess()).append("<br/>");
                                    }

                                    buf.append("</p>");

                                    // detallado_exitosos[=true/false] (por defecto false)
                                    if (att.get("detallado_exitosos") != null && att.get("detallado_exitosos").equals("true")) {

                                        boolean saltos = false;
                                        for (AttributeOfAttributeClass sitio : modelo.getFinalResponses()) {
                                            if (saltos) {
                                                buf.append("<br/><br/>");
                                            } else {
                                                saltos = true;
                                            }
                                            buf.append("<table border='1' cellspacing='0'>");
                                            buf.append("<tr class=\"heads\"><th colspan='6'>Detallado de resultados exitosos (")
                                                    .append(sitio.getFinalResponses().size()).append(")<br/>")
                                                    .append("(Host: ").append(sitio.getValue()).append(")</th></tr>");
                                            buf.append("<tr class=\"heads\">");
                                            buf.append("<th>Usuario</th><th>Contrase??a</th><th>Respuesta extraida</th>");
                                            buf.append("</tr>");

                                            for (AttributeClass response : sitio.getFinalResponses()) {

                                                buf.append("<tr>");
                                                buf.append("<td>").append(response.getKey()).append("</td>");
                                                buf.append("<td>").append(response.getValue()).append("</td>");
                                                buf.append("<td>").append(response.getMsg()).append("</td>");
                                                buf.append("</tr>");
                                            }
                                            buf.append("</table>");

                                        }

                                    }

                                    buf.append("</body>");
                                    buf.append("</html>");

//                                    String rawString = "Entwickeln Sie mit Vergn??gen";
                                    byte[] bytes = buf.toString().getBytes(StandardCharsets.UTF_8);

//                                    String utf8EncodedString = new String(bytes, StandardCharsets.ISO_8859_1);
//                                    Main.LOG.info(buf);

                                    // parse the markup into an xml Document
                                    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                                    Document doc = builder.parse(new ByteArrayInputStream(bytes));

                                    ITextRenderer renderer = new ITextRenderer();
                                    renderer.setDocument(doc, null);

                                    OutputStream os = new FileOutputStream(fileLocation);
                                    renderer.layout();
                                    renderer.createPDF(os);
                                    os.close();
                                    output = "Ok";
                                    Main.LOG.info("Proceso finalizado");

                                } else {
                                    Main.LOG.error("El archivo \"" + fileLocation + "\" no existe o esta ocupado por otro usuario");
                                }

                            } catch (Exception e) {
                                Main.LOG.error("No es posible realizar el volcado de datos, error: ", e);
                            }
                        } else {
                            Main.LOG.error("El archivo JSON original '" + fileOrigen + "' no existe ");
                        }
                    } else {
                        Main.LOG.error("Para ejecutar el comando '" + cmd.getType() + "' debe asignar un comando CMD");
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

        for (String prop : properties) {
            output.add(this.replaceCommand(prop));
        }

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

    public String getDownloadsLocation() {
        return downloadsLocation;
    }

    public void setDownloadsLocation(String downloadsLocation) {
        this.downloadsLocation = downloadsLocation;
    }

    public String getDriverExecutorLocation() {
        return driverExecutorLocation;
    }

    public void setDriverExecutorLocation(String driverExecutorLocation) {
        this.driverExecutorLocation = driverExecutorLocation;
    }

    public String getTimeSleep() {
        return timeSleep;
    }

    public void setTimeSleep(String timeSleep) {
        this.timeSleep = timeSleep;
    }

    public int getExcelMaxCharsCell() {
        return excelMaxCharsCell;
    }

    public void setExcelMaxCharsCell(int excelMaxCharsCell) {
        this.excelMaxCharsCell = excelMaxCharsCell;
    }

    public int getExcelMaxRows() {
        return excelMaxRows;
    }

    public void setExcelMaxRows(int excelMaxRows) {
        this.excelMaxRows = excelMaxRows;
    }

    private String escapeHtml4(String text) {
        String[] tildesEsc = {"&aacute;", "&eacute;", "&iacute;", "&oacute;", "&uacute;", "&ntilde;", "&Aacute;", "&Eacute;", "&Iacute;", "&Oacute;", "&Uacute;", "&Ntilde;", "&iquest;", "&iexcl;"};
        String[] tildes = {"??", "??", "??", "??", "??", "??", "??", "??", "??", "??", "??", "??", "??", "??"};
        for (int i = 0; i < tildes.length; i++) {
            text = text.replace(tildesEsc[i], tildes[i]);
        }
        // Reemplazar todos por vacios
        // Palabras que cumplan: Empiezan por & y terminan con ;
        text = text.replaceAll("&[A-Za-z]*;", "");
        text = text.replace("<", "&lt;").replace(">", "&gt;");
        return text;
    }

    private String realizarLogin(String host, String userTxtForm, String user, String passTxtForm, String pass, List<String> falsosPositivos) throws Exception {
        Connection.Response resp = Jsoup.connect(host)
                .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0")
                .ignoreHttpErrors(true)
                .method(Connection.Method.GET)
                .execute();

        org.jsoup.nodes.Document doc = resp.parse();

        // Llenar campo de usuario
        Element loginField1 = doc.selectFirst("#" + userTxtForm);
        if (loginField1 == null) {
            Main.LOG.info("ID #" + userTxtForm + " no encontrado, buscando nombre...");
            loginField1 = doc.selectFirst("[name=" + userTxtForm + "]");
            if (loginField1 == null) {
                Main.LOG.error("Error: Elemento " + userTxtForm + " no encontrado");
                throw new Exception("Error: Elemento " + userTxtForm + " no encontrado");
            }
        }
        loginField1.val(user);


        // Llenar campo de contrase??a
        Element loginField2 = doc.selectFirst("#" + passTxtForm);
        if (loginField2 == null) {
            Main.LOG.info("ID #" + passTxtForm + " no encontrado, buscando nombre...");
            loginField2 = doc.selectFirst("[name=" + passTxtForm + "]");
            if (loginField2 == null) {
                Main.LOG.error("Error: Elemento " + passTxtForm + " no encontrado");
                throw new Exception("Error: Elemento " + passTxtForm + " no encontrado");
            }
        }
        loginField2.val(pass);

        Element parent = loginField2.parent();

        while (parent != null) {
//            Main.LOG.info("Parent.Name: " + parent.nodeName());
            if ("form".equals(parent.nodeName())) {
                Main.LOG.info("Encontrado form, submiting...");
                FormElement loginForm = (FormElement) parent;

                Connection.Response respLogin = loginForm.submit()
                        .cookies(resp.cookies())
                        .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0")
                        .ignoreHttpErrors(true)
                        .execute();

                if (falsosPositivos == null || falsosPositivos.size() <= 0) {
                    org.jsoup.nodes.Document docLogin = respLogin.parse();
                    Element testing = docLogin.selectFirst("#" + userTxtForm);
                    if (testing == null) {
                        testing = docLogin.selectFirst("[name=" + userTxtForm + "]");
                        if (testing == null) {
                            Main.LOG.info("Intento de login finalizado (OK) user=" + user + ", response" + docLogin.text());
                            return docLogin.text();
                        }
                    }
                } else {
                    // Si alguno de los HTML se encuentra es que no se ha hecho login
                    for (String falso : falsosPositivos) {
                        // Reemplzar saltos de linea de {nl} a \n
                        if (respLogin.body().contains(falso.replace("{nl}","\n")) ||
                                respLogin.body().contains(falso.replace("{nl}","\r\n"))) {
                            return "";
                        }
                    }
                    org.jsoup.nodes.Document docLogin = respLogin.parse();
                    Main.LOG.info("Intento de login finalizado (OK) user=" + user + ", response" + docLogin.text());
                    return docLogin.text();
                }
                break;
            }
            parent = parent.parent();
        }

        return "";
    }
}
