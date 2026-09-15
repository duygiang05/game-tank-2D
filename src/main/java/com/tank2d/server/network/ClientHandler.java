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
import com.tank2d.server.room.Room;
import com.tank2d.server.room.RoomManager;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Worker xử lý kết nối riêng biệt cho từng Client qua TCP Socket. Được quản lý
 * bởi ExecutorService (Thread Pool) trong TankServer.
 */
public class ClientHandler implements Runnable {

    private static final Set<ClientHandler> connectedClients
            = ConcurrentHashMap.newKeySet();

    private final Socket socket;
    private final UserDAO userDAO;
    private final RoomManager roomManager;
    private final Gson gson;
    private DataInputStream dis;
    private DataOutputStream dos;
    private volatile boolean isRunning;
    private User currentUser; // Lưu thông tin người chơi sau khi xác thực thành công
    private int currentRoomId = -1;

    public ClientHandler(Socket socket, UserDAO userDAO,
            RoomManager roomManager) {
        this.socket = socket;
        this.userDAO = userDAO;
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

            // Vòng lặp liên tục đọc gói tin theo cơ chế Length-Prefix chống dính/vỡ gói
            while (isRunning && !socket.isClosed()) {
                Packet packet = NetworkUtil.readPacket(dis);
                if (packet == null) {
                    System.out.println("[ClientHandler] Client ngắt kết nối: " + clientAddress);
                    break;
                }

                // Điều phối xử lý theo loại gói tin
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
            case ROOM_LEAVE_REQ:
                handleLeaveRoom();
                break;

            default:
                System.out.println(
                        "[ClientHandler] Nhận packet chưa hỗ trợ: "
                        + packet.getType()
                );
                break;
        }
    }

    private void handleLogin(String rawJson) {
        LoginResponse res;
        try {
            // Parse trực tiếp JSON sang DTO LoginRequest
            LoginRequest req = gson.fromJson(rawJson, LoginRequest.class);
            System.out.println("[Auth] Yêu cầu đăng nhập từ tài khoản: " + req.getUsername());

            User user = userDAO.login(req.getUsername(), req.getPassword());

            if (user != null) {
                this.currentUser = user;
                res = new LoginResponse(true, user.getId(), user.getUsername(), "Đăng nhập thành công!");
                System.out.println("[Auth] Đăng nhập THÀNH CÔNG: " + user.getUsername() + " (ID: " + user.getId() + ")");
            } else {
                res = new LoginResponse(false, -1, "", "Sai tên tài khoản hoặc mật khẩu!");
                System.out.println("[Auth] Đăng nhập THẤT BẠI: " + req.getUsername());
            }

        } catch (Exception e) {
            System.err.println("[Auth] Lỗi xử lý AUTH_LOGIN_REQ: " + e.getMessage());
            res = new LoginResponse(false, -1, "", "Lỗi định dạng dữ liệu đăng nhập!");
        }

        // Gửi phản hồi ra ngoài try-catch nghiệp vụ và bắt IOException mạng riêng
        try {
            Packet resPacket = new Packet(PacketType.AUTH_LOGIN_RES, gson.toJson(res));
            NetworkUtil.sendPacket(dos, resPacket);
        } catch (IOException e) {
            System.err.println("[Auth] Lỗi gửi phản hồi Login: " + e.getMessage());
        }
    }

    private void handleCreateRoom() {
        try {
            if (currentUser == null) {
                System.out.println("[Lobby] Client chưa đăng nhập, không thể tạo phòng.");
                return;
            }

            Room room = roomManager.createRoom(currentUser);
            currentRoomId = room.getRoomId();

            Packet response = new Packet(
                    PacketType.ROOM_STATE_UPDATE,
                    gson.toJson(roomManager.getRoomDTO(currentRoomId))
            );

            NetworkUtil.sendPacket(dos, response);

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

    private void handleJoinRoom(String rawJson) {
        try {
            int roomId = gson.fromJson(rawJson, Integer.class);

            boolean success = roomManager.joinRoom(
                    roomId,
                    currentUser
            );

            if (success) {
                currentRoomId = roomId;

                System.out.println(
                        "[Lobby] Client đã vào phòng ID: " + roomId
                );

                // Đồng bộ trạng thái phòng cho tất cả Client
                broadcastRoomState();

            } else {
                System.out.println(
                        "[Lobby] Client không thể vào phòng ID: " + roomId
                );

                // Chỉ báo trạng thái hiện tại cho Client join thất bại
                Packet response = new Packet(
                        PacketType.ROOM_STATE_UPDATE,
                        gson.toJson(roomManager.getRoomDTO(roomId))
                );

                NetworkUtil.sendPacket(dos, response);
            }

        } catch (Exception e) {
            System.err.println(
                    "[Lobby] Lỗi xử lý ROOM_JOIN_REQ: "
                    + e.getMessage()
            );
        }
    }

    private void handleReady(String rawJson) {
        try {
            if (currentUser == null) {
                System.out.println("[Room] Client chưa đăng nhập.");
                return;
            }

            if (currentRoomId == -1) {
                System.out.println("[Room] Client chưa ở trong phòng.");
                return;
            }

            boolean ready = gson.fromJson(rawJson, Boolean.class);

            boolean success = roomManager.setPlayerReady(
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

// Kiểm tra tất cả người chơi đã Ready chưa
            Room room = roomManager.getRoom(currentRoomId);

            if (room != null
                    && room.getCurrentPlayers() == room.getMaxPlayers()
                    && room.areAllPlayersReady()) {

                System.out.println(
                        "[Room] Tất cả người chơi đã Ready. Bắt đầu trận!"
                );

                broadcastGameStart();
            }

        } catch (Exception e) {
            System.err.println(
                    "[Room] Lỗi xử lý ROOM_READY_REQ: "
                    + e.getMessage()
            );
        }
    }

    private void broadcastRoomStateForRoom(int roomId) {

        Room room = roomManager.getRoom(roomId);

        if (room == null) {
            return;
        }

        Packet packet = new Packet(
                PacketType.ROOM_STATE_UPDATE,
                gson.toJson(roomManager.getRoomDTO(roomId))
        );

        for (ClientHandler client : connectedClients) {

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

    private void broadcastGameStart() {
        if (currentRoomId == -1) {
            return;
        }

        Packet packet = new Packet(
                PacketType.GAME_START_NOTIFY,
                gson.toJson(currentRoomId)
        );

        for (ClientHandler client : connectedClients) {
            if (client.currentRoomId == currentRoomId) {
                try {
                    NetworkUtil.sendPacket(client.dos, packet);

                    System.out.println(
                            "[Game] Đã gửi GAME_START_NOTIFY tới Client"
                    );

                } catch (IOException e) {
                    System.err.println(
                            "[Game] Không thể gửi GAME_START_NOTIFY: "
                            + e.getMessage()
                    );
                }
            }
        }
    }

    private void handleRegister(String rawJson) {
        RegisterResponse res;
        try {
            // Dùng chung LoginRequest vì đăng ký cùng gồm 2 trường username và password
            LoginRequest req = gson.fromJson(rawJson, LoginRequest.class);
            System.out.println("[Auth] Yêu cầu đăng ký tài khoản: " + req.getUsername());

            boolean isSuccess = userDAO.register(req.getUsername(), req.getPassword());

            if (isSuccess) {
                res = new RegisterResponse(true, "Đăng ký tài khoản thành công!");
                System.out.println("[Auth] Đăng ký THÀNH CÔNG: " + req.getUsername());
            } else {
                res = new RegisterResponse(false, "Đăng ký thất bại! Tên tài khoản có thể đã tồn tại.");
                System.out.println("[Auth] Đăng ký THẤT BẠI: " + req.getUsername());
            }

        } catch (Exception e) {
            System.err.println("[Auth] Lỗi xử lý AUTH_REGISTER_REQ: " + e.getMessage());
            res = new RegisterResponse(false, "Lỗi định dạng dữ liệu đăng ký!");
        }

        // Gửi phản hồi ra ngoài try-catch nghiệp vụ và bắt IOException mạng riêng
        try {
            Packet resPacket = new Packet(PacketType.AUTH_REGISTER_RES, gson.toJson(res));
            NetworkUtil.sendPacket(dos, resPacket);
        } catch (IOException e) {
            System.err.println("[Auth] Lỗi gửi phản hồi Register: " + e.getMessage());
        }
    }

    private void handleGetRooms() {
        try {
            String roomsJson = gson.toJson(roomManager.getAllRooms());

            Packet response = new Packet(
                    PacketType.LOBBY_ROOMS_RES,
                    roomsJson
            );

            NetworkUtil.sendPacket(dos, response);

            System.out.println("[Lobby] Đã gửi danh sách phòng cho Client.");

        } catch (IOException e) {
            System.err.println(
                    "[Lobby] Lỗi gửi danh sách phòng: "
                    + e.getMessage()
            );
        }
    }

    private void broadcastRoomState() {
        if (currentRoomId == -1) {
            return;
        }

        Room room = roomManager.getRoom(currentRoomId);

        if (room == null) {
            return;
        }

        Packet packet = new Packet(
                PacketType.ROOM_STATE_UPDATE,
                gson.toJson(roomManager.getRoomDTO(currentRoomId))
        );

        for (ClientHandler client : connectedClients) {
            if (client.currentRoomId == currentRoomId) {
                try {
                    NetworkUtil.sendPacket(client.dos, packet);

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

    private void handleLeaveRoom() {
        try {
            if (currentUser == null || currentRoomId == -1) {
                return;
            }

            int roomId = currentRoomId;
            int userId = currentUser.getId();

            boolean success = roomManager.leaveRoom(
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

                // Cập nhật cho những Client còn lại trong phòng
                broadcastRoomStateForRoom(roomId);
            }

        } catch (Exception e) {
            System.err.println(
                    "[Room] Lỗi xử lý ROOM_LEAVE_REQ: "
                    + e.getMessage()
            );
        }
    }

    public void closeConnection() {
        isRunning = false;
        connectedClients.remove(this);

        // Nếu người chơi đang ở trong phòng thì xóa khỏi phòng
        if (currentUser != null && currentRoomId != -1) {
            roomManager.leaveRoom(currentRoomId, currentUser.getId());

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
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[ClientHandler] Lỗi khi giải phóng socket: " + e.getMessage());
        }

        System.out.println("[ClientHandler] Đã đóng tài nguyên kết nối an toàn.");
    }
}
