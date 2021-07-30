package com.arquitecsoft.util;

import com.arquitecsoft.controller.Main;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

/**
 *
 * @author JValencia
 */
public class SendMail {

    private static void addAttachment(Multipart multipart, String filename, OutputStream outputStream) throws MessagingException, IOException {

        ByteArrayOutputStream bos = (ByteArrayOutputStream) outputStream;
        byte[] bytes = bos.toByteArray();
        BodyPart messageBodyPart = new MimeBodyPart();
        DataSource dataSource = new ByteArrayDataSource(bytes, "application/zip");
        messageBodyPart.setDataHandler(new DataHandler(dataSource));
        messageBodyPart.setFileName(filename);
        multipart.addBodyPart(messageBodyPart);

    }

    public static String send(String servidor,
            String puerto,
            String usuario,
            String pass,
            String asunto,
            String texto,
            String para,
            String cc,
            OutputStream outputStream) throws MessagingException, IOException {

        String salida = "";
        StringWriter errors = new StringWriter();

        Properties props = System.getProperties();
        //props.put("mail.smtp.starttls.enable", true); // added this line
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", servidor);
        props.put("mail.smtp.user", usuario);
        props.put("mail.smtp.password", pass);
        props.put("mail.smtp.port", puerto);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.trust", servidor);

        Session session = Session.getInstance(props, null);
        MimeMessage message = new MimeMessage(session);

        Main.LOG.info("Port: " + session.getProperty(servidor));

        // Create the email addresses involved
        try {
            InternetAddress from = new InternetAddress(usuario);
            message.setSubject(asunto);
            message.setFrom(from);
            message.addRecipients(Message.RecipientType.TO, InternetAddress.parse(para));

            if (cc != null) {
                if (cc.contains(",")) {
                    String[] destinatariosCC = cc.split(",");

                    for (int i = 0; i < destinatariosCC.length; i++) {

                        Main.LOG.info("Array -> " + destinatariosCC[i]);
                        message.addRecipients(Message.RecipientType.CC,
                                InternetAddress.parse(destinatariosCC[i]));
                    }
                } else {
                    message.addRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
                }

            }
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent(texto, "text/html; charset=utf-8");
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);

            // adjuntos
            messageBodyPart = new MimeBodyPart();

            if (outputStream != null) {
                String filename = "adjuntos.zip";
                addAttachment(multipart, filename, outputStream);
            }

            message.setContent(multipart);
            Transport transport = session.getTransport("smtp");
            transport.connect(servidor, usuario, pass);
            Main.LOG.info("Transport: " + transport.toString());
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();

            return "0|correo enviado con exito";
        } catch (AddressException e) {
            // TODO Auto-generated catch block
            e.printStackTrace(new PrintWriter(errors));

            salida = "Correo: " + para + " Mensaje: " + texto + " Mensaje tecnico: " + e.getMessage() + " - " + errors.toString();

            return "-1| Error AddressException: " + salida;

        }

    }
}
