package com.tank2d.common.dto.game;

public class ReconnectPromptDTO {
    private int roomId;
    private String roomName;
    private double remainingTime;

    public ReconnectPromptDTO() {}

    public ReconnectPromptDTO(int roomId, String roomName, double remainingTime) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.remainingTime = remainingTime;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public double getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(double remainingTime) {
        this.remainingTime = remainingTime;
    }
}
