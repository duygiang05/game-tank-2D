package com.tank2d.common.dto;

import java.util.List;
import java.util.Map;

/**
 * Đối tượng truyền dữ liệu (DTO) biểu diễn thông tin trạng thái của một phòng chơi.
 */
public class RoomDTO {

    private int roomId;
    private String roomName;
    private int currentPlayers;
    private int maxPlayers;
    private String status;
    private List<String> playerNames;
    private int hostId;
    private Map<Integer, Boolean> readyStates;
    private int duration;

    public RoomDTO() {}

    public RoomDTO(int roomId,
                   String roomName,
                   int currentPlayers,
                   int maxPlayers,
                   String status,
                   List<String> playerNames,
                   int hostId,
                   Map<Integer, Boolean> readyStates,
                   int duration) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
        this.playerNames = playerNames;
        this.hostId = hostId;
        this.readyStates = readyStates;
        this.duration = duration;
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

    public List<String> getPlayerNames() {
        return playerNames;
    }

    public void setPlayerNames(List<String> playerNames) {
        this.playerNames = playerNames;
    }

    public int getHostId() {
        return hostId;
    }

    public void setHostId(int hostId) {
        this.hostId = hostId;
    }

    public Map<Integer, Boolean> getReadyStates() {
        return readyStates;
    }

    public void setReadyStates(Map<Integer, Boolean> readyStates) {
        this.readyStates = readyStates;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}