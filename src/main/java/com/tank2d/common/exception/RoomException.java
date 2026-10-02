package com.tank2d.common.exception;

/**
 * Ngoại lệ liên quan đến quản lý phòng và sảnh chờ (Tạo phòng, Vào phòng, Rời phòng, Ready, Start).
 */
public class RoomException extends GameNetworkException {

    public RoomException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RoomException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RoomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public RoomException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
