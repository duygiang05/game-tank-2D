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

    public ClientHandler(Socket socket, UserDAO userDAO, RoomManager roomManager) {
        this.socket = socket;
        this.userDAO = userDAO;
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
        if (packet.getType() == null) return;

        switch (packet.getType()) {

            case AUTH_LOGIN_REQ:
                handleLogin(packet.getData());
                break;
            case AUTH_REGISTER_REQ:
                handleRegister(packet.getData());
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
        } catch (IOException ignored) {}
    }

    // =========================================================
    // CREATE ROOM
    // =========================================================
    private void handleCreateRoom() {

        try {
            if (currentUser == null) return;
            Room room = roomManager.createRoom(currentUser);
            currentRoomId = room.getRoomId();

            Packet response = new Packet(PacketType.ROOM_STATE_UPDATE, gson.toJson(roomManager.getRoomDTO(currentRoomId)));
            NetworkUtil.sendPacket(dos, response);
            broadcastLobbyRooms();
        } catch (IOException ignored) {}
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
            } else {
                Packet response = new Packet(PacketType.ROOM_STATE_UPDATE, gson.toJson(roomManager.getRoomDTO(roomId)));
                NetworkUtil.sendPacket(dos, response);
            }
        } catch (Exception ignored) {}
    }

    // =========================================================
    // READY
    // =========================================================
    private void handleReady(String rawJson) {

        try {
            if (currentUser == null || currentRoomId == -1) return;
            boolean ready = gson.fromJson(rawJson, Boolean.class);
            if (roomManager.setPlayerReady(currentRoomId, currentUser.getId(), ready)) {
                broadcastRoomState();
            }
        } catch (Exception ignored) {}
    }

    private void handlePlayerInput(String rawJson) {
        if (currentGameLoop == null || myTankId == -1) return;
        TankEntity tank = currentGameLoop.getTank(myTankId);
        if (tank == null || !tank.isAlive()) return;

        try {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Boolean>>(){}.getType();
            Map<String, Boolean> input = gson.fromJson(rawJson, type);
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

    private void handlePlayerShoot(String rawJson) {
        if (currentGameLoop == null || myTankId == -1) return;
        TankEntity tank = currentGameLoop.getTank(myTankId);
        if (tank == null || !tank.isAlive()) return;

        com.tank2d.server.model.BulletEntity.BulletType requestedType = com.tank2d.server.model.BulletEntity.BulletType.NORMAL;
        try {
            com.tank2d.common.dto.game.PlayerShootRequestDTO req =
                    gson.fromJson(rawJson, com.tank2d.common.dto.game.PlayerShootRequestDTO.class);
            if (req != null && "ROCKET".equalsIgnoreCase(req.getBulletType())) {
                requestedType = com.tank2d.server.model.BulletEntity.BulletType.ROCKET;
            }
        } catch (Exception ignored) {} // payload rỗng "{}" hoặc lỗi -> mặc định NORMAL

        currentGameLoop.handleShootRequest(tank, requestedType);
    }

    // =========================================================================
    // TASK 1 (GIANG) & FIX THEO PHẢN HỒI CỦA HOÀNG (PHYSICS & MAP)
    // =========================================================================
    private void handleStartGame() {
        try {
            if (currentUser == null || currentRoomId == -1) return;
            Room room = roomManager.getRoom(currentRoomId);
            if (room == null || !room.isHost(currentUser.getId())) return;
            if (room.getCurrentPlayers() < room.getMaxPlayers() || !room.areAllPlayersReady()) return;

            broadcastGameStart();

            // 1. Khởi tạo GameLoop với Tick-rate chuẩn từ Config (thay vì hardcode 60)
            int serverTickRate = ConfigLoader.getPhysicsStats().has("server_tick_rate") 
                    ? ConfigLoader.getPhysicsStats().get("server_tick_rate").getAsInt() : 30;
            GameLoop gameLoop = new GameLoop(serverTickRate);

            // 2. KHẮC PHỤC LỖI HOÀNG NÊU: Nạp GameMap để kích hoạt vật lý chặn tường
            try {
                gameLoop.setGameMap(MapLoader.loadFromFile("config/maps/map_default.json"));
            } catch (Exception e) {
                System.err.println("[Game] Cảnh báo: Không thể nạp map_default.json: " + e.getMessage());
            }

            // 3. Khởi tạo GameStateManager với thời lượng phòng chọn từ Saimay (hoặc mặc định 60s)
            double matchDuration = room.getMatchDuration() > 0 ? room.getMatchDuration() : 60.0;
            GameStateManager stateManager = new GameStateManager(gameLoop, userDAO, matchDuration);
            gameLoop.setStateManager(stateManager);
            gameLoop.setMapChangeListener(stateManager); // GameStateManager cần implement MapChangeListener
            gameLoop.setItemEventListener(stateManager);  // và ItemEventListener
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

            // 6. SNAPSHOT LISTENER ĐỒNG BỘ: Hỗ trợ Bụi Cỏ / Tàng hình cá nhân hóa
            int finalRoomId = currentRoomId;
            gameLoop.setSnapshotListener(perViewerSnapshots -> {
                networkBroadcastPool.submit(() -> {
                    try {
                        for (ClientHandler client : connectedClients) {
                            if (client.currentRoomId == finalRoomId && client.dos != null) {
                                var snap = perViewerSnapshots.get(client.myTankId);
                                if (snap == null) continue;
                                String json = gson.toJson(snap);
                                NetworkUtil.sendPacket(client.dos, new Packet(PacketType.GAME_SNAPSHOT, json));
                            }
                        }
                    } catch (Exception ignored) {}
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
            if (currentUser == null || currentRoomId == -1) return;

            // 1. Nếu đang trong trận đấu, báo GameStateManager hủy xe an toàn
            if (currentGameStateManager != null && myTankId != -1) {
                currentGameStateManager.handlePlayerLeave(myTankId);
                this.currentGameLoop = null;
                this.currentGameStateManager = null;
                this.myTankId = -1;
            }

            int roomId = currentRoomId;
            int userId = currentUser.getId();

            // 2. Xóa khỏi RoomManager
            if (roomManager.leaveRoom(roomId, userId)) {
                System.out.println("[Room] User " + currentUser.getUsername() + " đã thoát phòng " + roomId);
                currentRoomId = -1;
                broadcastRoomStateForRoom(roomId);
            }

        } catch (Exception e) {
            System.err.println("[Room] Lỗi xử lý ROOM_LEAVE_REQ: " + e.getMessage());
        }

    public void closeConnection() {
        isRunning = false;
        connectedClients.remove(this);

        if (currentUser != null && currentRoomId != -1) {
            handleLeaveRoom();
        }

        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    private void broadcastLobbyRooms() {
        try {
            Packet packet = new Packet(PacketType.LOBBY_ROOMS_RES, gson.toJson(roomManager.getAllRooms()));
            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == -1) NetworkUtil.sendPacket(client.dos, packet);
            }
        } catch (IOException ignored) {}
    }

    private void broadcastRoomState() {
        broadcastRoomStateForRoom(currentRoomId);
    }

    private void broadcastRoomStateForRoom(int roomId) {
        if (roomId == -1) return;
        Room room = roomManager.getRoom(roomId);
        if (room == null) return;

        Packet packet = new Packet(PacketType.ROOM_STATE_UPDATE, gson.toJson(roomManager.getRoomDTO(roomId)));
        for (ClientHandler client : connectedClients) {
            if (client.currentRoomId == roomId) {
                try {
                    NetworkUtil.sendPacket(client.dos, packet);
                } catch (IOException ignored) {}
            }
        }
    }

    private void broadcastGameStart() {
        if (currentRoomId == -1) return;
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomId", currentRoomId);
        Packet packet = new Packet(PacketType.GAME_START_NOTIFY, gson.toJson(dataMap));

        for (ClientHandler client : connectedClients) {
            if (client.currentRoomId == currentRoomId) {
                try {
                    NetworkUtil.sendPacket(client.dos, packet);
                } catch (IOException ignored) {}
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
        } catch (IOException ignored) {}
    }

    private void handleGetRooms() {
        try {
            Packet response = new Packet(PacketType.LOBBY_ROOMS_RES, gson.toJson(roomManager.getAllRooms()));
            NetworkUtil.sendPacket(dos, response);
        } catch (IOException ignored) {}
    }
}