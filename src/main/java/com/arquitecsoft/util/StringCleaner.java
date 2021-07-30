package com.arquitecsoft.util;

/**
 * Clase cuyo objetivo es limpiar caracteres especiales o
 * transformaciones en strings
 * @author YMondragon
 */
public class StringCleaner {

    public static String[] listado = {
            String.valueOf((char) 27), // Tecla ESC
            String.valueOf((char) 15), // Tecla SI
            "[K",
            "[B",
            "[m",
            "[H",
            "[J",
            "[D",
            ")0"

    };

    /**
     * Toma el texto de entrada y remueve caracteres especiales y secuencias
     * asginadas en el "listado" definido anteriormente
     * @param text
     * @return
     */
    public static String cleanSistemaUnoOutput(String text){
        for (String r : listado) {
            text = text.replace(r, "");
        }


        for (int i = 0; i < 100; i++) {
            String r = "[" + i + "m";
            text = text.replace(r, "");

            r = "[" + i + ";7H";
            text = text.replace(r, " ");

            r = "[" + i + ";13H";
            text = text.replace(r, " ");

            r = "[" + i + ";21H";
            text = text.replace(r, " ");

            r = "[" + i + ";73H";
            text = text.replace(r, " ");
        }

        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 100; j++) {
                String r = "[" + i + ";" + j + "H";
                text = text.replace(r, "\n");

                r = "[" + i + ";" + j + "r";
                text = text.replace(r, "");

                r = "[" + i + ";" + j + "m";
                text = text.replace(r, "");
            }
        }

        text = text.replace("[C", " ");

        return text;
    }
}
