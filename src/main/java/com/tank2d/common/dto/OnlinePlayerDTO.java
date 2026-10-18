package com.tank2d.common.dto;

/**
 * DTO biểu diễn thông tin người chơi đang trực tuyến (Online Player).
 */
public class OnlinePlayerDTO {
    private int userId;
    private String username;
    private String status; // "LOBBY", "IN_ROOM", "IN_GAME"
    private int roomId;

    public OnlinePlayerDTO() {}

    public OnlinePlayerDTO(int userId, String username, String status, int roomId) {
        this.userId = userId;
        this.username = username;
        this.status = status;
        this.roomId = roomId;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }
}
