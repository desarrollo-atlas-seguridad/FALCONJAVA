package com.arquitecsoft.model;

import com.arquitecsoft.controller.Main;
import com.arquitecsoft.util.FileLineCounter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ExecutorCommandModel {

    private String id;
    private String command;
    private String type; // normal, contains, function, [...]
    private List<String> properties;
    private String nextYes;
    private String nextNo;
    private String output;
    private String status;
    private String timeSleep;

    public ExecutorCommandModel(String command, String nextYes) {
        this.id = "INIT";
        this.type = "normal";
        this.command = command;
        this.nextYes = nextYes;
        this.nextNo = "END";
        this.output = "";
        this.status = "";
        this.properties = new ArrayList<>();
    }

    public ExecutorCommandModel(ExecutorCommandModel cmd) {
        if (cmd != null) {
            this.id = cmd.getId();
            this.type = cmd.getType();
            this.command = cmd.getCommand();
            this.nextYes = cmd.getNextYes();
            this.nextNo = cmd.getNextNo();
            this.output = cmd.getOutput();
            this.status = cmd.getStatus();
            this.properties = new ArrayList<>();
            if (cmd.getProperties() != null) {
                for (String d : cmd.getProperties()) {
                    this.properties.add(d);
                }
            }
        } else {
            this.id = "INIT";
            this.type = "normal";
            this.command = "";
            this.nextYes = "END";
            this.nextNo = "END";
            this.output = "";
            this.status = "";
            this.properties = new ArrayList<>();
        }
    }

    public ExecutorCommandModel(String id, String command, String type, String next) {
        this.id = id;
        this.command = command;
        this.type = type;
        this.nextYes = next;
        this.nextNo = "END";
        this.output = "";
        this.status = "";
        this.properties = new ArrayList<>();
    }

    public ExecutorCommandModel(String id, String command, String type, String nextYes, String nextNo) {
        this.id = id;
        this.command = command;
        this.type = type;
        this.nextYes = nextYes;
        this.nextNo = nextNo;
        this.output = "";
        this.status = "";
        this.properties = new ArrayList<>();
    }

    public ExecutorCommandModel(String id, String type, List<String> properties, String nextYes) {
        this.id = id;
        this.command = "";
        this.type = type;
        this.properties = properties;
        this.nextYes = nextYes;
        this.nextNo = "END";
        this.output = "";
        this.status = "";
    }

    public ExecutorCommandModel(String id, String type, List<String> properties, String nextYes, String nextNo) {
        this.id = id;
        this.command = "";
        this.type = type;
        this.properties = properties;
        this.nextYes = nextYes;
        this.nextNo = nextNo;
        this.output = "";
        this.status = "";
    }

    /**
     * Retorna la llave para el siguiente comando, basado en que tipo de comando
     * es (normal, validador, funcion...)
     *
     * @param outTerminalText Texto que responden el shell luego de ejecutar un
     * comando
     * @param addOuput Parametro que indica si se debe agregar toda la salida
     * del SHELL al parametro Output
     * @return String con la posicion siguiente
     */
    public String getNext(String outTerminalText, boolean addOuput) {
        Base64.Encoder encoder = Base64.getEncoder();
        if (addOuput) {
            this.output = encoder.encodeToString(outTerminalText.getBytes());
        } else {
            this.output = "";
        }
        String out;
        switch (this.type) {
            // Normal solo retorna su siguiente ejecucion
            case "normal":      // [CGUno, Browser, Desktop]
            case "key":         // [CGUno]
            case "wait":        // [CGUno, Browser]
            case "get":         // [Browser]
            case "clear":       // [Browser]
            case "dropdown":    // [Browser]
            case "back":        // [Browser]
            case "click":       // [Browser]
            case "clickxy":       // [Desktop]
            case "dobleclickxy":       // [Desktop]
            case "suprimir":       // [Desktop]
            case "write_text": // [Desktop]
            case "click_text":  // [Browser]
            case "set_variable":// [Browser]
            case "increment_variable":// [Browser]
            case "get_text":    // [Browser]        
            case "mkdir":    // [Browser]        
            case "copydir":    // [Browser]
            case "opentab":    // [Browser]
            case "key_enter":    // [Browser]

            //Las mismas funciones que los comandos de arriba,solo que estas son las mismas utilizadas para desktop
            case "createcon":       // [Desktop]
            case "start":           // [Desktop]
            case "taskill":         // [Desktop]
            case "tab":             // [Desktop]
            case "space":           // [Desktop]
            case "shifttab":        // [Desktop]
            case "right":           // [Desktop]
            case "down":            // [Desktop]
            case "up":              // [Desktop]
            case "left":            // [Desktop]
            case "enter":           // [Desktop]
            case "buscar":          // [Desktop]
            case "acctabdir":       // [Desktop]
            case "busquedadporcont":// [Desktop]
            case "formasociado":    // [Desktop]
            case "nuevo":           // [Desktop]
            case "borrado":         // [Desktop]
            case "ayuda":           // [Desktop]
            case "AvPag":           // [Desktop]
            case "RePag":           // [Desktop]
            case "insertline":      // [Desktop]
            case "copiar":          // [Desktop]
            case "cortar":          // [Desktop]
            case "pegar":           // [Desktop]
            case "guardar":         // [Desktop]
            case "cerrar":          // [Desktop]
            case "inicio":          // [Desktop]
            case "fin":             // [Desktop]
            case "crear_carpeta":   // [Desktop]
                out = nextYes;
                this.status = "OK";
                break;
            // Se valida properties con la salida del terminal
            case "contains":    // [CGUno, Browser, Desktop]
                Main.LOG.info("Empiezo contains, buscando entre: " + this.properties);
                if (this.properties.size() > 0) {
                    this.status = "FAIL";
                    out = nextNo;
                    for (String txtCompare : this.properties) {
                        // Ojo: No es CASE-SENTIVE, los comandos de CGUno validan sin importar mayus-minus
                        if (outTerminalText.toLowerCase().contains(txtCompare.toLowerCase())) {
                            Main.LOG.info("Encontrado: \"" + txtCompare + "\"");
                            this.status = "OK";
                            out = nextYes;
                            break;
                        }
                    }
                } else {
                    this.status = "FAIL";
                    out = nextNo;
                }

                break;

            // Se valida properties con la salida del terminal
            case "no_contains": // [CGUno, Browser, Desktop]
                Main.LOG.info("Empiezo no_contains, buscando entre: " + this.properties);
                this.status = "OK";
                out = nextYes;
                if (this.properties.size() > 0) {
                    for (String txtCompare : this.properties) {
                        if (outTerminalText.toLowerCase().contains(txtCompare.toLowerCase())) {
                            Main.LOG.info("Encontrado: \"" + txtCompare + "\"");
                            this.status = "FAIL";
                            out = nextNo;
                            break;
                        }
                    }
                }

                break;

            // Se valida properties para contar las lineas del archivo
            case "count_lines": // [CGUno]
                if (this.properties != null && this.properties.size() >= 2) {
                    Main.LOG.info("Empiezo count_lines, archivo: " + this.properties.get(0) + ", lineas: " + this.properties.get(1));
                    try {
                        int lines = Integer.parseInt(this.properties.get(1));
                        if (FileLineCounter.lessThan(this.properties.get(0), lines)) {
                            this.status = "OK";
                            out = nextYes;
                        } else {
                            this.status = "FAIL";
                            out = nextNo;
                        }
                    } catch (NumberFormatException e) {
                        Main.LOG.info("Cantidad de lineas no es un numero, no se valida correctamente.");
                        this.status = "FAIL";
                        out = nextNo;
                    }
                } else {
                    Main.LOG.warn("Comando tipo 'count_lines' no se ejecuta correctamente, propiedades insuficientes");
                    this.status = "FAIL";
                    out = nextNo;
                }
                break;

            case "validate_element":
            case "ddos":
            case "ddos_report_xlsx":
            case "ddos_report_docx":
            case "ddos_report_pdf":
            case "sqlinjection":
            case "sqlinjection_report_xlsx":
            case "sqlinjection_report_docx":
            case "sqlinjection_report_pdf":
            case "bruteforce":
            case "bruteforce_report_xlsx":
            case "bruteforce_report_docx":
            case "bruteforce_report_pdf":
                if (outTerminalText.equals("Ok")) {
                    this.status = "OK";
                    out = nextYes;
                    break;
                } else {
                    this.status = "FAIL";
                    out = nextNo;
                }

                break;

            case "searchtextimg":
                if (outTerminalText.equals("Si")) {
                    this.status = "OK";
                    out = nextYes;
                    break;
                } else {
                    this.status = "FAIL";
                    out = nextNo;
                }

                break;

            case "sendemail":
                if (outTerminalText.equals("S")) {
                    this.status = "OK";
                    out = nextYes;
                    break;
                } else {
                    this.status = "FAIL";
                    out = nextNo;
                }

                break;

            // Se transforman a numero las propiedades y el texto a comparar
            case "variable_greater_than_sizeof": // [Browser]
            case "variable_greater_than":        // [Browser]
                Main.LOG.info("Empiezo variable_greater_than..., comparando: '" + outTerminalText + "' y " + this.properties);
                this.status = "FAIL";
                out = nextNo;
                if (this.properties.size() > 0) {
                    try {
                        int val1 = Integer.parseInt(this.properties.get(0));    // Variable
                        int val2 = Integer.parseInt(outTerminalText);           // Cantidad de elementos encontrados
                        if (val1 > val2) {
                            this.status = "OK";
                            out = nextYes;
                        }
                    } catch (Exception e) {
                    }
                }

                break;

            // Se transforman a numero las propiedades y el texto a comparar
            case "variable_greater_equal_sizeof": // [Browser]
            case "variable_greater_equal":        // [Browser]
                Main.LOG.info("Empiezo variable_greater_equal..., comparando: '" + outTerminalText + "' y " + this.properties);
                this.status = "FAIL";
                out = nextNo;
                if (this.properties.size() > 0) {
                    try {
                        int val1 = Integer.parseInt(this.properties.get(0));    // Variable
                        int val2 = Integer.parseInt(outTerminalText);           // Cantidad de elementos encontrados
                        if (val1 >= val2) {
                            this.status = "OK";
                            out = nextYes;
                        }
                    } catch (Exception e) {
                    }
                }

                break;

            // Se transforman a numero las propiedades y el texto a comparar
            case "variable_lower_than_sizeof": // [Browser]
            case "variable_lower_than":        // [Browser]
                Main.LOG.info("Empiezo variable_greater_than..., comparando: '" + outTerminalText + "' y " + this.properties);
                this.status = "FAIL";
                out = nextNo;
                if (this.properties.size() > 0) {
                    try {
                        int val1 = Integer.parseInt(this.properties.get(0));    // Variable
                        int val2 = Integer.parseInt(outTerminalText);           // Cantidad de elementos encontrados
                        if (val1 < val2) {
                            this.status = "OK";
                            out = nextYes;
                        }
                    } catch (Exception e) {
                    }
                }

                break;

            // Se transforman a numero las propiedades y el texto a comparar
            case "variable_lower_equal_sizeof": // [Browser]
            case "variable_lower_equal":        // [Browser]
                Main.LOG.info("Empiezo variable_lower_equal..., comparando: '" + outTerminalText + "' y " + this.properties);
                this.status = "FAIL";
                out = nextNo;
                if (this.properties.size() > 0) {
                    try {
                        int val1 = Integer.parseInt(this.properties.get(0));    // Variable
                        int val2 = Integer.parseInt(outTerminalText);           // Cantidad de elementos encontrados
                        if (val1 <= val2) {
                            this.status = "OK";
                            out = nextYes;
                        }
                    } catch (Exception e) {
                    }
                }

                break;
            default:
                this.status = "FAIL";
                out = "END";
                break;
        }

        return out;
    }

    /*
        -------------- GETTERS & SETTERS --------------
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getProperties() {
        return properties;
    }

    public void setProperties(List<String> properties) {
        this.properties = properties;
    }

    public String getNextYes() {
        return nextYes;
    }

    public void setNextYes(String nextYes) {
        this.nextYes = nextYes;
    }

    public String getNextNo() {
        return nextNo;
    }

    public void setNextNo(String nextNo) {
        this.nextNo = nextNo;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimeSleep() {
        return timeSleep;
    }

    public void setTimeSleep(String timeSleep) {
        this.timeSleep = timeSleep;
    }
}
