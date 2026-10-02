package com.tank2d.common.exception;

/**
 * Base Exception cha cho toàn bộ hệ thống Tank2D Online.
 * Tất cả các Exception chuyên biệt đều kế thừa từ class này.
 */
public class GameNetworkException extends RuntimeException {

    private final ErrorCode errorCode;

    public GameNetworkException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public GameNetworkException(ErrorCode errorCode, String message) {
        super(message != null ? message : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public GameNetworkException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public GameNetworkException(ErrorCode errorCode, String message, Throwable cause) {
        super(message != null ? message : errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
