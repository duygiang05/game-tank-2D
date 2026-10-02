package com.tank2d.common.dto;

/**
 * DTO phản hồi lỗi chuẩn hóa gửi từ Server về Client.
 * Chứa mã lỗi, thông điệp hiển thị và thời gian hiển thị (mặc định 5s = 5000ms).
 */
public class ErrorResponseDTO {

    private int code;
    private String message;
    private long durationMs;

    public ErrorResponseDTO() {
        this.durationMs = 5000L;
    }

    public ErrorResponseDTO(int code, String message) {
        this(code, message, 5000L);
    }

    public ErrorResponseDTO(int code, String message, long durationMs) {
        this.code = code;
        this.message = message;
        this.durationMs = durationMs;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
