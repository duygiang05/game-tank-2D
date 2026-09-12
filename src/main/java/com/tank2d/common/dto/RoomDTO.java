package com.tank2d.common.dto;

public class RoomDTO {

    private int roomId;
    private String roomName;
    private int currentPlayers;
    private int maxPlayers;
    private String status;

    public RoomDTO() {
    }

    public RoomDTO(int roomId, String roomName,
                   int currentPlayers, int maxPlayers,
                   String status) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
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

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public void setCurrentPlayers(int currentPlayers) {
        this.currentPlayers = currentPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}