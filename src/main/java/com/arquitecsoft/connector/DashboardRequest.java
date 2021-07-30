package com.arquitecsoft.connector;

import com.arquitecsoft.controller.Main;
import com.arquitecsoft.model.DashboardTokenModel;
import com.google.gson.Gson;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.List;

public class DashboardRequest {

    private String host;
    private String username;
    private String password;
    private String tokenValidation;

    public DashboardRequest(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.tokenValidation = "";
    }

    /**
     * Obtiene el token de login del dashboard
     */
    public void updateDashBoardToken() {
        try {
            Gson gson = new Gson();
            String destination = this.host + "/api/login";
            Main.LOG.debug("Endpoint destino: " + destination);

            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(destination);

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("username", this.username));
            params.add(new BasicNameValuePair("password", this.password));
            httpPost.setEntity(new UrlEncodedFormEntity(params));

            Main.LOG.debug("Login con username: " + this.username);
            DashboardTokenModel token = null;
            CloseableHttpResponse response = client.execute(httpPost);
            String result = "";
            try {
                result = EntityUtils.toString(response.getEntity());
                token = gson.fromJson(result, DashboardTokenModel.class);
            } catch (Exception ex) {
                Main.LOG.error("No se puede convertir la salida: " + ex.getMessage());
            } finally {
                response.close();
            }

            client.close();

            this.tokenValidation = token.getSuccess().getToken();
            Main.LOG.debug("Token obtenido: " + this.tokenValidation);
            return;

        } catch (Exception e) {
            Main.LOG.error("No se puede enviar la peticion al servidor " + this.host);
            e.printStackTrace();
        }

        // Si algo sale mal, el token se envia vacio
        this.tokenValidation = "";
    }

    /**
     * Enviar datos al dashboard Realizar loggin, tomar el token y enviar los
     * archivos descargados al dashboard
     *
     * @param serviceUri Servicio, URI que se agrega luego del HOTST
     * @param jsonParams Parametros, String en formato JSON con los parametros
     */
    public String sendData(String serviceUri, String jsonParams) {
        try {
            // TODO: 25/07/2019 Cuantas veces se hara intento de obtener token valido
            if (!this.testToken(5)) {
                Main.LOG.error("No es posible enviar la peticion al servidor " + this.host);
                return "";
            }
            Gson gson = new Gson();
            String destination = this.host + serviceUri;
            Main.LOG.debug("Endpoint destino: " + destination);

            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(destination);

            httpPost.addHeader("Authorization", "Bearer " + this.tokenValidation);
            httpPost.addHeader("content-type", "application/json");
            Main.LOG.info("Parametros a enviar: " + jsonParams);
            //Convertimos el json en encode utf-8 para soportar caracteres JValencia  
            StringEntity requestEntity = new StringEntity(jsonParams, "UTF-8");
            httpPost.setEntity(requestEntity);
            CloseableHttpResponse response = client.execute(httpPost);
            String result = "";
            try {
                result = EntityUtils.toString(response.getEntity());
            } catch (Exception ex) {
                Main.LOG.error("No se puede convertir la salida: " + ex.getMessage());
            } finally {
                response.close();
            }

            client.close();

            Main.LOG.info("Respuesta server: " + result);

            return result;

        } catch (Exception e) {
            Main.LOG.error("No es posible enviar la peticion al servidor " + this.host);
            e.printStackTrace();
        }

        return "";
    }

    /**
     * Verifica si el token es valido de lo contrario solicita un nuevo token
     *
     * @return
     */
    private boolean testToken(int tries) {
        boolean out = false;
        if (tries <= 0) {
            return false;
        }

        if (this.tokenValidation == null || this.tokenValidation.equals("")) {
            this.updateDashBoardToken();
        }

        String responseString = "";
        try {

            Gson gson = new Gson();
            String destination = this.host + "/api/details";
            Main.LOG.debug("Endpoint destino: " + destination);

            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(destination);

            Main.LOG.debug("Token a enviar: " + this.tokenValidation);
            httpPost.addHeader("Authorization", "Bearer " + this.tokenValidation);
            httpPost.addHeader("content-type", "application/json");

            CloseableHttpResponse response = client.execute(httpPost);
            try {
                responseString = EntityUtils.toString(response.getEntity());
            } catch (Exception ex) {
//                Main.LOG.error("No se puede convertir la salida: " + ex.getMessage());
            } finally {
                response.close();
            }

            client.close();

        } catch (Exception e) {
            Main.LOG.error("No es posible enviar la peticion al servidor " + this.host);
        }
        if (responseString.length() > 400) {
            Main.LOG.debug("response: " + responseString.substring(0, 400));
        } else {
            Main.LOG.debug("Response: " + responseString);
        }

        try {
            Gson gson = new Gson();
            DashboardTokenModel response = gson.fromJson(responseString, DashboardTokenModel.class);
            Main.LOG.debug("Encontrado ID: " + response.getSuccess().getId());
            out = response.getSuccess().getId() > 0;
        } catch (Exception ex) {
            Main.LOG.error("Token invalido -" + tries);
//            Main.LOG.error("No se puede convertir la salida: " + ex.getMessage());
        }

        // Si hay error con el token se debe solicitar un nuevo token
        if (!out) {
            this.tokenValidation = null;
            return this.testToken(tries - 1);
        }

        return out;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
