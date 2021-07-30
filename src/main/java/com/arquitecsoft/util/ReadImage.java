
package com.arquitecsoft.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 *
 * @author JValencia
 */
public class ReadImage {
        public String readImage(String word, int x, int y, int width, int height) {
        ReadImage app = new ReadImage();
        String text = "";
        String palabra = word;
        int i = 0;
        try {

            Robot robot = new Robot();
            String format = "png";
            String fileName = "capture00." + format;
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle captureRect = new Rectangle(x, y, width, height); // taskbar

            BufferedImage screenFullImage = robot.createScreenCapture(captureRect);
            ImageIO.write(screenFullImage, format, new File(fileName));
            text = app.getImgText("capture00.png");
            System.out.println("Se encontró la palabra "+text);
            if (text.toLowerCase().contains(palabra.toLowerCase())) {
                return "Si";
            } else {
                invertImage("capture00.png");
                text = app.getImgText("invert-capture00.png");
                System.out.println("En la imagen inversa se encontró la palabra "+text);
                if (text.toLowerCase().contains(palabra.toLowerCase())) {
                    return "Si";
                }else{
                    return "No";
                }
            }
        } catch (AWTException | IOException e) {
            e.printStackTrace();
            return "No";
        }

    }
 
    public String getImgText(String imageLocation) {
        ITesseract instance = new Tesseract();
        Rectangle rect = new Rectangle(-50, 900); // define an equal or smaller region of interest on the image

        try {
            String imgText = instance.doOCR(new File(imageLocation));
           return imgText;
        } catch (TesseractException e) {
            e.getMessage();
            return "Errorwhilereadingimage";
        }
    }

    public static void invertImage(String imageName) {
        BufferedImage inputFile = null;
        try {
            inputFile = ImageIO.read(new File(imageName));
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int x = 0; x < inputFile.getWidth(); x++) {
            for (int y = 0; y < inputFile.getHeight(); y++) {
                int rgba = inputFile.getRGB(x, y);
                Color col = new Color(rgba, true);
                col = new Color(255 - col.getRed(),
                        255 - col.getGreen(),
                        255 - col.getBlue());
                inputFile.setRGB(x, y, col.getRGB());
            }
        }

        try {
            File outputFile = new File("invert-"+imageName);
            ImageIO.write(inputFile, "png", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
