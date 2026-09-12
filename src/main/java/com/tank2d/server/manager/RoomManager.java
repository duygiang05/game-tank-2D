package com.tank2d.server.manager;

import com.tank2d.common.dto.RoomDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoomManager {

    private final Map<Integer, RoomDTO> rooms;
    private int nextRoomId = 1;

    public RoomManager() {
        rooms = new LinkedHashMap<>();
    }

    public synchronized RoomDTO createRoom() {
        int roomId = nextRoomId++;

        RoomDTO room = new RoomDTO(
                roomId,
                "Room " + String.format("%02d", roomId),
                1,
                8,
                "Waiting"
        );

        rooms.put(roomId, room);

        System.out.println("[RoomManager] Tạo phòng: "
                + room.getRoomName());

        return room;
    }

    public synchronized boolean joinRoom(int roomId) {
        RoomDTO room = rooms.get(roomId);

        if (room == null) {
            return false;
        }

        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            return false;
        }

        room.setCurrentPlayers(room.getCurrentPlayers() + 1);

        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            room.setStatus("Full");
        } else {
            room.setStatus("Waiting");
        }

        System.out.println("[RoomManager] "
                + room.getRoomName()
                + " có "
                + room.getCurrentPlayers()
                + "/"
                + room.getMaxPlayers());

        return true;
    }

    public synchronized List<RoomDTO> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    public synchronized RoomDTO getRoom(int roomId) {
        return rooms.get(roomId);
    }
}