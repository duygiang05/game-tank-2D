package com.tank2d.server.network;

import com.google.gson.Gson;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.LoginRequest;
import com.tank2d.common.dto.LoginResponse;
import com.tank2d.common.dto.RegisterResponse;
import com.tank2d.common.dto.game.PlayerShootRequestDTO;
import com.tank2d.common.dto.game.ReconnectPromptDTO;
import com.tank2d.common.dto.game.ReconnectResponseDTO;
import com.tank2d.common.dto.game.TankPlayerDTO;
import com.tank2d.common.exception.ErrorCode;
import com.tank2d.common.exception.GameNetworkException;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private static final Set<ClientHandler> connectedClients = ConcurrentHashMap.newKeySet();
    private static final ExecutorService networkBroadcastPool = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    private static final Type INPUT_MAP_TYPE = new com.google.gson.reflect.TypeToken<Map<String, Boolean>>() {}.getType();

    public static void shutdownBroadcastPool() {
        networkBroadcastPool.shutdown();
    }

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
        LOGGER.info("[ClientHandler] Khởi tạo phiên làm việc với Client: " + clientAddress);

        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            connectedClients.add(this);

            while (isRunning && !socket.isClosed()) {
                Packet packet = NetworkUtil.readPacket(dis);

                if (packet == null) {
                    LOGGER.info("[ClientHandler] Client ngắt kết nối: " + clientAddress);
                    break;
                }

                dispatchPacket(packet);
            }

        } catch (IOException e) {
            if (e instanceof java.net.SocketException || e instanceof java.io.EOFException) {
                LOGGER.info("[ClientHandler] Client ngắt kết nối (" + clientAddress + "): " + e.getMessage());
            } else {
                LOGGER.log(Level.WARNING, "[ClientHandler] Lỗi kết nối (" + clientAddress + ")", e);
            }
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
            case MATCH_HISTORY_REQ -> handleMatchHistory();
            case MATCH_DETAIL_REQ -> handleMatchDetail(packet.getData());
            case LOGOUT_REQ -> handleLogout();
            case LOBBY_GET_ROOMS_REQ -> handleGetRooms();
            case ROOM_CREATE_REQ -> handleCreateRoom();
            case ROOM_JOIN_REQ -> handleJoinRoom(packet.getData());
            case ROOM_READY_REQ -> handleReady(packet.getData());
            case ROOM_DURATION_REQ -> handleRoomDuration(packet.getData());
            case ROOM_START_REQ -> handleStartGame();
            case ROOM_LEAVE_REQ -> handleLeaveRoom();
            case GAME_RECONNECT_REQ -> handleReconnectGame();
            case TANK_PLAYER_INFO_REQ -> handleTankPlayerInfo();
            case PLAYER_INPUT -> handlePlayerInput(packet.getData());
            case PLAYER_SHOOT_REQ -> handlePlayerShoot(packet.getData());
            default -> LOGGER.info("[ClientHandler] Nhận packet chưa hỗ trợ: " + packet.getType());
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
            LOGGER.log(Level.SEVERE, "[Game] Lỗi gửi Tank Player Info", e);
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
            LOGGER.info("[ClientHandler] Client đăng xuất thành công.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[ClientHandler] Lỗi khi đăng xuất", e);
        }
    }

    /**
     * Xử lý yêu cầu đăng nhập từ client.
     *
     * @param rawJson chuỗi JSON gói tin đăng nhập
     */
    private void handleLogin(String rawJson) {
        LoginResponse res;
        try {
            LoginRequest req = gson.fromJson(rawJson, LoginRequest.class);
            User user = userDAO.login(req.getUsername(), req.getPassword());

            if (user != null) {
                boolean isAlreadyLoggedIn = false;
                for (ClientHandler client : connectedClients) {
                    if (client != this && client.currentUser != null && client.currentUser.getId() == user.getId()) {
                        isAlreadyLoggedIn = true;
                        break;
                    }
                }

                if (isAlreadyLoggedIn) {
                    LOGGER.info("[Auth] Chặn đăng nhập trùng lặp cho User: " + user.getUsername());
                    res = new LoginResponse(false, -1, "", ErrorCode.AUTH_USER_ALREADY_LOGGED_IN.getDefaultMessage());
                } else {
                    this.currentUser = user;
                    res = new LoginResponse(true, user.getId(), user.getUsername(), "Đăng nhập thành công!");
                }
            } else {
                res = new LoginResponse(false, -1, "", ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage());
            }
        } catch (GameNetworkException e) {
            res = new LoginResponse(false, -1, "", e.getMessage());
        } catch (Exception e) {
            res = new LoginResponse(false, -1, "", ErrorCode.AUTH_INVALID_INPUT.getDefaultMessage());
        }

        try {
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_LOGIN_RES, gson.toJson(res)));

            if (currentUser != null) {
                if (!checkUserPenalized()) {
                    Room playingRoom = roomManager.getPlayingRoomByUserId(currentUser.getId());
                    if (playingRoom != null && playingRoom.getGameStateManager() != null && !playingRoom.getGameStateManager().isGameOver()) {
                        double remain = playingRoom.getGameStateManager().getMatchRemainingTime();
                        ReconnectPromptDTO promptDTO = new ReconnectPromptDTO(playingRoom.getRoomId(), playingRoom.getRoomName(), remain);
                        NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_RECONNECT_PROMPT, gson.toJson(promptDTO)));
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    /**
     * Kiểm tra xem người dùng hiện tại có đang chịu án phạt do thoát trận trước đó hay không.
     *
     * @return {@code true} nếu người dùng vẫn bị phạt
     * @throws IOException khi gửi packet thất bại
     */
    private boolean checkUserPenalized() throws IOException {
        if (currentUser == null) return false;
        if (roomManager.isPenalized(currentUser.getId())) {
            Integer penRoomId = roomManager.getPenalizedRoomId(currentUser.getId());
            Room penRoom = (penRoomId != null) ? roomManager.getRoom(penRoomId) : null;
            if (penRoom != null && "Playing".equalsIgnoreCase(penRoom.getStatus())) {
                NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_PENALTY_NOTIFY,
                        "PENALTY_ACTIVE:" + penRoomId));
                return true;
            } else {
                roomManager.removePenalty(currentUser.getId());
                NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_PENALTY_NOTIFY, "PENALTY_LIFTED"));
                return false;
            }
        }
        return false;
    }

    private void handleRegister(String rawJson) {
        RegisterResponse res;
        try {
            LoginRequest req = gson.fromJson(rawJson, LoginRequest.class);
            boolean isSuccess = userDAO.register(req.getUsername(), req.getPassword());
            res = isSuccess ? new RegisterResponse(true, "Đăng ký thành công!")
                    : new RegisterResponse(false, ErrorCode.AUTH_USERNAME_TAKEN.getDefaultMessage());
        } catch (GameNetworkException e) {
            res = new RegisterResponse(false, e.getMessage());
        } catch (Exception e) {
            res = new RegisterResponse(false, ErrorCode.AUTH_INVALID_INPUT.getDefaultMessage());
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
            LOGGER.log(Level.SEVERE, "[Leaderboard] Lỗi gửi bảng xếp hạng", e);
        }
    }

    private void handleMatchHistory() {
        try {
            if (currentUser == null) return;
            List<com.tank2d.common.dto.UserMatchHistoryDTO> history = matchDAO.getMatchHistoryByUserId(currentUser.getId());
            Packet response = new Packet(PacketType.MATCH_HISTORY_RES, gson.toJson(history));
            NetworkUtil.sendPacket(dos, response);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi gửi lịch sử đấu", e);
        }
    }

    private void handleMatchDetail(String rawJson) {
        try {
            int matchId = gson.fromJson(rawJson, Integer.class);
            com.tank2d.common.dto.MatchDetailDTO detail = matchDAO.getMatchDetail(matchId);
            if (detail != null) {
                Packet response = new Packet(PacketType.MATCH_DETAIL_RES, gson.toJson(detail));
                NetworkUtil.sendPacket(dos, response);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[MatchHistory] Lỗi gửi chi tiết trận đấu", e);
        }
    }

    private void handleGetRooms() {
        try {
            Packet response = new Packet(PacketType.LOBBY_ROOMS_RES, gson.toJson(roomManager.getAllRooms()));
            NetworkUtil.sendPacket(dos, response);

            if (currentUser != null) {
                if (!checkUserPenalized()) {
                    Room playingRoom = roomManager.getPlayingRoomByUserId(currentUser.getId());
                    if (playingRoom != null && playingRoom.getGameStateManager() != null && !playingRoom.getGameStateManager().isGameOver()) {
                        double remain = playingRoom.getGameStateManager().getMatchRemainingTime();
                        ReconnectPromptDTO promptDTO = new ReconnectPromptDTO(playingRoom.getRoomId(), playingRoom.getRoomName(), remain);
                        NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_RECONNECT_PROMPT, gson.toJson(promptDTO)));
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private void handleCreateRoom() {
        try {
            if (currentUser == null) return;

            if (checkUserPenalized()) {
                return;
            }

            Room playingRoom = roomManager.getPlayingRoomByUserId(currentUser.getId());
            if (playingRoom != null && playingRoom.getGameStateManager() != null && !playingRoom.getGameStateManager().isGameOver()) {
                double remain = playingRoom.getGameStateManager().getMatchRemainingTime();
                NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_RECONNECT_PROMPT,
                        gson.toJson(new ReconnectPromptDTO(playingRoom.getRoomId(), playingRoom.getRoomName(), remain))));
                return;
            }

            Room room = roomManager.createRoom(currentUser);
            currentRoomId = room.getRoomId();

            Packet response = new Packet(PacketType.ROOM_STATE_UPDATE, gson.toJson(roomManager.getRoomDTO(currentRoomId)));
            NetworkUtil.sendPacket(dos, response);

            broadcastLobbyRooms();
        } catch (IOException ignored) {}
    }

    private void handleJoinRoom(String rawJson) {
        try {
            if (currentUser == null) return;

            if (checkUserPenalized()) {
                return;
            }

            Room playingRoom = roomManager.getPlayingRoomByUserId(currentUser.getId());
            if (playingRoom != null && playingRoom.getGameStateManager() != null && !playingRoom.getGameStateManager().isGameOver()) {
                double remain = playingRoom.getGameStateManager().getMatchRemainingTime();
                NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_RECONNECT_PROMPT,
                        gson.toJson(new ReconnectPromptDTO(playingRoom.getRoomId(), playingRoom.getRoomName(), remain))));
                return;
            }

            int roomId = gson.fromJson(rawJson, Integer.class);
            boolean success = roomManager.joinRoom(roomId, currentUser);

            if (success) {
                currentRoomId = roomId;
                broadcastRoomState();
                broadcastLobbyRooms();
            } else {
                LOGGER.info("[Room] Từ chối " + currentUser.getUsername() + " vào phòng " + roomId);
                Packet packet = new Packet(PacketType.ROOM_LIST_UPDATE, gson.toJson(roomManager.getAllRooms()));
                NetworkUtil.sendPacket(dos, packet);
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

    private void handlePlayerShoot(String rawJson) {
        if (currentGameLoop == null || myTankId == -1) {
            return;
        }
        TankEntity tank = currentGameLoop.getTank(myTankId);
        if (tank == null || !tank.isAlive()) {
            return;
        }

        BulletEntity.BulletType requestedType = BulletEntity.BulletType.NORMAL;
        try {
            PlayerShootRequestDTO req = gson.fromJson(rawJson, PlayerShootRequestDTO.class);
            if (req != null && "ROCKET".equalsIgnoreCase(req.getBulletType())) {
                requestedType = BulletEntity.BulletType.ROCKET;
            }
        } catch (Exception ignored) {}

        currentGameLoop.handleShootRequest(tank, requestedType);
    }

    /**
     * Bắt đầu trận đấu: khởi tạo GameLoop, nạp Map, gán xe tăng và phân phối vị trí xuất phát.
     */
    private void handleStartGame() {
        try {
            if (currentUser == null || currentRoomId == -1) return;

            Room room = roomManager.getRoom(currentRoomId);
            if (room == null || !room.isHost(currentUser.getId())) return;

            if (room.getCurrentPlayers() < 2 || !room.areAllPlayersReady()) {
                LOGGER.info("[Game] Chưa đủ điều kiện: Người = " + room.getCurrentPlayers() + ", Ready = " + room.areAllPlayersReady());
                return;
            }

            room.setStatus("Playing");
            broadcastGameStart();
            broadcastLobbyRooms();

            int serverTickRate = ConfigLoader.getPhysicsStats().has("server_tick_rate")
                    ? ConfigLoader.getPhysicsStats().get("server_tick_rate").getAsInt() : 30;
            GameLoop gameLoop = new GameLoop(serverTickRate);

            try {
                gameLoop.setGameMap(MapLoader.loadFromFile("config/maps/map_default.json"));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[Game] Lỗi nạp map", e);
            }

            double matchDuration = room.getDuration();
            GameStateManager stateManager = new GameStateManager(gameLoop, userDAO, matchDAO, matchDuration);
            gameLoop.setStateManager(stateManager);
            gameLoop.setMapChangeListener(stateManager);
            gameLoop.setItemEventListener(stateManager);

            room.setGameLoop(gameLoop);
            room.setGameStateManager(stateManager);
            room.getUserTankMappings().clear();
            
            int targetRoomId = currentRoomId;
            stateManager.setOnGameOverCallback(() -> {
                liftPenaltiesForRoom(targetRoomId);
                roomManager.resetRoomAfterMatch(targetRoomId);
                broadcastRoomStateForRoom(targetRoomId);
                broadcastLobbyRooms();
            });

            double speed = ConfigLoader.getTankSpeedPerSecond();
            double rotationSpeed = 120.0;

            int tankIndex = 1;
            for (ClientHandler client : connectedClients) {
                if (client.currentRoomId == currentRoomId && client.currentUser != null) {
                    double startX = ConfigLoader.getSpawnX(tankIndex, 15.0);
                    double startY = ConfigLoader.getSpawnY(tankIndex, 15.0);
                    double startAngle = ConfigLoader.getSpawnAngle(tankIndex, 0.0);

                    TankEntity tank = new TankEntity(tankIndex, startX, startY, startAngle, speed, rotationSpeed);
                    tank.setHp(ConfigLoader.getMaxHp());
                    gameLoop.addTank(tank);
                    stateManager.registerPlayer(tankIndex, client.currentUser.getId(), client.dos);

                    client.myTankId = tankIndex;
                    client.currentGameLoop = gameLoop;
                    client.currentGameStateManager = stateManager;

                    room.getUserTankMappings().put(client.currentUser.getId(), tankIndex);

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
            LOGGER.log(Level.SEVERE, "[Game] Lỗi xử lý ROOM_START_REQ", e);
        }
    }

    /**
     * Xử lý yêu cầu thay đổi thời lượng trận đấu từ chủ phòng.
     *
     * @param rawJson chuỗi JSON chứa giá trị thời lượng mới (giây)
     */
    private void handleRoomDuration(String rawJson) {
        try {
            if (currentUser == null) {
                LOGGER.info("[Room] Client chưa đăng nhập.");
                return;
            }

            if (currentRoomId == -1) {
                LOGGER.info("[Room] Client chưa ở trong phòng.");
                return;
            }

            Room room = roomManager.getRoom(currentRoomId);
            if (room == null) {
                LOGGER.info("[Room] Không tìm thấy phòng.");
                return;
            }

            if (!room.isHost(currentUser.getId())) {
                LOGGER.info("[Room] User " + currentUser.getUsername() + " không phải Host.");
                return;
            }

            int duration = gson.fromJson(rawJson, Integer.class);

            if (duration != 45 && duration != 60 && duration != 90 && duration != 180) {
                LOGGER.info("[Room] Thời lượng không hợp lệ: " + duration);
                return;
            }

            boolean success = roomManager.setRoomDuration(currentRoomId, duration);

            if (success) {
                LOGGER.info("[Room] Host " + currentUser.getUsername() + " chọn " + duration + " giây.");
                broadcastRoomState();
                broadcastLobbyRooms();
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Room] Lỗi xử lý ROOM_DURATION_REQ", e);
        }
    }

    /**
     * Xử lý gói tin phím bấm điều khiển di chuyển và xoay nòng xe từ người chơi.
     *
     * @param rawJson chuỗi JSON mô tả trạng thái bàn phím
     */
    private void handlePlayerInput(String rawJson) {
        if (currentGameLoop == null || myTankId == -1) return;

        TankEntity tank = currentGameLoop.getTank(myTankId);
        if (tank == null || !tank.isAlive()) return;

        try {
            Map<String, Boolean> input = gson.fromJson(rawJson, INPUT_MAP_TYPE);

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

    /**
     * Hủy bỏ án phạt cho tất cả người chơi liên quan đến phòng đấu đã chỉ định.
     *
     * @param roomId mã số phòng
     */
    private void liftPenaltiesForRoom(int roomId) {
        List<Integer> penalizedUsers = roomManager.getPenalizedUserIdsForRoom(roomId);
        roomManager.removePenaltiesForRoom(roomId);
        for (ClientHandler client : connectedClients) {
            if (client.currentUser != null && penalizedUsers.contains(client.currentUser.getId())) {
                try {
                    NetworkUtil.sendPacket(client.dos, new Packet(PacketType.GAME_PENALTY_NOTIFY, "PENALTY_LIFTED"));
                    LOGGER.info("[Penalty] Đã gửi thông báo gỡ phạt cho user " + client.currentUser.getUsername());
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Xử lý yêu cầu thoát phòng từ người chơi (cả trong lúc chờ và giữa trận đấu).
     */
    private void handleLeaveRoom() {
        try {
            if (currentUser == null || currentRoomId == -1) return;

            if (currentGameStateManager != null && myTankId != -1) {
                int leaverUserId = currentUser.getId();
                int leaverRoomId = currentRoomId;

                currentGameStateManager.handlePlayerSurrender(myTankId);

                this.currentGameLoop = null;
                this.currentGameStateManager = null;
                this.myTankId = -1;

                if (roomManager.leaveRoom(leaverRoomId, leaverUserId)) {
                    LOGGER.info("[Room] User " + currentUser.getUsername() + " đã chủ động thoát trận và rời phòng " + leaverRoomId);
                    currentRoomId = -1;
                    broadcastRoomStateForRoom(leaverRoomId);
                    broadcastLobbyRooms();
                }

                Room targetRoom = roomManager.getRoom(leaverRoomId);
                if (targetRoom != null && "Playing".equalsIgnoreCase(targetRoom.getStatus())) {
                    roomManager.addPenalty(leaverUserId, leaverRoomId);
                    try {
                        NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_PENALTY_NOTIFY,
                                "PENALTY_ACTIVE:" + leaverRoomId));
                    } catch (IOException ignored) {}
                } else {
                    roomManager.removePenalty(leaverUserId);
                    try {
                        NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_PENALTY_NOTIFY, "PENALTY_LIFTED"));
                    } catch (IOException ignored) {}
                }

                return;
            }

            int roomId = currentRoomId;
            int userId = currentUser.getId();

            if (roomManager.leaveRoom(roomId, userId)) {
                LOGGER.info("[Room] User " + currentUser.getUsername() + " đã thoát phòng " + roomId);
                currentRoomId = -1;
                if (roomManager.getRoom(roomId) == null) {
                    liftPenaltiesForRoom(roomId);
                }
                broadcastRoomStateForRoom(roomId);
                broadcastLobbyRooms();
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Room] Lỗi xử lý ROOM_LEAVE_REQ", e);
        }
    }

    /**
     * Xử lý yêu cầu tái kết nối (Reconnect) vào trận đấu đang diễn ra.
     */
    private void handleReconnectGame() {
        try {
            if (currentUser == null) return;

            Room playingRoom = roomManager.getPlayingRoomByUserId(currentUser.getId());
            if (playingRoom == null || playingRoom.getGameStateManager() == null || playingRoom.getGameStateManager().isGameOver()) {
                NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_PENALTY_NOTIFY, "Trận đấu đã kết thúc!"));
                return;
            }

            Integer tankId = playingRoom.getUserTankMappings().get(currentUser.getId());
            if (tankId == null) {
                LOGGER.warning("[Reconnect] Không tìm thấy tankId cho user " + currentUser.getUsername());
                return;
            }

            this.currentRoomId = playingRoom.getRoomId();
            this.myTankId = tankId;
            this.currentGameLoop = playingRoom.getGameLoop();
            this.currentGameStateManager = playingRoom.getGameStateManager();

            boolean reconnected = currentGameStateManager.reconnectPlayer(tankId, this.dos);
            if (reconnected) {
                LOGGER.info("[Reconnect] User " + currentUser.getUsername() + " đã reconnect thành công vào xe Tank " + tankId + " tại phòng " + currentRoomId);

                ReconnectResponseDTO resDTO = new ReconnectResponseDTO(
                        roomManager.getRoomDTO(currentRoomId),
                        tankId,
                        currentGameStateManager.getMatchRemainingTime()
                );
                NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_RECONNECT_RES, gson.toJson(resDTO)));

                List<TankPlayerDTO> tankPlayers = new ArrayList<>();
                for (Map.Entry<Integer, Integer> entry : playingRoom.getUserTankMappings().entrySet()) {
                    int uId = entry.getKey();
                    int tId = entry.getValue();
                    for (User u : playingRoom.getPlayers()) {
                        if (u.getId() == uId) {
                            tankPlayers.add(new TankPlayerDTO(tId, u.getUsername()));
                            break;
                        }
                    }
                }
                NetworkUtil.sendPacket(dos, new Packet(PacketType.TANK_PLAYER_INFO, gson.toJson(tankPlayers)));
            } else {
                NetworkUtil.sendPacket(dos, new Packet(PacketType.GAME_PENALTY_NOTIFY, "Không thể kết nối lại vào trận đấu!"));
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Reconnect] Lỗi xử lý GAME_RECONNECT_REQ", e);
        }
    }

    /**
     * Đóng kết nối client an toàn, xử lý AFK nếu đang trong trận hoặc rời phòng nếu đang ở phòng chờ.
     */
    public void closeConnection() {
        isRunning = false;
        connectedClients.remove(this);

        if (currentUser != null) {
            if (currentGameStateManager != null && myTankId != -1) {
                LOGGER.info("[ClientHandler] User " + currentUser.getUsername() + " (Tank " + myTankId + ") ngắt kết nối đột ngột trong trận. Kích hoạt chế độ AFK.");
                currentGameStateManager.handlePlayerDisconnected(myTankId);
                this.currentGameStateManager = null;
                this.currentGameLoop = null;
                this.currentRoomId = -1;
                this.myTankId = -1;
            } else if (currentRoomId != -1) {
                handleLeaveRoom();
            }
            this.currentUser = null;
        }

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
