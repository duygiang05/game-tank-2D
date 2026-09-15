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

    // ID của người tạo phòng
    private final int hostId;

    private final List<User> players;
    private final Map<Integer, Boolean> readyStates;

    private String status;

    public Room(int roomId, String roomName, int hostId) {

        this.roomId = roomId;
        this.roomName = roomName;
        this.hostId = hostId;

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

    // =========================
    // HOST
    // =========================

    public int getHostId() {
        return hostId;
    }

    public synchronized boolean isHost(int userId) {
        return hostId == userId;
    }

    // =========================
    // PLAYERS
    // =========================

    public synchronized List<User> getPlayers() {
        return new ArrayList<>(players);
    }

    // =========================
    // READY STATES
    // =========================

    public synchronized Map<Integer, Boolean> getReadyStates() {
        return new LinkedHashMap<>(readyStates);
    }

    public synchronized String getStatus() {
        return status;
    }

    // =========================
    // ADD PLAYER
    // =========================

    public synchronized boolean addPlayer(User user) {

        if (user == null) {
            return false;
        }

        // Không cho một user vào cùng một phòng 2 lần
        for (User player : players) {

            if (player.getId() == user.getId()) {
                return false;
            }
        }

        if (players.size() >= maxPlayers) {
            return false;
        }

        players.add(user);

        /*
         * Host luôn được xem là Ready.
         * Người chơi thường ban đầu chưa Ready.
         */
        if (user.getId() == hostId) {
            readyStates.put(user.getId(), true);
        } else {
            readyStates.put(user.getId(), false);
        }

        updateStatus();

        return true;
    }

    // =========================
    // REMOVE PLAYER
    // =========================

    public synchronized boolean removePlayer(int userId) {

        boolean removed = players.removeIf(
                user -> user.getId() == userId
        );

        if (removed) {

            readyStates.remove(userId);

            updateStatus();
        }

        return removed;
    }

    // =========================
    // SET READY
    // =========================

    public synchronized boolean setReady(
            int userId,
            boolean ready) {

        if (!readyStates.containsKey(userId)) {
            return false;
        }

        /*
         * Host luôn Ready.
         * Không cho Host chuyển về Not Ready.
         */
        if (userId == hostId) {
            readyStates.put(userId, true);
        } else {
            readyStates.put(userId, ready);
        }

        return true;
    }

    // =========================
    // CHECK READY
    // =========================

    public synchronized boolean isReady(int userId) {

        return readyStates.getOrDefault(
                userId,
                false
        );
    }

    public synchronized boolean areAllPlayersReady() {

        if (players.isEmpty()) {
            return false;
        }

        return readyStates.values()
                .stream()
                .allMatch(Boolean::booleanValue);
    }

    // =========================
    // PLAYERS COUNT
    // =========================

    public synchronized int getCurrentPlayers() {

        return players.size();
    }

    // =========================
    // ROOM STATUS
    // =========================

    private void updateStatus() {

        if (players.size() >= maxPlayers) {

            status = "Full";

        } else {

            status = "Waiting";
        }
    }
}