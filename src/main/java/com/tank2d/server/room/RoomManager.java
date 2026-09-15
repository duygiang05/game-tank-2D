package com.tank2d.server.room;

import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoomManager {

    private final Map<Integer, Room> rooms;
    private int nextRoomId = 1;

    public RoomManager() {
        rooms = new LinkedHashMap<>();
    }

    // =========================
    // CREATE ROOM
    // =========================

    public synchronized Room createRoom(User creator) {

        if (creator == null) {
            return null;
        }

        int roomId = nextRoomId++;

        /*
         * Người tạo phòng chính là Host.
         */
        Room room = new Room(
                roomId,
                "Room " + String.format("%02d", roomId),
                creator.getId()
        );

        room.addPlayer(creator);

        rooms.put(roomId, room);

        System.out.println(
                "[RoomManager] Tạo phòng: "
                + room.getRoomName()
                + " | Host: "
                + creator.getUsername()
                + " (ID: "
                + creator.getId()
                + ")"
        );

        return room;
    }

    // =========================
    // JOIN ROOM
    // =========================

    public synchronized boolean joinRoom(
            int roomId,
            User user) {

        Room room = rooms.get(roomId);

        if (room == null || user == null) {
            return false;
        }

        boolean success =
                room.addPlayer(user);

        if (success) {

            System.out.println(
                    "[RoomManager] "
                    + user.getUsername()
                    + " vào "
                    + room.getRoomName()
                    + " ("
                    + room.getCurrentPlayers()
                    + "/"
                    + room.getMaxPlayers()
                    + ")"
            );
        }

        return success;
    }

    // =========================
    // LEAVE ROOM
    // =========================

    public synchronized boolean leaveRoom(
            int roomId,
            int userId) {

        Room room = rooms.get(roomId);

        if (room == null) {
            return false;
        }

        boolean success =
                room.removePlayer(userId);

        if (success) {

            System.out.println(
                    "[RoomManager] User "
                    + userId
                    + " rời "
                    + room.getRoomName()
            );

            if (room.getCurrentPlayers() == 0) {

                rooms.remove(roomId);

                System.out.println(
                        "[RoomManager] Xóa phòng trống: "
                        + room.getRoomName()
                );
            }
        }

        return success;
    }

    // =========================
    // GET ROOM
    // =========================

    public synchronized Room getRoom(
            int roomId) {

        return rooms.get(roomId);
    }

    // =========================
    // GET ALL ROOMS
    // =========================

    public synchronized List<RoomDTO> getAllRooms() {

        List<RoomDTO> roomDTOs =
                new ArrayList<>();

        for (Room room : rooms.values()) {

            roomDTOs.add(
                    toRoomDTO(room)
            );
        }

        return roomDTOs;
    }

    // =========================
    // GET ROOM DTO
    // =========================

    public synchronized RoomDTO getRoomDTO(
            int roomId) {

        Room room =
                rooms.get(roomId);

        if (room == null) {
            return null;
        }

        return toRoomDTO(room);
    }

    // =========================
    // SET PLAYER READY
    // =========================

    public synchronized boolean setPlayerReady(
            int roomId,
            int userId,
            boolean ready) {

        Room room =
                rooms.get(roomId);

        if (room == null) {
            return false;
        }

        return room.setReady(
                userId,
                ready
        );
    }

    // =========================
    // CONVERT ROOM → DTO
    // =========================

    private RoomDTO toRoomDTO(Room room) {

    List<String> playerNames = new ArrayList<>();

    for (User user : room.getPlayers()) {
        playerNames.add(user.getUsername());
    }

    return new RoomDTO(
            room.getRoomId(),
            room.getRoomName(),
            room.getCurrentPlayers(),
            room.getMaxPlayers(),
            room.getStatus(),
            playerNames,
            room.getHostId(),
            room.getReadyStates()
    );
}
}