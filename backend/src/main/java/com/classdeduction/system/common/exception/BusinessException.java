package com.classdeduction.system.common.exception;

/**
 * Business exception used for expected, display-safe error messages.
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        this("BIZ_ERROR", message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
