package com.tank2d.common.exception;

/**
 * Ngoại lệ truyền nhận qua mạng, kết nối Socket và đọc/ghi Packet TCP.
 */
public class NetworkException extends GameNetworkException {

    public NetworkException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NetworkException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public NetworkException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public NetworkException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
