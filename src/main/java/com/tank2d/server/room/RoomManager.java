package com.tank2d.server.room;

import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class RoomManager {

    private static final Logger LOGGER = Logger.getLogger(RoomManager.class.getName());

    private final Map<Integer, Room> rooms;
    private final Map<Integer, Integer> penalizedUsers = new ConcurrentHashMap<>(); // userId -> roomId

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

        int roomId = 1;
        while (rooms.containsKey(roomId)) {
            roomId++;
        }

        Room room = new Room(
                roomId,
                "Room " + String.format("%02d", roomId),
                creator.getId()
        );

        room.addPlayer(creator);
        rooms.put(roomId, room);

        LOGGER.info(
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
    // JOIN ROOM (ĐÃ SỬA: CHẶN VÀO PHÒNG ĐANG CHƠI HOẶC ĐÃ ĐẦY)
    // =========================
    public synchronized boolean joinRoom(int roomId, User user) {
        Room room = rooms.get(roomId);

        if (room == null || user == null) {
            return false;
        }

        // Nếu người chơi ĐÃ Ở TRONG PHÒNG rồi -> Trả về true để đồng bộ state, không báo lỗi giả
        if (room.hasPlayer(user.getId())) {
            return true;
        }

        // Chặn người chơi MỚI vào phòng đang chơi
        if ("Playing".equalsIgnoreCase(room.getStatus())) {
            LOGGER.info("[RoomManager] Từ chối vào phòng " + roomId + ": Trận đấu đang diễn ra.");
            return false;
        }

        // Chặn vào phòng đã đủ người
        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            LOGGER.info("[RoomManager] Từ chối vào phòng " + roomId + ": Phòng đã đầy.");
            return false;
        }

        boolean success = room.addPlayer(user);

        if (success) {
            LOGGER.info(
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
    public synchronized boolean leaveRoom(int roomId, int userId) {
        Room room = rooms.get(roomId);

        if (room == null) {
            return false;
        }

        boolean wasHost = room.isHost(userId);
        boolean success = room.removePlayer(userId);

        if (!success) {
            return false;
        }

        // Không còn ai -> Xóa phòng triệt để khỏi bộ nhớ Server
        if (room.getCurrentPlayers() <= 0) {
            rooms.remove(roomId);
            LOGGER.info(
                    "[RoomManager] Phòng "
                    + roomId
                    + " đã được xóa vì không còn người chơi."
            );
            return true;
        }

        // Nếu Host rời -> chuyển quyền Host cho người kế tiếp
        if (wasHost) {
            room.transferHostRandom();
            LOGGER.info(
                    "[RoomManager] Host cũ đã rời phòng "
                    + roomId
                    + ". Đã chuyển quyền Host sang ID: "
                    + room.getHostId()
            );
        }

        return true;
    }

    // =========================
    // GET ROOM
    // =========================
    public synchronized Room getRoom(int roomId) {
        return rooms.get(roomId);
    }

    // =========================
    // GET ALL ROOMS
    // =========================
    public synchronized List<RoomDTO> getAllRooms() {
        List<RoomDTO> roomDTOs = new ArrayList<>();
        for (Room room : rooms.values()) {
            roomDTOs.add(toRoomDTO(room));
        }
        return roomDTOs;
    }

    // =========================
    // GET ROOM DTO
    // =========================
    public synchronized RoomDTO getRoomDTO(int roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return null;
        }
        return toRoomDTO(room);
    }

    // =========================
    // SET PLAYER READY
    // =========================
    public synchronized boolean setPlayerReady(int roomId, int userId, boolean ready) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return false;
        }
        return room.setReady(userId, ready);
    }

    // =========================
    // SET ROOM DURATION
    // =========================
    public synchronized boolean setRoomDuration(int roomId, int duration) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return false;
        }

        if (duration != 45 && duration != 60 && duration != 90 && duration != 180) {
            return false;
        }

        room.setDuration(duration);
        LOGGER.info(
                "[RoomManager] "
                + room.getRoomName()
                + " chọn thời lượng: "
                + duration
                + " giây"
        );

        return true;
    }

    // =========================
    // GET ROOM DURATION
    // =========================
    public synchronized int getRoomDuration(int roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return 60;
        }
        return room.getDuration();
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
                room.getReadyStates(),
                room.getDuration()
        );
    }
    
    // =========================================================
    // RESET PHÒNG VÀ HỦY SẴN SÀNG KHI HẾT TRẬN ĐẤU
    // =========================================================
    public synchronized void resetRoomAfterMatch(int roomId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.resetReadyStatesForNewGame(); // GỌI TRỰC TIẾP HÀM CÓ SẴN TRONG Room.java
            room.setGameLoop(null);
            room.setGameStateManager(null);
            LOGGER.info("[RoomManager] Đã kích hoạt resetReadyStatesForNewGame() cho phòng " + roomId);
        }
        removePenaltiesForRoom(roomId);
    }

    // =========================================================
    // PENALTY MANAGEMENT (PHẠT THOÁT GIỮA TRẬN)
    // =========================================================
    public void addPenalty(int userId, int roomId) {
        penalizedUsers.put(userId, roomId);
        LOGGER.info("[RoomManager] Phạt user ID " + userId + " do thoát trận phòng " + roomId);
    }

    public void removePenalty(int userId) {
        penalizedUsers.remove(userId);
    }

    public void removePenaltiesForRoom(int roomId) {
        penalizedUsers.entrySet().removeIf(e -> e.getValue() == roomId);
        LOGGER.info("[RoomManager] Đã xóa toàn bộ hình phạt cho phòng " + roomId);
    }

    public boolean isPenalized(int userId) {
        return penalizedUsers.containsKey(userId);
    }

    public Integer getPenalizedRoomId(int userId) {
        return penalizedUsers.get(userId);
    }

    public List<Integer> getPenalizedUserIdsForRoom(int roomId) {
        List<Integer> userIds = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : penalizedUsers.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == roomId) {
                userIds.add(entry.getKey());
            }
        }
        return userIds;
    }

    // =========================================================
    // RECONNECT SUPPORT: TÌM PHÒNG ĐANG CHƠI CỦA USER
    // =========================================================
    public synchronized Room getPlayingRoomByUserId(int userId) {
        for (Room room : rooms.values()) {
            if ("Playing".equalsIgnoreCase(room.getStatus()) && room.hasPlayer(userId)) {
                return room;
            }
        }
        return null;
    }
}