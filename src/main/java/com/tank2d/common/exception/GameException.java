package com.tank2d.common.exception;

/**
 * Ngoại lệ liên quan đến logic gameplay, thực thể xe tank và vòng lặp game (GameLoop).
 */
public class GameException extends GameNetworkException {

    public GameException(ErrorCode errorCode) {
        super(errorCode);
    }

    public GameException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public GameException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public GameException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
