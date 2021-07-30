package com.arquitecsoft.model;

public class ResponseModel {
    public final static String ERROR_CODE = "error";
    public final static String ERROR_PARAMS_CODE = "error_params";
    public final static String SUCCESS_CODE = "success";

    private String errorCode;
    private String errorDesc;

    public ResponseModel() {
        errorCode = "";
        errorDesc = "";
    }

    public ResponseModel(String type) {
        if(type == null){
            errorCode = "0.001";
            errorDesc = "Not valid run";
        }else if(type.equals("error")){
            errorCode = "0.01";
            errorDesc = "Not valid authorization status";
        }else if(type.equals("error_params")){
            errorCode = "0.02";
            errorDesc = "Not valid parameters";
        }else if(type.equals("success")){
            errorCode = "200";
            errorDesc = "Run OK";
        }else{
            errorCode = "0.00";
            errorDesc = "Default response";
        }
    }

    public ResponseModel(String errorCode, String errorDesc) {
        this.errorCode = errorCode;
        this.errorDesc = errorDesc;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDesc() {
        return errorDesc;
    }

    public void setErrorDesc(String errorDesc) {
        this.errorDesc = errorDesc;
    }
}
