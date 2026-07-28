package com.classdeduction.system.common.exception;

/**
 * Business exception used for expected, display-safe error messages.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final Object data;

    public BusinessException(String message) {
        this("BIZ_ERROR", message);
    }

    public BusinessException(String code, String message) {
        this(code, message, null);
    }

    public BusinessException(String code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
