package com.tank2d.common.exception;

/**
 * Ngoại lệ liên quan đến HikariCP Connection Pool và truy vấn SQL MySQL.
 */
public class DatabaseException extends GameNetworkException {

    public DatabaseException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DatabaseException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DatabaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public DatabaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
