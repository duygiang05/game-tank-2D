package com.tank2d.server.room;

import com.tank2d.common.model.User;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.game.GameStateManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class Room {

    private static final Logger LOGGER = Logger.getLogger(Room.class.getName());

    private final int roomId;
    private final String roomName;
    private final int maxPlayers;
    private int hostId;

    // Thời lượng trận đấu (đơn vị: giây - mặc định: 60)
    private int duration = 60;

    private final List<User> players;
    private final Map<Integer, Boolean> readyStates;

    // Trạng thái: "Waiting", "Full", "Playing"
    private String status;

    private GameLoop gameLoop;
    private GameStateManager gameStateManager;
    private final Map<Integer, Integer> userTankMappings = new ConcurrentHashMap<>();

    public Room(int roomId, String roomName, int hostId) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.hostId = hostId;
        this.maxPlayers = 4;
        this.players = new ArrayList<>();
        this.readyStates = new LinkedHashMap<>();
        this.status = "Waiting";
    }

    // =========================
    // ROOM INFORMATION
    // =========================
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

    // =========================
    // STATUS (GETTER & SETTER)
    // =========================
    public synchronized String getStatus() {
        return status;
    }

    public synchronized void setStatus(String status) {
        this.status = status;
    }

    // =========================
    // PLAYERS
    // =========================
    public synchronized List<User> getPlayers() {
        return new ArrayList<>(players);
    }

    public synchronized int getCurrentPlayers() {
        return players.size();
    }

    public synchronized boolean hasPlayer(int userId) {
        for (User player : players) {
            if (player.getId() == userId) return true;
        }
        return false;
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

    private void updateStatus() {
        // Nếu phòng đang trong trận đấu thì không tự động chuyển về Waiting/Full
        if ("Playing".equalsIgnoreCase(this.status)) {
            return;
        }

        if (players.size() >= maxPlayers) {
            this.status = "Full";
        } else {
            this.status = "Waiting";
        }
    }

    // =========================
    // READY STATES
    // =========================
    public synchronized Map<Integer, Boolean> getReadyStates() {
        return new LinkedHashMap<>(readyStates);
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

    public synchronized void resetReadyStatesForNewGame() {
        for (User player : players) {
            if (player.getId() == hostId) {
                readyStates.put(player.getId(), true);
            } else {
                readyStates.put(player.getId(), false);
            }
        }
        this.status = "Waiting";
        LOGGER.info("[Room] Đã reset Ready cho ván mới.");
    }

    // =========================
    // TRANSFER HOST
    // =========================
    public synchronized void transferHostRandom() {
        if (players.isEmpty()) {
            return;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(players.size());
        User newHost = players.get(randomIndex);

        hostId = newHost.getId();
        readyStates.put(newHost.getId(), true);

        LOGGER.info("[Room] Host mới: " + newHost.getUsername() + " (ID: " + newHost.getId() + ")");
    }

    // =========================
    // MATCH DURATION
    // =========================
    public synchronized int getDuration() {
        return duration;
    }

    public synchronized boolean setDuration(int duration) {
        if (duration != 45 && duration != 60 && duration != 90 && duration != 180) {
            return false;
        }
        this.duration = duration;
        return true;
    }

    public synchronized GameLoop getGameLoop() {
        return gameLoop;
    }

    public synchronized void setGameLoop(GameLoop gameLoop) {
        this.gameLoop = gameLoop;
    }

    public synchronized GameStateManager getGameStateManager() {
        return gameStateManager;
    }

    public synchronized void setGameStateManager(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    public Map<Integer, Integer> getUserTankMappings() {
        return userTankMappings;
    }
}