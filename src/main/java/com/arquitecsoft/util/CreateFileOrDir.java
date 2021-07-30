package com.arquitecsoft.util;

import java.io.File;
import java.io.IOException;

/**
 * Clase para generar ficheros o directorios dentro del equipo
 *
 * @author JValencia
 */
public class CreateFileOrDir {

    public void filecreate(String fichero) throws IOException {
        String[] divCad = fichero.split("\\\\");
        int contador = divCad.length;
        String validador = ".";
        String godDiv;
        String saveVar = "";

        for (int i = 0; i < contador; i++) {
            if (i == contador - 1) {
                godDiv = divCad[i];
            } else {
                godDiv = divCad[i] + "\\";
            }
            saveVar = saveVar + godDiv;

            if (i == contador - 1) {
                File file = new File(saveVar);
                if (divCad[i].contains(validador)) {
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                } else {
                    if (!file.exists()) {
                        file.mkdir();
                    }

                }
            } else {
                File file = new File(saveVar);
                if (!file.exists()) {
                    file.mkdir();
                }
            }

        }

    }

    public void onlinecreatedir(String fichero) throws IOException {
        System.out.println(fichero);
        String[] divCad = fichero.split("\\\\");
        int contador = divCad.length;
        String godDiv;
        String saveVar = "";

        for (int i = 0; i < contador; i++) {
            if (i == contador - 1) {
                godDiv = divCad[i];
            } else {
                godDiv = divCad[i] + "\\";
            }
            saveVar = saveVar + godDiv;

            if (i == contador - 1) {
                File file = new File(saveVar);
                if (!file.exists()) {
                    file.mkdir();
                }
                
            }
            
        }
        
    }
    
}
