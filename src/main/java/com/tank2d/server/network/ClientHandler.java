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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Worker xử lý kết nối riêng biệt cho từng Client qua TCP Socket.
 * Được quản lý bởi ExecutorService (Thread Pool) trong TankServer.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final UserDAO userDAO;
    private final Gson gson;
    private DataInputStream dis;
    private DataOutputStream dos;
    private volatile boolean isRunning;
    private User currentUser; // Lưu thông tin người chơi sau khi xác thực thành công

    public ClientHandler(Socket socket, UserDAO userDAO) {
        this.socket = socket;
        this.userDAO = userDAO;
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
        if (packet.getType() == null) return;

        switch (packet.getType()) {
            case AUTH_LOGIN_REQ:
                handleLogin(packet.getData());
                break;

            case AUTH_REGISTER_REQ:
                handleRegister(packet.getData());
                break;

            default:
                System.out.println("[ClientHandler] Nhận packet chưa hỗ trợ ở Sprint 1: " + packet.getType());
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

    public void closeConnection() {
        isRunning = false;
        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[ClientHandler] Lỗi khi giải phóng socket: " + e.getMessage());
        }
        System.out.println("[ClientHandler] Đã đóng tài nguyên kết nối an toàn.");
    }

    public User getCurrentUser() {
        return currentUser;
    }
}