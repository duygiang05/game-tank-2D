package com.tank2d.server.network;

import com.google.gson.Gson;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.LoginRequest;
import com.tank2d.common.dto.LoginResponse;
import com.tank2d.common.dto.RegisterResponse;
import com.tank2d.common.dto.game.PlayerShootRequestDTO;
import com.tank2d.common.dto.game.TankPlayerDTO;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.NetworkUtil;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
import com.tank2d.server.dao.MatchDAO;
import com.tank2d.server.dao.UserDAO;
import com.tank2d.server.game.GameLoop;
import com.tank2d.server.game.GameStateManager;
import com.tank2d.server.map.MapLoader;
import com.tank2d.server.model.BulletEntity;
import com.tank2d.server.model.TankEntity;
import com.tank2d.server.room.Room;
import com.tank2d.server.room.RoomManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        String clientAddress = socket.getRemoteSocketAddress().toString();
        System.out.println("[ClientHandler] Khởi tạo phiên làm việc với Client: " + clientAddress);

        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            connectedClients.add(this);

            while (isRunning && !socket.isClosed()) {
                Packet packet = NetworkUtil.readPacket(dis);

                if (packet == null) {
                    System.out.println("[ClientHandler] Client ngắt kết nối: " + clientAddress);
                    break;
                }

                dispatchPacket(packet);
            }

        } catch (IOException e) {
            System.err.println("[ClientHandler] Lỗi kết nối (" + clientAddress + "): " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void dispatchPacket(Packet packet) {
        if (packet.getType() == null) {
            return;
        }

        switch (packet.getType()) {
            case AUTH_LOGIN_REQ -> handleLogin(packet.getData());
            case AUTH_REGISTER_REQ -> handleRegister(packet.getData());
            case LEADERBOARD_REQ -> handleLeaderboard();
            case LOGOUT_REQ -> handleLogout();
            case LOBBY_GET_ROOMS_REQ -> handleGetRooms();
            case ROOM_CREATE_REQ -> handleCreateRoom();
            case ROOM_JOIN_REQ -> handleJoinRoom(packet.getData());
            case ROOM_READY_REQ -> handleReady(packet.getData());
            case ROOM_DURATION_REQ -> handleRoomDuration(packet.getData());
            case ROOM_START_REQ -> handleStartGame();
            case ROOM_LEAVE_REQ -> handleLeaveRoom();
            case TANK_PLAYER_INFO_REQ -> handleTankPlayerInfo();
            case PLAYER_INPUT -> handlePlayerInput(packet.getData());
            case PLAYER_SHOOT_REQ -> handlePlayerShoot(packet.getData());
            default -> System.out.println("[ClientHandler] Nhận packet chưa hỗ trợ: " + packet.getType());
        }
    }

    private void handleTankPlayerInfo() {
        if (currentUser == null || currentRoomId == -1) {
            return;
        }

        List<TankPlayerDTO> tankPlayers = new ArrayList<>();
        for (ClientHandler client : connectedClients) {
            if (client.currentRoomId == currentRoomId && client.currentUser != null && client.myTankId > 0) {
                tankPlayers.add(new TankPlayerDTO(client.myTankId, client.currentUser.getUsername()));
            }
        }

        String json = gson.toJson(tankPlayers);
        Packet response = new Packet(PacketType.TANK_PLAYER_INFO, json);

        try {
            if (dos != null) {
                NetworkUtil.sendPacket(dos, response);
            }
        } catch (Exception e) {
            System.err.println("[Game] Lỗi gửi Tank Player Info: " + e.getMessage());
        }
    }

    private void handleLogout() {
        try {
            if (currentRoomId != -1) {
                int roomId = currentRoomId;
                roomManager.leaveRoom(roomId, currentUser.getId());
                currentRoomId = -1;
                myTankId = -1;

                broadcastRoomStateForRoom(roomId);
                broadcastLobbyRooms();
            }

            currentUser = null;
            System.out.println("[ClientHandler] Client đăng xuất thành công.");
        } catch (Exception e) {
            System.err.println("[ClientHandler] Lỗi khi đăng xuất: " + e.getMessage());
        }
    }

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

    private void handleLeaderboard() {
        try {
            List<com.tank2d.common.dto.LeaderboardDTO> leaderboard = userDAO.getLeaderboard();
            Packet response = new Packet(PacketType.LEADERBOARD_RES, gson.toJson(leaderboard));
            NetworkUtil.sendPacket(dos, response);
        } catch (IOException e) {
            System.err.println("[Leaderboard] Lỗi gửi bảng xếp hạng: " + e.getMessage());
        }
    }

    private void handleGetRooms() {
        try {
            Packet response = new Packet(PacketType.LOBBY_ROOMS_RES, gson.toJson(roomManager.getAllRooms()));
            NetworkUtil.sendPacket(dos, response);
        } catch (IOException ignored) {}
    }

    private void handleCreateRoom() {
        try {
            if (currentUser == null) return;

            Room room = roomManager.createRoom(currentUser);
            currentRoomId = room.getRoomId();

            Packet response = new Packet(PacketType.ROOM_STATE_UPDATE, gson.toJson(roomManager.getRoomDTO(currentRoomId)));
            NetworkUtil.sendPacket(dos, response);

            // Cập nhật sảnh ngay khi tạo phòng mới
            broadcastLobbyRooms();
        } catch (IOException ignored) {}
    }

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
        } catch (Exception ignored) {}
    }

    private void handleReady(String rawJson) {
        try {
            if (currentUser == null || currentRoomId == -1) return;

            boolean ready = gson.fromJson(rawJson, Boolean.class);
            if (roomManager.setPlayerReady(currentRoomId, currentUser.getId(), ready)) {
                broadcastRoomState();
            }
        } catch (Exception ignored) {}
    }

    private void handleRoomDuration(String rawJson) {
        try {
            if (currentUser == null || currentRoomId == -1) return;

            Room room = roomManager.getRoom(currentRoomId);
            if (room == null || !room.isHost(currentUser.getId())) return;

            int duration = gson.fromJson(rawJson, Integer.class);
            if (duration != 45 && duration != 60 && duration != 90) return;

            if (roomManager.setRoomDuration(currentRoomId, duration)) {
                broadcastRoomState();
                broadcastLobbyRooms();
            }
        } catch (Exception e) {
            System.err.println("[Room] Lỗi xử lý ROOM_DURATION_REQ: " + e.getMessage());
        }
    }

    private void handleStartGame() {
        try {
            if (currentUser == null || currentRoomId == -1) return;

            Room room = roomManager.getRoom(currentRoomId);
            if (room == null || !room.isHost(currentUser.getId())) return;

            if (room.getCurrentPlayers() < 2 || !room.areAllPlayersReady()) {
                System.out.println("[Game] Chưa đủ điều kiện: Người = " + room.getCurrentPlayers() + ", Ready = " + room.areAllPlayersReady());
                return;
            }

            // Đổi trạng thái phòng thành Playing
            room.setStatus("Playing");

            // Bắn thông báo cho người trong phòng
            broadcastGameStart();

            // Cập nhật ngay danh sách sảnh thành "Đang chơi" để người ngoài sảnh thấy và không bấm vào
            broadcastLobbyRooms();

            int serverTickRate = ConfigLoader.getPhysicsStats().has("server_tick_rate")
                    ? ConfigLoader.getPhysicsStats().get("server_tick_rate").getAsInt() : 30;
            GameLoop gameLoop = new GameLoop(serverTickRate);

            try {
                gameLoop.setGameMap(MapLoader.loadFromFile("config/maps/map_default.json"));
            } catch (Exception e) {
                System.err.println("[Game] Lỗi nạp map: " + e.getMessage());
            }

            double matchDuration = room.getDuration();
            GameStateManager stateManager = new GameStateManager(gameLoop, userDAO, matchDAO, matchDuration);
            gameLoop.setStateManager(stateManager);
            gameLoop.setMapChangeListener(stateManager);
            gameLoop.setItemEventListener(stateManager);

            double speed = ConfigLoader.getTankSpeedPerSecond();
            double rotationSpeed = 120.0;

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

            List<TankPlayerDTO> tankPlayers = new ArrayList<>();
            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == currentRoomId && client.currentUser != null && client.myTankId > 0) {
                    tankPlayers.add(new TankPlayerDTO(client.myTankId, client.currentUser.getUsername()));
                }
            }

            String tankPlayersJson = gson.toJson(tankPlayers);
            Packet tankPlayerPacket = new Packet(PacketType.TANK_PLAYER_INFO, tankPlayersJson);

            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == currentRoomId && client.dos != null) {
                    try {
                        NetworkUtil.sendPacket(client.dos, tankPlayerPacket);
                    } catch (IOException ignored) {}
                }
            }

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

    private void handlePlayerShoot(String rawJson) {
        if (currentGameLoop == null || myTankId == -1) return;

        TankEntity tank = currentGameLoop.getTank(myTankId);
        if (tank == null || !tank.isAlive()) return;

        BulletEntity.BulletType requestedType = BulletEntity.BulletType.NORMAL;
        try {
            PlayerShootRequestDTO req = gson.fromJson(rawJson, PlayerShootRequestDTO.class);
            if (req != null && "ROCKET".equalsIgnoreCase(req.getBulletType())) {
                requestedType = BulletEntity.BulletType.ROCKET;
            }
        } catch (Exception ignored) {}

        currentGameLoop.handleShootRequest(tank, requestedType);
    }

    private void handlePlayerInput(String rawJson) {
        if (currentGameLoop == null || myTankId == -1) return;

        TankEntity tank = currentGameLoop.getTank(myTankId);
        if (tank == null || !tank.isAlive()) return;

        try {
            Type type = new com.google.gson.reflect.TypeToken<Map<String, Boolean>>() {}.getType();
            Map<String, Boolean> input = gson.fromJson(rawJson, type);

            if (input != null) {
                boolean up = input.getOrDefault("up", false);
                boolean down = input.getOrDefault("down", false);
                boolean left = input.getOrDefault("left", false);
                boolean right = input.getOrDefault("right", false);

                if (up && !down) {
                    tank.setMoveState(TankEntity.MoveState.FORWARD);
                } else if (down && !up) {
                    tank.setMoveState(TankEntity.MoveState.BACKWARD);
                } else {
                    tank.setMoveState(TankEntity.MoveState.NONE);
                }

                if (left && !right) {
                    tank.setRotateState(TankEntity.RotateState.LEFT);
                } else if (right && !left) {
                    tank.setRotateState(TankEntity.RotateState.RIGHT);
                } else {
                    tank.setRotateState(TankEntity.RotateState.NONE);
                }
            }
        } catch (Exception ignored) {}
    }

    private void handleLeaveRoom() {
        try {
            if (currentUser == null || currentRoomId == -1) return;

            if (currentGameStateManager != null && myTankId != -1) {
                currentGameStateManager.handlePlayerLeave(myTankId);
                if (currentGameLoop != null) {
                    currentGameLoop.getTanks().remove(myTankId);
                }
                this.currentGameLoop = null;
                this.currentGameStateManager = null;
                this.myTankId = -1;
            }

            int roomId = currentRoomId;
            int userId = currentUser.getId();

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

        // Đảm bảo sảnh luôn nhận danh sách phòng mới nhất khi có client ngắt kết nối đột ngột
        broadcastLobbyRooms();

        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    private void broadcastLobbyRooms() {
        try {
            Packet packet = new Packet(PacketType.ROOM_LIST_UPDATE, gson.toJson(roomManager.getAllRooms()));
            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == -1 && client.dos != null) {
                    NetworkUtil.sendPacket(client.dos, packet);
                }
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
            if (client.currentRoomId == roomId && client.dos != null) {
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
            if (client.currentRoomId == currentRoomId && client.dos != null) {
                try {
                    NetworkUtil.sendPacket(client.dos, packet);
                } catch (IOException ignored) {}
            }
        }
    }
}