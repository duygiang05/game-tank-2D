package com.tank2d.common.exception;

/**
 * Ngoại lệ liên quan đến xác thực người dùng (Đăng nhập, Đăng ký, Đăng xuất, Phân quyền).
 */
public class AuthException extends GameNetworkException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AuthException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public AuthException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
