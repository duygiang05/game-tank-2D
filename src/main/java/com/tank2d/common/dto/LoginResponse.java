package com.tank2d.common.dto;

public class LoginResponse {
    private boolean success;
    private int userId;
    private String username;
    private String message;

    public LoginResponse() {
    }

    public LoginResponse(boolean success, int userId, String username, String message) {
        this.success = success;
        this.userId = userId;
        this.username = username;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}