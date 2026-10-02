package com.tank2d.common.exception;

/**
 * Ngoại lệ liên quan đến nạp file cấu hình hệ thống (.env, stats.json, game_rules.yml, map.json).
 */
public class ConfigException extends GameNetworkException {

    public ConfigException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConfigException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ConfigException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
