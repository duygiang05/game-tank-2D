package com.tank2d.server.room;

import com.tank2d.common.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Room {

    private final int roomId;
    private final String roomName;
    private final int maxPlayers;
    private final int hostId;

    private final List<User> players;
    private final Map<Integer, Boolean> readyStates;

    private String status;
    private double matchDuration = 60.0; // Mặc định trận 60s

    public Room(int roomId, String roomName, int hostId) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.hostId = hostId;
        this.maxPlayers = 4; // Cấu hình phòng tối đa 4 slot cho Sprint 3
        this.players = new ArrayList<>();
        this.readyStates = new LinkedHashMap<>();
        this.status = "Waiting";
    }

    public int getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getHostId() {
        return hostId;
    }

    public synchronized boolean isHost(int userId) {
        return hostId == userId;
    }

    public synchronized List<User> getPlayers() {
        return new ArrayList<>(players);
    }

    public synchronized Map<Integer, Boolean> getReadyStates() {
        return new LinkedHashMap<>(readyStates);
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized double getMatchDuration() {
        return matchDuration;
    }

    public synchronized void setMatchDuration(double matchDuration) {
        this.matchDuration = matchDuration;
    }

    public synchronized boolean addPlayer(User user) {
        if (user == null) return false;

        for (User player : players) {
            if (player.getId() == user.getId()) return false;
        }

        if (players.size() >= maxPlayers) return false;

        players.add(user);

        if (user.getId() == hostId) {
            readyStates.put(user.getId(), true);
        } else {
            readyStates.put(user.getId(), false);
        }

        updateStatus();
        return true;
    }

    public synchronized boolean removePlayer(int userId) {
        boolean removed = players.removeIf(user -> user.getId() == userId);
        if (removed) {
            readyStates.remove(userId);
            updateStatus();
        }
        return removed;
    }

    public synchronized boolean setReady(int userId, boolean ready) {
        if (!readyStates.containsKey(userId)) return false;

        if (userId == hostId) {
            readyStates.put(userId, true);
        } else {
            readyStates.put(userId, ready);
        }
        return true;
    }

    public synchronized boolean isReady(int userId) {
        return readyStates.getOrDefault(userId, false);
    }

    public synchronized boolean areAllPlayersReady() {
        if (players.isEmpty()) return false;
        return readyStates.values().stream().allMatch(Boolean::booleanValue);
    }

    public synchronized int getCurrentPlayers() {
        return players.size();
    }

    private void updateStatus() {
        if (players.size() >= maxPlayers) {
            status = "Full";
        } else {
            status = "Waiting";
        }
    }
}