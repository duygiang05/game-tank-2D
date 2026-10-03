package com.tank2d.server.room;

import com.tank2d.common.dto.RoomDTO;
import com.tank2d.common.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Trình quản lý danh sách toàn bộ các phòng chơi (Room Manager) trên máy chủ.
 * <p>
 * Hỗ trợ các nghiệp vụ:
 * <ul>
 *     <li>Tạo phòng mới, vào phòng, rời phòng và tự giải phóng phòng rỗng.</li>
 *     <li>Kiểm soát trạng thái sẵn sàng, lựa chọn thời lượng trận đấu.</li>
 *     <li>Quản lý danh sách phạt cấm thi đấu cho người chơi thoát trận giữa chừng.</li>
 *     <li>Tra cứu phòng đang diễn ra trận đấu để hỗ trợ tính năng Tái kết nối (Reconnect).</li>
 * </ul>
 */
public class RoomManager {

    private static final Logger LOGGER = Logger.getLogger(RoomManager.class.getName());

    private final Map<Integer, Room> rooms;
    private final Map<Integer, Integer> penalizedUsers = new ConcurrentHashMap<>();

    public RoomManager() {
        rooms = new LinkedHashMap<>();
    }

    /**
     * Tạo một phòng chơi mới với người tạo làm chủ phòng (Host).
     *
     * @param creator tài khoản người tạo phòng
     * @return đối tượng {@link Room} mới được tạo, hoặc {@code null} nếu creator không hợp lệ
     */
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

        LOGGER.info("[RoomManager] Tạo phòng: " + room.getRoomName()
                + " | Host: " + creator.getUsername() + " (ID: " + creator.getId() + ")");
        return room;
    }

    /**
     * Thêm người chơi vào phòng theo ID phòng chỉ định.
     * Tự động chặn vào các phòng đang thi đấu hoặc đã đầy người.
     *
     * @param roomId mã ID phòng cần tham gia
     * @param user   tài khoản người chơi
     * @return {@code true} nếu tham gia thành công, ngược lại {@code false}
     */
    public synchronized boolean joinRoom(int roomId, User user) {
        Room room = rooms.get(roomId);
        if (room == null || user == null) {
            return false;
        }

        if (room.hasPlayer(user.getId())) {
            return true;
        }

        if (Room.STATUS_PLAYING.equalsIgnoreCase(room.getStatus())) {
            LOGGER.info("[RoomManager] Từ chối vào phòng " + roomId + ": Trận đấu đang diễn ra.");
            return false;
        }

        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            LOGGER.info("[RoomManager] Từ chối vào phòng " + roomId + ": Phòng đã đầy.");
            return false;
        }

        boolean success = room.addPlayer(user);
        if (success) {
            LOGGER.info("[RoomManager] " + user.getUsername() + " vào " + room.getRoomName()
                    + " (" + room.getCurrentPlayers() + "/" + room.getMaxPlayers() + ")");
        }
        return success;
    }

    /**
     * Xóa người chơi khỏi phòng. Nếu phòng không còn ai sẽ tự động hủy khỏi bộ nhớ.
     *
     * @param roomId mã ID phòng
     * @param userId ID người chơi rời phòng
     * @return {@code true} nếu xử lý thành công
     */
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

        if (room.getCurrentPlayers() <= 0) {
            rooms.remove(roomId);
            LOGGER.info("[RoomManager] Phòng " + roomId + " đã được xóa vì không còn người chơi.");
            return true;
        }

        if (wasHost) {
            room.transferHostRandom();
            LOGGER.info("[RoomManager] Host cũ rời phòng " + roomId + ". Host mới: ID " + room.getHostId());
        }

        return true;
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

    public synchronized boolean setPlayerReady(int roomId, int userId, boolean ready) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return false;
        }
        return room.setReady(userId, ready);
    }

    public synchronized boolean setRoomDuration(int roomId, int duration) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return false;
        }

        if (duration != 45 && duration != 60 && duration != 90 && duration != 180) {
            return false;
        }

        room.setDuration(duration);
        LOGGER.info("[RoomManager] " + room.getRoomName() + " chọn thời lượng: " + duration + " giây");
        return true;
    }

    public synchronized int getRoomDuration(int roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return 60;
        }
        return room.getDuration();
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
                playerNames,
                room.getHostId(),
                room.getReadyStates(),
                room.getDuration()
        );
    }

    /**
     * Đặt lại trạng thái phòng sau khi trận đấu kết thúc.
     *
     * @param roomId ID phòng cần reset
     */
    public synchronized void resetRoomAfterMatch(int roomId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.resetReadyStatesForNewGame();
            room.setGameLoop(null);
            room.setGameStateManager(null);
            LOGGER.info("[RoomManager] Đã reset trạng thái phòng " + roomId + " cho trận đấu kế tiếp.");
        }
        removePenaltiesForRoom(roomId);
    }

    public void addPenalty(int userId, int roomId) {
        penalizedUsers.put(userId, roomId);
        LOGGER.info("[RoomManager] Áp dụng hình phạt cho User ID " + userId + " do thoát trận phòng " + roomId);
    }

    public void removePenalty(int userId) {
        penalizedUsers.remove(userId);
    }

    public void removePenaltiesForRoom(int roomId) {
        penalizedUsers.entrySet().removeIf(e -> e.getValue() == roomId);
        LOGGER.info("[RoomManager] Đã gỡ bỏ toàn bộ hình phạt của phòng " + roomId);
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

    /**
     * Tìm phòng đang trong trạng thái Playing mà người chơi đang tham gia (phục vụ cơ chế Reconnect).
     *
     * @param userId ID người chơi cần tra cứu
     * @return phòng chơi đang diễn ra trận đấu của người chơi, hoặc {@code null} nếu không có
     */
    public synchronized Room getPlayingRoomByUserId(int userId) {
        for (Room room : rooms.values()) {
            if (Room.STATUS_PLAYING.equalsIgnoreCase(room.getStatus()) && room.hasPlayer(userId)) {
                return room;
            }
        }
        return null;
    }
}