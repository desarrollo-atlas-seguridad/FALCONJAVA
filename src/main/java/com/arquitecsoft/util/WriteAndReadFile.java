package com.arquitecsoft.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sobre escribrir en un fichero
 *
 * @author JValencia
 */
public class WriteAndReadFile {

    public String Read(String archivo) throws Exception {
        String cadena;
        String almacenar = "";
        File file = new File(archivo);
        if (file.exists()) {
            FileReader f = new FileReader(archivo);
            BufferedReader b = new BufferedReader(f);
            while ((cadena = b.readLine()) != null) {
                almacenar = almacenar + cadena;
            }
            b.close();
        } else {
            throw new Exception("NOT FOUND/FALSE/ERROR");
        }
        return almacenar;

    }

    /**
     * Lee un archivo y guarda cada linea en una posicion de la variable de salida
     * Nota: Sólo guarda aquellas líneas que no son espacios en blanco
     * @param archivo ruta absoluta del archivo
     * @return Lista de cada linea
     * @throws Exception Archivo no encontrado en la ubicacion dada
     */
    public List<String> ReadLine(String archivo) throws Exception {
        String cadena;
        List<String> almacenar = new ArrayList<>();
        File file = new File(archivo);
        if (file.exists()) {
            FileReader f = new FileReader(archivo);
            BufferedReader b = new BufferedReader(f);
            while ((cadena = b.readLine()) != null) {
                if (!"".equals(cadena.trim())) {
                    almacenar.add(cadena);
                }
            }
            b.close();
        } else {
            throw new Exception(archivo + ", NOT FOUND/FALSE/ERROR");
        }
        return almacenar;

    }

    public void Write(String archivo, String escrito) throws IOException {
        File file = new File(archivo);
        if (!file.exists()) {
            CreateFileOrDir c = new CreateFileOrDir();
            c.filecreate(archivo);
        }
        BufferedWriter bw;
        bw = new BufferedWriter(new FileWriter(file));
        bw.write(escrito);
        bw.close();
    }

}
