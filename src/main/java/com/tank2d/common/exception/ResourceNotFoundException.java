package com.tank2d.common.exception;

/**
 * Ngoại lệ khi không tìm thấy tài nguyên hệ thống (Hình ảnh assets, Âm thanh WAV).
 */
public class ResourceNotFoundException extends GameNetworkException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
