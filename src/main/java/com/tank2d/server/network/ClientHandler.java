package com.tank2d.server.network;

import com.google.gson.Gson;
import com.tank2d.common.dto.LoginRequest;
import com.tank2d.common.dto.LoginResponse;
import com.tank2d.common.dto.RegisterResponse;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.NetworkUtil;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
import com.tank2d.server.dao.UserDAO;
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

/**
 * Worker xử lý kết nối riêng biệt cho từng Client qua TCP Socket.
 * Được quản lý bởi ExecutorService (Thread Pool) trong TankServer.
 */
public class ClientHandler implements Runnable {

    private static final Set<ClientHandler> connectedClients =
            ConcurrentHashMap.newKeySet();

    private final Socket socket;
    private final UserDAO userDAO;
    private final RoomManager roomManager;
    private final Gson gson;

    private DataInputStream dis;
    private DataOutputStream dos;
    private volatile boolean isRunning;

    private User currentUser;
    private int currentRoomId = -1;

    // Game
    private com.tank2d.server.game.GameLoop currentGameLoop;
    private int myTankId = -1;

    public ClientHandler(
            Socket socket,
            UserDAO userDAO,
            RoomManager roomManager) {

        this.socket = socket;
        this.userDAO = userDAO;
        this.roomManager = roomManager;
        this.gson = new Gson();
        this.isRunning = true;
    }

    @Override
    public void run() {

        String clientAddress =
                socket.getRemoteSocketAddress().toString();

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
                handlePlayerShoot();
                break;

            default:

                System.out.println(
                        "[ClientHandler] Nhận packet chưa hỗ trợ: "
                        + packet.getType()
                );

                break;
        }
    }

    // =========================================================
    // LOGIN
    // =========================================================

    private void handleLogin(String rawJson) {

        LoginResponse res;

        try {

            LoginRequest req =
                    gson.fromJson(rawJson, LoginRequest.class);

            System.out.println(
                    "[Auth] Yêu cầu đăng nhập từ tài khoản: "
                    + req.getUsername()
            );

            User user =
                    userDAO.login(
                            req.getUsername(),
                            req.getPassword()
                    );

            if (user != null) {

                this.currentUser = user;

                res = new LoginResponse(
                        true,
                        user.getId(),
                        user.getUsername(),
                        "Đăng nhập thành công!"
                );

                System.out.println(
                        "[Auth] Đăng nhập THÀNH CÔNG: "
                        + user.getUsername()
                        + " (ID: "
                        + user.getId()
                        + ")"
                );

            } else {

                res = new LoginResponse(
                        false,
                        -1,
                        "",
                        "Sai tên tài khoản hoặc mật khẩu!"
                );

                System.out.println(
                        "[Auth] Đăng nhập THẤT BẠI: "
                        + req.getUsername()
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "[Auth] Lỗi xử lý AUTH_LOGIN_REQ: "
                    + e.getMessage()
            );

            res = new LoginResponse(
                    false,
                    -1,
                    "",
                    "Lỗi định dạng dữ liệu đăng nhập!"
            );
        }

        try {

            Packet resPacket =
                    new Packet(
                            PacketType.AUTH_LOGIN_RES,
                            gson.toJson(res)
                    );

            NetworkUtil.sendPacket(
                    dos,
                    resPacket
            );

        } catch (IOException e) {

            System.err.println(
                    "[Auth] Lỗi gửi phản hồi Login: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // CREATE ROOM
    // =========================================================

    private void handleCreateRoom() {

        try {

            if (currentUser == null) {

                System.out.println(
                        "[Lobby] Client chưa đăng nhập, "
                        + "không thể tạo phòng."
                );

                return;
            }

            Room room =
                    roomManager.createRoom(currentUser);

            currentRoomId =
                    room.getRoomId();

            Packet response =
                    new Packet(
                            PacketType.ROOM_STATE_UPDATE,
                            gson.toJson(
                                    roomManager.getRoomDTO(
                                            currentRoomId
                                    )
                            )
                    );

            NetworkUtil.sendPacket(
                    dos,
                    response
            );

            broadcastLobbyRooms();

            System.out.println(
                    "[Lobby] User "
                    + currentUser.getUsername()
                    + " đã tạo phòng "
                    + room.getRoomName()
            );

        } catch (IOException e) {

            System.err.println(
                    "[Lobby] Lỗi tạo phòng: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // JOIN ROOM
    // =========================================================

    private void handleJoinRoom(String rawJson) {

        try {

            int roomId =
                    gson.fromJson(
                            rawJson,
                            Integer.class
                    );

            boolean success =
                    roomManager.joinRoom(
                            roomId,
                            currentUser
                    );

            if (success) {

                currentRoomId =
                        roomId;

                System.out.println(
                        "[Lobby] Client đã vào phòng ID: "
                        + roomId
                );

                // Đồng bộ trạng thái phòng
                broadcastRoomState();

            } else {

                System.out.println(
                        "[Lobby] Client không thể vào phòng ID: "
                        + roomId
                );

                Packet response =
                        new Packet(
                                PacketType.ROOM_STATE_UPDATE,
                                gson.toJson(
                                        roomManager.getRoomDTO(
                                                roomId
                                        )
                                )
                        );

                NetworkUtil.sendPacket(
                        dos,
                        response
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "[Lobby] Lỗi xử lý ROOM_JOIN_REQ: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // READY
    // =========================================================

    private void handleReady(String rawJson) {

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

            boolean ready =
                    gson.fromJson(
                            rawJson,
                            Boolean.class
                    );

            boolean success =
                    roomManager.setPlayerReady(
                            currentRoomId,
                            currentUser.getId(),
                            ready
                    );

            if (!success) {

                System.out.println(
                        "[Room] Không thể cập nhật Ready cho user "
                        + currentUser.getUsername()
                );

                return;
            }

            System.out.println(
                    "[Room] User "
                    + currentUser.getUsername()
                    + " -> Ready: "
                    + ready
            );

            // Cập nhật trạng thái cho tất cả Client trong phòng
            broadcastRoomState();

        } catch (Exception e) {

            System.err.println(
                    "[Room] Lỗi xử lý ROOM_READY_REQ: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // PLAYER INPUT
    // =========================================================

    private void handlePlayerInput(String rawJson) {

        if (currentGameLoop == null || myTankId == -1) {
            return;
        }

        TankEntity tank =
                currentGameLoop.getTank(myTankId);

        if (tank == null || !tank.isAlive()) {
            return;
        }

        try {

            java.lang.reflect.Type type =
                    new com.google.gson.reflect.TypeToken<
                            Map<String, Boolean>>() {
                    }.getType();

            Map<String, Boolean> input =
                    gson.fromJson(
                            rawJson,
                            type
                    );

            if (input != null) {

                boolean up =
                        input.getOrDefault(
                                "up",
                                false
                        );

                boolean down =
                        input.getOrDefault(
                                "down",
                                false
                        );

                boolean left =
                        input.getOrDefault(
                                "left",
                                false
                        );

                boolean right =
                        input.getOrDefault(
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

    private void handlePlayerShoot() {

        if (currentGameLoop == null || myTankId == -1) {
            return;
        }

        TankEntity tank =
                currentGameLoop.getTank(myTankId);

        if (tank != null && tank.isAlive()) {

            currentGameLoop.handleShootRequest(
                    tank
            );
        }
    }

    // =========================================================
    // BROADCAST LOBBY
    // =========================================================

    private void broadcastLobbyRooms() {

        try {

            Packet packet =
                    new Packet(
                            PacketType.LOBBY_ROOMS_RES,
                            gson.toJson(
                                    roomManager.getAllRooms()
                            )
                    );

            for (ClientHandler client :
                    connectedClients) {

                // Chỉ gửi cho Client đang ở Lobby
                if (client.currentRoomId == -1) {

                    NetworkUtil.sendPacket(
                            client.dos,
                            packet
                    );
                }
            }

        } catch (IOException e) {

            System.err.println(
                    "[Lobby] Lỗi broadcast danh sách phòng: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // BROADCAST ROOM STATE
    // =========================================================

    private void broadcastRoomStateForRoom(int roomId) {

        Room room =
                roomManager.getRoom(roomId);

        if (room == null) {
            return;
        }

        Packet packet =
                new Packet(
                        PacketType.ROOM_STATE_UPDATE,
                        gson.toJson(
                                roomManager.getRoomDTO(
                                        roomId
                                )
                        )
                );

        for (ClientHandler client :
                connectedClients) {

            if (client.currentRoomId == roomId) {

                try {

                    NetworkUtil.sendPacket(
                            client.dos,
                            packet
                    );

                } catch (IOException e) {

                    System.err.println(
                            "[Room] Không thể gửi trạng thái phòng: "
                            + e.getMessage()
                    );
                }
            }
        }
    }

    private void broadcastRoomState() {

        if (currentRoomId == -1) {
            return;
        }

        Room room =
                roomManager.getRoom(
                        currentRoomId
                );

        if (room == null) {
            return;
        }

        Packet packet =
                new Packet(
                        PacketType.ROOM_STATE_UPDATE,
                        gson.toJson(
                                roomManager.getRoomDTO(
                                        currentRoomId
                                )
                        )
                );

        for (ClientHandler client :
                connectedClients) {

            if (client.currentRoomId ==
                    currentRoomId) {

                try {

                    NetworkUtil.sendPacket(
                            client.dos,
                            packet
                    );

                    System.out.println(
                            "[Room] Đã broadcast ROOM_STATE_UPDATE tới Client"
                    );

                } catch (IOException e) {

                    System.err.println(
                            "[Room] Không thể gửi trạng thái phòng: "
                            + e.getMessage()
                    );
                }
            }
        }
    }

    // =========================================================
    // GAME START NOTIFY
    // =========================================================

    private void broadcastGameStart() {

        if (currentRoomId == -1) {
            return;
        }

        // JSON dạng: {"roomId": 1}
        Map<String, Object> dataMap =
                new HashMap<>();

        dataMap.put(
                "roomId",
                currentRoomId
        );

        Packet packet =
                new Packet(
                        PacketType.GAME_START_NOTIFY,
                        gson.toJson(dataMap)
                );

        for (ClientHandler client :
                connectedClients) {

            if (client.currentRoomId ==
                    currentRoomId) {

                try {

                    NetworkUtil.sendPacket(
                            client.dos,
                            packet
                    );

                    System.out.println(
                            "[Game] Đã gửi GAME_START_NOTIFY tới Client"
                    );

                } catch (IOException e) {

                    System.err.println(
                            "[Game] Không thể gửi GAME_START_NOTIFY tới Client: "
                            + e.getMessage()
                    );
                }
            }
        }
    }

    // =========================================================
    // START GAME
    // =========================================================

    private void handleStartGame() {

        try {

            if (currentUser == null) {

                System.out.println(
                        "[Game] Client chưa đăng nhập."
                );

                return;
            }

            if (currentRoomId == -1) {

                System.out.println(
                        "[Game] Client chưa ở trong phòng."
                );

                return;
            }

            Room room =
                    roomManager.getRoom(
                            currentRoomId
                    );

            if (room == null) {

                System.out.println(
                        "[Game] Không tìm thấy phòng."
                );

                return;
            }

            // Chỉ Host mới được bắt đầu
            if (!room.isHost(
                    currentUser.getId())) {

                System.out.println(
                        "[Game] User "
                        + currentUser.getUsername()
                        + " không phải Host, không được Start."
                );

                return;
            }

            // Hiện tại giữ nguyên logic cũ:
            // phải đủ số người tối đa mới Start.
            if (room.getCurrentPlayers()
                    < room.getMaxPlayers()) {

                System.out.println(
                        "[Game] Chưa đủ người chơi."
                );

                return;
            }

            // Tất cả người chơi phải Ready
            if (!room.areAllPlayersReady()) {

                System.out.println(
                        "[Game] Chưa phải tất cả người chơi Ready."
                );

                return;
            }

            System.out.println(
                    "[Game] Host "
                    + currentUser.getUsername()
                    + " bắt đầu trận!"
            );

            // Thông báo cho Client chuyển sang Game
            broadcastGameStart();

            // =================================================
            // TẠO GAME LOOP
            // =================================================

            com.tank2d.server.game.GameLoop gameLoop =
                    new com.tank2d.server.game.GameLoop(60);

            com.tank2d.server.game.GameStateManager stateManager =
                    new com.tank2d.server.game.GameStateManager(
                            gameLoop,
                            userDAO,
                            180.0
                    );

            gameLoop.setStateManager(
                    stateManager
            );

            int tankIndex = 1;

            // Tạo Tank cho từng Client trong Room
            for (ClientHandler client :
                    connectedClients) {

                if (client.currentRoomId ==
                        currentRoomId) {

                    double startX =
                            (tankIndex == 1)
                            ? 100.0
                            : 650.0;

                    double startY =
                            (tankIndex == 1)
                            ? 100.0
                            : 450.0;

                    double startAngle =
                            (tankIndex == 1)
                            ? 0.0
                            : 180.0;

                    double speed = 150.0;

                    double rotationSpeed = 120.0;

                    TankEntity tank =
                            new TankEntity(
                                    tankIndex,
                                    startX,
                                    startY,
                                    startAngle,
                                    speed,
                                    rotationSpeed
                            );

                    tank.setHp(3);

                    gameLoop.addTank(
                            tank
                    );

                    stateManager.registerPlayer(
                            tankIndex,
                            client.currentUser.getId(),
                            client.dos
                    );

                    client.myTankId =
                            tankIndex;

                    client.currentGameLoop =
                            gameLoop;

                    tankIndex++;
                }
            }

            // =================================================
            // SNAPSHOT → CLIENT
            // =================================================

            java.util.concurrent.ExecutorService
                    networkBroadcastPool =
                    java.util.concurrent.Executors
                            .newSingleThreadExecutor();

            int finalRoomId =
                    currentRoomId;

            gameLoop.setSnapshotListener(
                    snapshot -> {

                        // Đẩy việc gửi Snapshot sang thread riêng
                        networkBroadcastPool.submit(
                                () -> {

                                    try {

                                        String json =
                                                gson.toJson(
                                                        snapshot
                                                );

                                        Packet snapshotPacket =
                                                new Packet(
                                                        PacketType.GAME_SNAPSHOT,
                                                        json
                                                );

                                        for (ClientHandler client :
                                                connectedClients) {

                                            if (client.currentRoomId ==
                                                    finalRoomId
                                                    && client.dos != null) {

                                                NetworkUtil.sendPacket(
                                                        client.dos,
                                                        snapshotPacket
                                                );
                                            }
                                        }

                                    } catch (Exception ignored) {
                                    }
                                }
                        );
                    }
            );

            // =================================================
            // START GAME LOOP THREAD
            // =================================================

            Thread loopThread =
                    new Thread(
                            gameLoop
                    );

            loopThread.setName(
                    "GameLoop-Room-"
                    + finalRoomId
            );

            loopThread.start();

        } catch (Exception e) {

            System.err.println(
                    "[Game] Lỗi xử lý ROOM_START_REQ: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // REGISTER
    // =========================================================

    private void handleRegister(String rawJson) {

        RegisterResponse res;

        try {

            LoginRequest req =
                    gson.fromJson(
                            rawJson,
                            LoginRequest.class
                    );

            System.out.println(
                    "[Auth] Yêu cầu đăng ký tài khoản: "
                    + req.getUsername()
            );

            boolean isSuccess =
                    userDAO.register(
                            req.getUsername(),
                            req.getPassword()
                    );

            if (isSuccess) {

                res =
                        new RegisterResponse(
                                true,
                                "Đăng ký tài khoản thành công!"
                        );

                System.out.println(
                        "[Auth] Đăng ký THÀNH CÔNG: "
                        + req.getUsername()
                );

            } else {

                res =
                        new RegisterResponse(
                                false,
                                "Đăng ký thất bại! "
                                + "Tên tài khoản có thể đã tồn tại."
                        );

                System.out.println(
                        "[Auth] Đăng ký THẤT BẠI: "
                        + req.getUsername()
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "[Auth] Lỗi xử lý AUTH_REGISTER_REQ: "
                    + e.getMessage()
            );

            res =
                    new RegisterResponse(
                            false,
                            "Lỗi định dạng dữ liệu đăng ký!"
                    );
        }

        try {

            Packet resPacket =
                    new Packet(
                            PacketType.AUTH_REGISTER_RES,
                            gson.toJson(res)
                    );

            NetworkUtil.sendPacket(
                    dos,
                    resPacket
            );

        } catch (IOException e) {

            System.err.println(
                    "[Auth] Lỗi gửi phản hồi Register: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // GET ROOMS
    // =========================================================

    private void handleGetRooms() {

        try {

            String roomsJson =
                    gson.toJson(
                            roomManager.getAllRooms()
                    );

            Packet response =
                    new Packet(
                            PacketType.LOBBY_ROOMS_RES,
                            roomsJson
                    );

            NetworkUtil.sendPacket(
                    dos,
                    response
            );

            System.out.println(
                    "[Lobby] Đã gửi danh sách phòng cho Client."
            );

        } catch (IOException e) {

            System.err.println(
                    "[Lobby] Lỗi gửi danh sách phòng: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // LEAVE ROOM
    // =========================================================

    private void handleLeaveRoom() {

        try {

            if (currentUser == null ||
                    currentRoomId == -1) {

                return;
            }

            int roomId =
                    currentRoomId;

            int userId =
                    currentUser.getId();

            boolean success =
                    roomManager.leaveRoom(
                            roomId,
                            userId
                    );

            if (success) {

                System.out.println(
                        "[Room] User "
                        + currentUser.getUsername()
                        + " đã chủ động rời phòng "
                        + roomId
                );

                currentRoomId = -1;

                // Cập nhật cho Client còn lại
                broadcastRoomStateForRoom(
                        roomId
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "[Room] Lỗi xử lý ROOM_LEAVE_REQ: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // CLOSE CONNECTION
    // =========================================================

    public void closeConnection() {

        isRunning = false;

        connectedClients.remove(
                this
        );

        // Nếu đang ở trong phòng thì xóa khỏi phòng
        if (currentUser != null &&
                currentRoomId != -1) {

            roomManager.leaveRoom(
                    currentRoomId,
                    currentUser.getId()
            );

            System.out.println(
                    "[Lobby] User "
                    + currentUser.getUsername()
                    + " đã rời phòng ID: "
                    + currentRoomId
            );

            currentRoomId = -1;
        }

        try {

            if (dis != null) {
                dis.close();
            }

            if (dos != null) {
                dos.close();
            }

            if (socket != null &&
                    !socket.isClosed()) {

                socket.close();
            }

        } catch (IOException e) {

            System.err.println(
                    "[ClientHandler] Lỗi đóng kết nối: "
                    + e.getMessage()
            );
        }

        System.out.println(
                "[ClientHandler] Đã đóng tài nguyên kết nối an toàn."
        );
    }
}