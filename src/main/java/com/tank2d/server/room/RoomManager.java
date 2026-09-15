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

    public synchronized Room createRoom(User creator) {
        int roomId = nextRoomId++;

        Room room = new Room(
                roomId,
                "Room " + String.format("%02d", roomId)
        );

        if (creator != null) {
            room.addPlayer(creator);
        }

        rooms.put(roomId, room);

        System.out.println(
                "[RoomManager] Tạo phòng: "
                + room.getRoomName()
        );

        return room;
    }

    public synchronized boolean joinRoom(int roomId, User user) {
        Room room = rooms.get(roomId);

        if (room == null || user == null) {
            return false;
        }

        boolean success = room.addPlayer(user);

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

    public synchronized boolean leaveRoom(int roomId, int userId) {
        Room room = rooms.get(roomId);

        if (room == null) {
            return false;
        }

        boolean success = room.removePlayer(userId);

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

    public synchronized Room getRoom(int roomId) {
        return rooms.get(roomId);
    }

    public synchronized List<RoomDTO> getAllRooms() {
        List<RoomDTO> roomDTOs = new ArrayList<>();

        for (Room room : rooms.values()) {
            roomDTOs.add(toRoomDTO(room));
        }

        return roomDTOs;
    }

    public synchronized RoomDTO getRoomDTO(int roomId) {
        Room room = rooms.get(roomId);

        if (room == null) {
            return null;
        }

        return toRoomDTO(room);
    }
    public synchronized boolean setPlayerReady(
        int roomId,
        int userId,
        boolean ready) {

    Room room = rooms.get(roomId);

    if (room == null) {
        return false;
    }

    return room.setReady(userId, ready);
}

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
            playerNames
    );
}
}
