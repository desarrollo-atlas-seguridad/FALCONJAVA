package com.arquitecsoft.util;

import com.arquitecsoft.controller.Main;

import javax.naming.AuthenticationException;

public class TokenValidator {
    /**
     * Se usa para comparar y realizar diferentes validaciones entre
     * el token recibido y el token de env
     * @param token Token recibido por HEADER
     * @return TRUE, si coinciden ambos tokens
     * @author YMondragon
     */
    public static boolean Validate(String token)  {
        if(token != null && token.equals(Main.APP_TOKEN)){
            return true;
        }

        return false;
        //throw new AuthenticationException("Not valid authorization status");
    }
}
