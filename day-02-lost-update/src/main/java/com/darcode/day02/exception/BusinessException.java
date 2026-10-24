package com.darcode.day02.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static BusinessException accountNotFound(long accountId) {
        return new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Account not found : " + accountId);
    }
}
