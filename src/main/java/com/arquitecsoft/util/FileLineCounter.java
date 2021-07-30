package com.arquitecsoft.util;

import com.arquitecsoft.controller.Main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Clase para contar lineas en un archivo
 */
public class FileLineCounter {
    public FileLineCounter() {
    }

    /**
     * Cuenta las lineas del archivo indicado,
     * si tiene mas lineas que las del parametro retorna falso;
     * si no encuentra el archivo, retorna falso;
     * si no puede leer el archivo, retorna falso;
     * si la cantidad de lineas del archivo es menor al especificado, retorna verdadero
     * @param fileLocation Ubicacion en disco del archivo
     * @param lineMax Cantidad de lineas a validar
     * @return fileLocation.cantidad_lineas < lineMax
     */
    public static boolean lessThan(String fileLocation, int lineMax){
        File file = new File(fileLocation);

        try {
            if (file.exists()){
                BufferedReader br = new BufferedReader(new FileReader(file));

                String st;
                int pos = 1;
                while ((st = br.readLine()) != null) {
//                    System.out.println(st);
                    // Si se llega a una cantidad de lineas mayor a la del maximo permitido, retornar falso
                    if(pos >= lineMax) {
                        return false;
                    }

                    pos++;

                }
                return true;
            }
        }catch (IOException e) {
            e.printStackTrace();
        }

        Main.LOG.error("El archivo " + fileLocation + " no existe o no se puede leer.");
        return false;
    }
}
