package com.tank2d.server.network;

import com.google.gson.Gson;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.LoginRequest;
import com.tank2d.common.dto.LoginResponse;
import com.tank2d.common.dto.RegisterResponse;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.NetworkUtil;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
import com.tank2d.server.dao.UserDAO;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.game.GameStateManager;
import com.tank2d.server.map.MapLoader;
import com.tank2d.server.model.TankEntity;
import com.tank2d.server.room.Room;
import com.tank2d.server.room.RoomManager;
import com.tank2d.common.dto.game.TankPlayerDTO;
import com.tank2d.server.dao.MatchDAO;
import java.util.ArrayList;
import java.util.List;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler implements Runnable {

    private static final Set<ClientHandler> connectedClients = ConcurrentHashMap.newKeySet();
    private static final ExecutorService networkBroadcastPool = Executors.newCachedThreadPool();

    private final Socket socket;
    private final UserDAO userDAO;
    private final MatchDAO matchDAO;
    private final RoomManager roomManager;
    private final Gson gson;

    private DataInputStream dis;
    private DataOutputStream dos;
    private volatile boolean isRunning;
    private User currentUser;
    private int currentRoomId = -1;
    private GameLoop currentGameLoop;
    private GameStateManager currentGameStateManager;
    private int myTankId = -1;

    public ClientHandler(Socket socket, UserDAO userDAO, MatchDAO matchDAO, RoomManager roomManager) {
        this.socket = socket;
        this.userDAO = userDAO;
        this.matchDAO = matchDAO;
        this.roomManager = roomManager;
        this.gson = new Gson();
        this.isRunning = true;
    }

    @Override
    public void run() {

        String clientAddress
                = socket.getRemoteSocketAddress().toString();

        System.out.println(
                "[ClientHandler] Khởi tạo phiên làm việc với Client: "
                + clientAddress
        );

        try {

            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            connectedClients.add(this);

            // Vòng lặp đọc Packet từ Client
            while (isRunning && !socket.isClosed()) {

                Packet packet = NetworkUtil.readPacket(dis);

                if (packet == null) {

                    System.out.println(
                            "[ClientHandler] Client ngắt kết nối: "
                            + clientAddress
                    );

                    break;
                }

                dispatchPacket(packet);
            }

        } catch (IOException e) {

            System.err.println(
                    "[ClientHandler] Lỗi kết nối ("
                    + clientAddress
                    + "): "
                    + e.getMessage()
            );

        } finally {

            closeConnection();
        }
    }

    // =========================================================
    // DISPATCH PACKET
    // =========================================================
    private void dispatchPacket(Packet packet) {
        if (packet.getType() == null) {
            return;
        }

        switch (packet.getType()) {

            case AUTH_LOGIN_REQ:
                handleLogin(packet.getData());
                break;
            case AUTH_REGISTER_REQ:
                handleRegister(packet.getData());
                break;

            case LEADERBOARD_REQ:
                handleLeaderboard();
                break;

            case LOGOUT_REQ:
                handleLogout();
                break;

            case LOBBY_GET_ROOMS_REQ:
                handleGetRooms();
                break;
            case ROOM_CREATE_REQ:
                handleCreateRoom();
                break;
            case ROOM_JOIN_REQ:
                handleJoinRoom(packet.getData());
                break;
            case ROOM_READY_REQ:
                handleReady(packet.getData());
                break;

            case ROOM_DURATION_REQ:
                handleRoomDuration(packet.getData());
                break;

            case ROOM_START_REQ:
                handleStartGame();
                break;

            case ROOM_LEAVE_REQ:
                handleLeaveRoom();
                break;

            case TANK_PLAYER_INFO_REQ:
                handleTankPlayerInfo();
                break;

            case PLAYER_INPUT:
                handlePlayerInput(packet.getData());
                break;
            case PLAYER_SHOOT_REQ:
                handlePlayerShoot(packet.getData());
                break;
            default:
                System.out.println("[ClientHandler] Nhận packet chưa hỗ trợ: " + packet.getType());
                break;
        }
    }

    private void handleTankPlayerInfo() {

        if (currentUser == null || currentRoomId == -1) {
            return;
        }

        List<TankPlayerDTO> tankPlayers = new ArrayList<>();

        for (ClientHandler client : connectedClients) {

            if (client.currentRoomId == currentRoomId
                    && client.currentUser != null
                    && client.myTankId > 0) {

                tankPlayers.add(
                        new TankPlayerDTO(
                                client.myTankId,
                                client.currentUser.getUsername()
                        )
                );
            }
        }

        String json = gson.toJson(tankPlayers);

        Packet response = new Packet(
                PacketType.TANK_PLAYER_INFO,
                json
        );

        try {
            if (dos != null) {
                NetworkUtil.sendPacket(dos, response);
            }

            System.out.println(
                    "[Game] Gửi Tank Player Info cho User "
                    + currentUser.getUsername()
                    + ": " + json
            );

        } catch (Exception e) {
            System.err.println(
                    "[Game] Lỗi gửi Tank Player Info: "
                    + e.getMessage()
            );
        }
    }

    private void handleLogout() {
        try {
            // Nếu người chơi đang ở trong phòng thì rời phòng trước
            if (currentRoomId != -1) {
                int roomId = currentRoomId;

                roomManager.leaveRoom(roomId, currentUser.getId());

                currentRoomId = -1;
                myTankId = -1;

                broadcastRoomStateForRoom(roomId);
                broadcastLobbyRooms();
            }

            // Xóa thông tin user khỏi session phía server
            currentUser = null;

            System.out.println("Client logged out successfully.");

        } catch (Exception e) {
            System.err.println("Error while logging out: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================
    // LOGIN
    // =========================================================
    private void handleLogin(String rawJson) {

        LoginResponse res;

        try {
            LoginRequest req = gson.fromJson(rawJson, LoginRequest.class);
            User user = userDAO.login(req.getUsername(), req.getPassword());

            if (user != null) {

                this.currentUser = user;
                res = new LoginResponse(true, user.getId(), user.getUsername(), "Đăng nhập thành công!");
            } else {
                res = new LoginResponse(false, -1, "", "Sai tên tài khoản hoặc mật khẩu!");
            }
        } catch (Exception e) {
            res = new LoginResponse(false, -1, "", "Lỗi định dạng dữ liệu đăng nhập!");
        }

        try {
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_LOGIN_RES, gson.toJson(res)));
        } catch (IOException ignored) {
        }
    }

    // =========================================================
    // CREATE ROOM
    // =========================================================
    private void handleCreateRoom() {

        try {
            if (currentUser == null) {
                return;
            }
            Room room = roomManager.createRoom(currentUser);
            currentRoomId = room.getRoomId();

            Packet response = new Packet(PacketType.ROOM_STATE_UPDATE, gson.toJson(roomManager.getRoomDTO(currentRoomId)));
            NetworkUtil.sendPacket(dos, response);
            broadcastLobbyRooms();
        } catch (IOException ignored) {
        }
    }

    // =========================================================
    // JOIN ROOM
    // =========================================================
    private void handleJoinRoom(String rawJson) {

        try {
            int roomId = gson.fromJson(rawJson, Integer.class);
            boolean success = roomManager.joinRoom(roomId, currentUser);

            if (success) {

                currentRoomId = roomId;
                broadcastRoomState();
                broadcastLobbyRooms();
            } else {
                Packet response = new Packet(PacketType.ROOM_STATE_UPDATE, gson.toJson(roomManager.getRoomDTO(roomId)));
                NetworkUtil.sendPacket(dos, response);
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // READY
    // =========================================================
    private void handleReady(String rawJson) {

        try {
            if (currentUser == null || currentRoomId == -1) {
                return;
            }
            boolean ready = gson.fromJson(rawJson, Boolean.class);
            if (roomManager.setPlayerReady(currentRoomId, currentUser.getId(), ready)) {
                broadcastRoomState();
            }
        } catch (Exception ignored) {
        }
    }

    private void handlePlayerShoot(String rawJson) {
        if (currentGameLoop == null || myTankId == -1) {
            return;
        }
        TankEntity tank = currentGameLoop.getTank(myTankId);
        if (tank == null || !tank.isAlive()) {
            return;
        }

        com.tank2d.server.model.BulletEntity.BulletType requestedType = com.tank2d.server.model.BulletEntity.BulletType.NORMAL;
        try {
            com.tank2d.common.dto.game.PlayerShootRequestDTO req
                    = gson.fromJson(rawJson, com.tank2d.common.dto.game.PlayerShootRequestDTO.class);
            if (req != null && "ROCKET".equalsIgnoreCase(req.getBulletType())) {
                requestedType = com.tank2d.server.model.BulletEntity.BulletType.ROCKET;
            }
        } catch (Exception ignored) {
        } // payload rỗng "{}" hoặc lỗi -> mặc định NORMAL

        currentGameLoop.handleShootRequest(tank, requestedType);
    }

    // =========================================================================
    // TASK 1 (GIANG) & FIX THEO PHẢN HỒI CỦA HOÀNG (PHYSICS & MAP)
    // =========================================================================
    private void handleStartGame() {
        try {
            if (currentUser == null || currentRoomId == -1) {
                return;
            }
            Room room = roomManager.getRoom(currentRoomId);
            if (room == null || !room.isHost(currentUser.getId())) {
                return;
            }

            // FIX 1: Chỉ cần tối thiểu 2 người (hoặc >= 1 khi test solo) và tất cả đã Ready
            if (room.getCurrentPlayers() < 2 || !room.areAllPlayersReady()) {
                System.out.println("[Game] Chưa đủ điều kiện bắt đầu: Số người = "
                        + room.getCurrentPlayers() + ", Ready = " + room.areAllPlayersReady());
                return;
            }

            broadcastGameStart();

            // 1. Khởi tạo GameLoop với Tick-rate chuẩn từ Config
            int serverTickRate = ConfigLoader.getPhysicsStats().has("server_tick_rate")
                    ? ConfigLoader.getPhysicsStats().get("server_tick_rate").getAsInt() : 30;
            GameLoop gameLoop = new GameLoop(serverTickRate);

            // FIX 2: Nạp đúng map tuyết của dự án
            try {
                gameLoop.setGameMap(MapLoader.loadFromFile("config/maps/map_default.json"));
            } catch (Exception e) {
                System.err.println("[Game] Cảnh báo: Không thể nạp map_snow.json: " + e.getMessage());
            }

            // FIX 3: Lấy duration linh hoạt theo cả 2 cách đặt tên của Room
            double matchDuration = 60.0;
            try {
                matchDuration = room.getDuration();
            } catch (NoSuchMethodError | Exception e) {
                matchDuration = room.getMatchDuration();
            }

            GameStateManager stateManager = new GameStateManager(gameLoop, userDAO, matchDAO, matchDuration);
            gameLoop.setStateManager(stateManager);
            gameLoop.setMapChangeListener(stateManager);
            gameLoop.setItemEventListener(stateManager);

            // 4. Lấy tốc độ chuẩn hóa pixel/giây từ ConfigLoader
            double speed = ConfigLoader.getTankSpeedPerSecond();
            double rotationSpeed = 120.0; // độ/giây

            // 5. Nạp các xe tăng theo 4 góc Spawn cấu hình
            int tankIndex = 1;
            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == currentRoomId) {
                    double startX = ConfigLoader.getSpawnX(tankIndex, 100.0);
                    double startY = ConfigLoader.getSpawnY(tankIndex, 100.0);
                    double startAngle = ConfigLoader.getSpawnAngle(tankIndex, 0.0);

                    TankEntity tank = new TankEntity(tankIndex, startX, startY, startAngle, speed, rotationSpeed);
                    tank.setHp(ConfigLoader.getMaxHp());
                    gameLoop.addTank(tank);
                    stateManager.registerPlayer(tankIndex, client.currentUser.getId(), client.dos);

                    client.myTankId = tankIndex;
                    client.currentGameLoop = gameLoop;
                    client.currentGameStateManager = stateManager;
                    tankIndex++;
                }
            }

            // Gửi mapping Tank -> Username cho tất cả client trong phòng.
            List<TankPlayerDTO> tankPlayers = new ArrayList<>();
            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == currentRoomId
                        && client.currentUser != null
                        && client.myTankId > 0) {
                    tankPlayers.add(new TankPlayerDTO(
                            client.myTankId,
                            client.currentUser.getUsername()
                    ));
                }
            }

            String tankPlayersJson = gson.toJson(tankPlayers);
            Packet tankPlayerPacket = new Packet(
                    PacketType.TANK_PLAYER_INFO,
                    tankPlayersJson
            );

            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == currentRoomId
                        && client.dos != null) {
                    try {
                        NetworkUtil.sendPacket(client.dos, tankPlayerPacket);
                    } catch (IOException ignored) {
                    }
                }
            }

            // 6. Snapshot Listener hỗ trợ Bụi Cỏ / Tàng hình cá nhân hóa
            int finalRoomId = currentRoomId;
            gameLoop.setSnapshotListener(perViewerSnapshots -> {
                networkBroadcastPool.submit(() -> {
                    try {
                        for (ClientHandler client : connectedClients) {
                            if (client.currentRoomId == finalRoomId && client.dos != null) {
                                var snap = perViewerSnapshots.get(client.myTankId);
                                if (snap == null) {
                                    continue;
                                }
                                String json = gson.toJson(snap);
                                NetworkUtil.sendPacket(client.dos, new Packet(PacketType.GAME_SNAPSHOT, json));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });
            });

            Thread loopThread = new Thread(gameLoop);
            loopThread.setName("GameLoop-Room-" + finalRoomId);
            loopThread.start();

        } catch (Exception e) {
            System.err.println("[Game] Lỗi xử lý ROOM_START_REQ: " + e.getMessage());
        }
    }

    // =========================================================================
    // TASK 1: XỬ LÝ THOÁT TRẬN GIỮA CHỪNG (ROOM_LEAVE_REQ)
    // =========================================================================
    private void handleLeaveRoom() {
        try {
            if (currentUser == null || currentRoomId == -1) {
                return;
            }

            // 1. Nếu đang trong trận đấu:
            if (currentGameStateManager != null && myTankId != -1) {
                // Báo GameStateManager lưu điểm vào DB, ngắt socket, kết thúc trận nếu chỉ còn 1 người
                currentGameStateManager.handlePlayerLeave(myTankId);

                //Xóa xe khỏi GameLoop để client không còn vẽ xác xe hoặc bóng ma
                if (currentGameLoop != null) {
                    currentGameLoop.getTanks().remove(myTankId);
                }

                this.currentGameLoop = null;
                this.currentGameStateManager = null;
                this.myTankId = -1;
            }

            int roomId = currentRoomId;
            int userId = currentUser.getId();

            // 2. Xóa khỏi RoomManager & thông báo cho các người chơi còn lại trong phòng
            if (roomManager.leaveRoom(roomId, userId)) {
                System.out.println("[Room] User " + currentUser.getUsername() + " đã thoát phòng " + roomId);
                currentRoomId = -1;
                broadcastRoomStateForRoom(roomId);

                broadcastLobbyRooms();
            }

        } catch (Exception e) {
            System.err.println("[Room] Lỗi xử lý ROOM_LEAVE_REQ: " + e.getMessage());
        }
    }

    public void closeConnection() {
        isRunning = false;
        connectedClients.remove(this);

        if (currentUser != null && currentRoomId != -1) {
            handleLeaveRoom();
        }

        try {
            if (dis != null) {
                dis.close();
            }
            if (dos != null) {
                dos.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void handleLeaderboard() {

        try {

            List<com.tank2d.common.dto.LeaderboardDTO> leaderboard
                    = userDAO.getLeaderboard();

            Packet response = new Packet(
                    PacketType.LEADERBOARD_RES,
                    gson.toJson(leaderboard)
            );

            NetworkUtil.sendPacket(
                    dos,
                    response
            );

            System.out.println(
                    "[Leaderboard] Đã gửi bảng xếp hạng cho Client."
            );

        } catch (IOException e) {

            System.err.println(
                    "[Leaderboard] Lỗi gửi bảng xếp hạng: "
                    + e.getMessage()
            );
        }
    }

    private void broadcastLobbyRooms() {
        try {
            Packet packet = new Packet(PacketType.ROOM_LIST_UPDATE, gson.toJson(roomManager.getAllRooms()));
            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == -1) {
                    NetworkUtil.sendPacket(client.dos, packet);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void broadcastRoomState() {
        broadcastRoomStateForRoom(currentRoomId);
    }

    private void broadcastRoomStateForRoom(int roomId) {
        if (roomId == -1) {
            return;
        }
        Room room = roomManager.getRoom(roomId);
        if (room == null) {
            return;
        }

        Packet packet = new Packet(PacketType.ROOM_STATE_UPDATE, gson.toJson(roomManager.getRoomDTO(roomId)));
        for (ClientHandler client : connectedClients) {
            if (client.currentRoomId == roomId) {
                try {
                    NetworkUtil.sendPacket(client.dos, packet);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void broadcastGameStart() {
        if (currentRoomId == -1) {
            return;
        }
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomId", currentRoomId);
        Packet packet = new Packet(PacketType.GAME_START_NOTIFY, gson.toJson(dataMap));

        for (ClientHandler client : connectedClients) {
            if (client.currentRoomId == currentRoomId) {
                try {
                    NetworkUtil.sendPacket(client.dos, packet);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void handleRegister(String rawJson) {
        RegisterResponse res;
        try {
            LoginRequest req = gson.fromJson(rawJson, LoginRequest.class);
            boolean isSuccess = userDAO.register(req.getUsername(), req.getPassword());
            res = isSuccess ? new RegisterResponse(true, "Đăng ký thành công!")
                    : new RegisterResponse(false, "Đăng ký thất bại! Tài khoản đã tồn tại.");
        } catch (Exception e) {
            res = new RegisterResponse(false, "Lỗi định dạng dữ liệu đăng ký!");
        }

        try {
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_REGISTER_RES, gson.toJson(res)));
        } catch (IOException ignored) {
        }
    }

    private void handleGetRooms() {
        try {
            Packet response = new Packet(PacketType.LOBBY_ROOMS_RES, gson.toJson(roomManager.getAllRooms()));
            NetworkUtil.sendPacket(dos, response);
        } catch (IOException ignored) {
        }
    }

    // ROOM DURATION
// =========================================================
    private void handleRoomDuration(String rawJson) {

        try {

            if (currentUser == null) {

                System.out.println(
                        "[Room] Client chưa đăng nhập."
                );

                return;
            }

            if (currentRoomId == -1) {

                System.out.println(
                        "[Room] Client chưa ở trong phòng."
                );

                return;
            }

            Room room
                    = roomManager.getRoom(
                            currentRoomId
                    );

            if (room == null) {

                System.out.println(
                        "[Room] Không tìm thấy phòng."
                );

                return;
            }

            // Chỉ Host được thay đổi thời lượng
            if (!room.isHost(
                    currentUser.getId())) {

                System.out.println(
                        "[Room] User "
                        + currentUser.getUsername()
                        + " không phải Host."
                );

                return;
            }

            int duration
                    = gson.fromJson(
                            rawJson,
                            Integer.class
                    );

            // Chỉ chấp nhận 45 / 60 / 90 giây
            if (duration != 45
                    && duration != 60
                    && duration != 90) {

                System.out.println(
                        "[Room] Thời lượng không hợp lệ: "
                        + duration
                );

                return;
            }

            boolean success
                    = roomManager.setRoomDuration(
                            currentRoomId,
                            duration
                    );

            if (success) {

                System.out.println(
                        "[Room] Host "
                        + currentUser.getUsername()
                        + " chọn "
                        + duration
                        + " giây."
                );

                // Đồng bộ duration cho tất cả người trong phòng
                broadcastRoomState();
                broadcastLobbyRooms();
            }

        } catch (Exception e) {

            System.err.println(
                    "[Room] Lỗi xử lý ROOM_DURATION_REQ: "
                    + e.getMessage()
            );
        }
    }

    private void handlePlayerInput(String rawJson) {

        if (currentGameLoop == null || myTankId == -1) {
            return;
        }

        TankEntity tank
                = currentGameLoop.getTank(myTankId);

        if (tank == null || !tank.isAlive()) {
            return;
        }

        try {

            java.lang.reflect.Type type
                    = new com.google.gson.reflect.TypeToken<
                            Map<String, Boolean>>() {
                    }.getType();

            Map<String, Boolean> input
                    = gson.fromJson(
                            rawJson,
                            type
                    );

            if (input != null) {

                boolean up
                        = input.getOrDefault(
                                "up",
                                false
                        );

                boolean down
                        = input.getOrDefault(
                                "down",
                                false
                        );

                boolean left
                        = input.getOrDefault(
                                "left",
                                false
                        );

                boolean right
                        = input.getOrDefault(
                                "right",
                                false
                        );

                // Di chuyển
                if (up && !down) {

                    tank.setMoveState(
                            TankEntity.MoveState.FORWARD
                    );

                } else if (down && !up) {

                    tank.setMoveState(
                            TankEntity.MoveState.BACKWARD
                    );

                } else {

                    tank.setMoveState(
                            TankEntity.MoveState.NONE
                    );
                }

                // Xoay
                if (left && !right) {

                    tank.setRotateState(
                            TankEntity.RotateState.LEFT
                    );

                } else if (right && !left) {

                    tank.setRotateState(
                            TankEntity.RotateState.RIGHT
                    );

                } else {

                    tank.setRotateState(
                            TankEntity.RotateState.NONE
                    );
                }
            }

        } catch (Exception ignored) {
        }
    }

    // =========================================================
    // PLAYER SHOOT
    // =========================================================
    // =========================================================
    // BROADCAST LOBBY
    // =========================================================
    // =========================================================
    // BROADCAST ROOM STATE
    // =========================================================
    // =========================================================
    // GAME START NOTIFY
    // =========================================================
    // =========================================================
    // START GAME
    // =========================================================
    // =========================================================
    // REGISTER
    // =========================================================
    // =========================================================
    // GET ROOMS
    // =========================================================
    // =========================================================
    // LEAVE ROOM
    // =========================================================
    // =========================================================
    // CLOSE CONNECTION
    // =========================================================
}
