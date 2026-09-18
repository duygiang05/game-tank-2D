package com.tank2d.common.dto;

import java.util.List;
import java.util.Map;

public class RoomDTO {

    private int roomId;
    private String roomName;
    private int currentPlayers;
    private int maxPlayers;
    private String status;

    // Danh sách tên người chơi
    private List<String> playerNames;

    // ID của Host
    private int hostId;

    // Trạng thái Ready của từng người chơi
    // userId -> true/false
    private Map<Integer, Boolean> readyStates;

    // Thời lượng trận đấu (đơn vị: giây)
    // Giá trị hợp lệ: 45, 60, 90
    private int duration;

    public RoomDTO() {
    }

    public RoomDTO(
            int roomId,
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

    // =========================
    // ROOM ID
    // =========================

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    // =========================
    // ROOM NAME
    // =========================

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    // =========================
    // CURRENT PLAYERS
    // =========================

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public void setCurrentPlayers(int currentPlayers) {
        this.currentPlayers = currentPlayers;
    }

    // =========================
    // MAX PLAYERS
    // =========================

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    // =========================
    // STATUS
    // =========================

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // =========================
    // PLAYER NAMES
    // =========================

    public List<String> getPlayerNames() {
        return playerNames;
    }

    public void setPlayerNames(
            List<String> playerNames) {

        this.playerNames = playerNames;
    }

    // =========================
    // HOST
    // =========================

    public int getHostId() {
        return hostId;
    }

    public void setHostId(int hostId) {
        this.hostId = hostId;
    }

    // =========================
    // READY STATES
    // =========================

    public Map<Integer, Boolean> getReadyStates() {
        return readyStates;
    }

    public void setReadyStates(
            Map<Integer, Boolean> readyStates) {

        this.readyStates = readyStates;
    }

    // =========================
    // MATCH DURATION
    // =========================

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}