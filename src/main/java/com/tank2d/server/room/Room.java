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

    private final List<User> players;
    private final Map<Integer, Boolean> readyStates;

    private String status;

    public Room(int roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.maxPlayers = 2;

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

    public List<User> getPlayers() {
        return new ArrayList<>(players);
    }

    public Map<Integer, Boolean> getReadyStates() {
        return new LinkedHashMap<>(readyStates);
    }

    public String getStatus() {
        return status;
    }

    public boolean addPlayer(User user) {
        if (user == null) {
            return false;
        }

        if (players.size() >= maxPlayers) {
            return false;
        }

        players.add(user);
        readyStates.put(user.getId(), false);

        updateStatus();

        return true;
    }

    public boolean removePlayer(int userId) {
        boolean removed = players.removeIf(
                user -> user.getId() == userId
        );

        if (removed) {
            readyStates.remove(userId);
            updateStatus();
        }

        return removed;
    }

    public boolean setReady(int userId, boolean ready) {
        if (!readyStates.containsKey(userId)) {
            return false;
        }

        readyStates.put(userId, ready);
        return true;
    }

    public boolean isReady(int userId) {
        return readyStates.getOrDefault(userId, false);
    }

    public boolean areAllPlayersReady() {
        if (players.isEmpty()) {
            return false;
        }

        return readyStates.values()
                .stream()
                .allMatch(Boolean::booleanValue);
    }

    public int getCurrentPlayers() {
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
