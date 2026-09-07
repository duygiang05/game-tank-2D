package com.tank2d.server.network;

import com.google.gson.Gson;
import com.tank2d.common.dto.LoginRequest;
import com.tank2d.common.dto.LoginResponse;
import com.tank2d.common.dto.RegisterResponse;
import com.tank2d.common.protocol.NetworkUtil;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class AuthClientTest {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8888;
        Gson gson = new Gson();

        System.out.println("==================================================");
        System.out.println(" [CLIENT TEST] BẮT ĐẦU DEMO KIỂM THỬ AUTH SOCKET");
        System.out.println("==================================================");

        try (Socket socket = new Socket(host, port);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("[Client] Đã kết nối thành công tới Server (" + host + ":" + port + ")\n");

            // Tạo username ngẫu nhiên để tránh lỗi trùng lặp khi chạy test nhiều lần
            String testUser = "giang_player_" + (System.currentTimeMillis() % 10000);
            String testPass = "pass123456";

            // -------------------------------------------------------------
            // KỊCH BẢN 1: ĐĂNG KÝ TÀI KHOẢN MỚI
            // -------------------------------------------------------------
            System.out.println(">>> [Kịch bản 1] Gửi yêu cầu đăng ký (AUTH_REGISTER_REQ)...");
            LoginRequest regReq = new LoginRequest(testUser, testPass);
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_REGISTER_REQ, gson.toJson(regReq)));

            Packet regResPacket = NetworkUtil.readPacket(dis);
            RegisterResponse regRes = gson.fromJson(regResPacket.getData(), RegisterResponse.class);
            System.out.println("<<< [Server phản hồi]:");
            System.out.println("    - Loại gói tin: " + regResPacket.getType());
            System.out.println("    - Trạng thái thành công: " + regRes.isSuccess());
            System.out.println("    - Thông báo: " + regRes.getMessage());
            System.out.println("--------------------------------------------------");

            // -------------------------------------------------------------
            // KỊCH BẢN 2: ĐĂNG KÝ LẠI TÀI KHOẢN VỪA TẠO (Kỳ vọng THẤT BẠI vì trùng)
            // -------------------------------------------------------------
            System.out.println(">>> [Kịch bản 2] Thử đăng ký trùng username vừa tạo...");
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_REGISTER_REQ, gson.toJson(regReq)));

            Packet duplicateResPacket = NetworkUtil.readPacket(dis);
            RegisterResponse dupRes = gson.fromJson(duplicateResPacket.getData(), RegisterResponse.class);
            System.out.println("<<< [Server phản hồi]:");
            System.out.println("    - Trạng thái: " + (dupRes.isSuccess() ? "LỖI (Trùng mà vẫn cho tạo)" : "ĐÚNG (Bị chặn)"));
            System.out.println("    - Thông báo: " + dupRes.getMessage());
            System.out.println("--------------------------------------------------");

            // -------------------------------------------------------------
            // KỊCH BẢN 3: ĐĂNG NHẬP VỚI MẬT KHẨU ĐÚNG (AUTH_LOGIN_REQ)
            // -------------------------------------------------------------
            System.out.println(">>> [Kịch bản 3] Gửi yêu cầu đăng nhập đúng mật khẩu...");
            LoginRequest loginReq = new LoginRequest(testUser, testPass);
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_LOGIN_REQ, gson.toJson(loginReq)));

            Packet loginResPacket = NetworkUtil.readPacket(dis);
            LoginResponse loginRes = gson.fromJson(loginResPacket.getData(), LoginResponse.class);
            System.out.println("<<< [Server phản hồi]:");
            System.out.println("    - Loại gói tin: " + loginResPacket.getType());
            System.out.println("    - Trạng thái: " + loginRes.isSuccess());
            System.out.println("    - User ID cấp từ DB: " + loginRes.getUserId());
            System.out.println("    - Username: " + loginRes.getUsername());
            System.out.println("    - Thông báo: " + loginRes.getMessage());
            System.out.println("--------------------------------------------------");

            // -------------------------------------------------------------
            // KỊCH BẢN 4: ĐĂNG NHẬP VỚI MẬT KHẨU SAI (Kỳ vọng THẤT BẠI)
            // -------------------------------------------------------------
            System.out.println(">>> [Kịch bản 4] Gửi yêu cầu đăng nhập với sai mật khẩu...");
            LoginRequest wrongLoginReq = new LoginRequest(testUser, "wrong_pass_xyz");
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_LOGIN_REQ, gson.toJson(wrongLoginReq)));

            Packet wrongResPacket = NetworkUtil.readPacket(dis);
            LoginResponse wrongRes = gson.fromJson(wrongResPacket.getData(), LoginResponse.class);
            System.out.println("<<< [Server phản hồi]:");
            System.out.println("    - Trạng thái: " + (wrongRes.isSuccess() ? "LỖI (Sai pass vẫn cho qua)" : "ĐÚNG (Từ chối)"));
            System.out.println("    - Thông báo: " + wrongRes.getMessage());
            System.out.println("==================================================");
            System.out.println(" [CLIENT TEST] HOÀN TẤT TẤT CẢ KỊCH BẢN KIỂM THỬ!");

        } catch (Exception e) {
            System.err.println("[Client Test] Lỗi kết nối Socket: " + e.getMessage());
        }
    }
}