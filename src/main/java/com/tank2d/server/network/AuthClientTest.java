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
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuthClientTest {

    private static final Logger LOGGER = Logger.getLogger(AuthClientTest.class.getName());

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8888;
        Gson gson = new Gson();

        LOGGER.info("==================================================");
        LOGGER.info(" [CLIENT TEST] BẮT ĐẦU DEMO KIỂM THỬ AUTH SOCKET");
        LOGGER.info("==================================================");

        try (Socket socket = new Socket(host, port);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            LOGGER.info("[Client] Đã kết nối thành công tới Server (" + host + ":" + port + ")");

            // Tạo username ngẫu nhiên để tránh lỗi trùng lặp khi chạy test nhiều lần
            String testUser = "giang_player_" + (System.currentTimeMillis() % 10000);
            String testPass = "pass123456";

            // -------------------------------------------------------------
            // KỊCH BẢN 1: ĐĂNG KÝ TÀI KHOẢN MỚI
            // -------------------------------------------------------------
            LOGGER.info(">>> [Kịch bản 1] Gửi yêu cầu đăng ký (AUTH_REGISTER_REQ)...");
            LoginRequest regReq = new LoginRequest(testUser, testPass);
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_REGISTER_REQ, gson.toJson(regReq)));

            Packet regResPacket = NetworkUtil.readPacket(dis);
            RegisterResponse regRes = gson.fromJson(regResPacket.getData(), RegisterResponse.class);
            LOGGER.info("<<< [Server phản hồi]: type=" + regResPacket.getType() + ", success=" + regRes.isSuccess() + ", msg=" + regRes.getMessage());
            LOGGER.info("--------------------------------------------------");

            // -------------------------------------------------------------
            // KỊCH BẢN 2: ĐĂNG KÝ LẠI TÀI KHOẢN VỪA TẠO (Kỳ vọng THẤT BẠI vì trùng)
            // -------------------------------------------------------------
            LOGGER.info(">>> [Kịch bản 2] Thử đăng ký trùng username vừa tạo...");
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_REGISTER_REQ, gson.toJson(regReq)));

            Packet duplicateResPacket = NetworkUtil.readPacket(dis);
            RegisterResponse dupRes = gson.fromJson(duplicateResPacket.getData(), RegisterResponse.class);
            LOGGER.info("<<< [Server phản hồi]: status=" + (dupRes.isSuccess() ? "LỖI" : "ĐÚNG") + ", msg=" + dupRes.getMessage());
            LOGGER.info("--------------------------------------------------");

            // -------------------------------------------------------------
            // KỊCH BẢN 3: ĐĂNG NHẬP VỚI MẬT KHẨU ĐÚNG (AUTH_LOGIN_REQ)
            // -------------------------------------------------------------
            LOGGER.info(">>> [Kịch bản 3] Gửi yêu cầu đăng nhập đúng mật khẩu...");
            LoginRequest loginReq = new LoginRequest(testUser, testPass);
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_LOGIN_REQ, gson.toJson(loginReq)));

            Packet loginResPacket = NetworkUtil.readPacket(dis);
            LoginResponse loginRes = gson.fromJson(loginResPacket.getData(), LoginResponse.class);
            LOGGER.info("<<< [Server phản hồi]: type=" + loginResPacket.getType() + ", success=" + loginRes.isSuccess() + ", userId=" + loginRes.getUserId() + ", msg=" + loginRes.getMessage());
            LOGGER.info("--------------------------------------------------");

            // -------------------------------------------------------------
            // KỊCH BẢN 4: ĐĂNG NHẬP VỚI MẬT KHẨU SAI (Kỳ vọng THẤT BẠI)
            // -------------------------------------------------------------
            LOGGER.info(">>> [Kịch bản 4] Gửi yêu cầu đăng nhập với sai mật khẩu...");
            LoginRequest wrongLoginReq = new LoginRequest(testUser, "wrong_pass_xyz");
            NetworkUtil.sendPacket(dos, new Packet(PacketType.AUTH_LOGIN_REQ, gson.toJson(wrongLoginReq)));

            Packet wrongResPacket = NetworkUtil.readPacket(dis);
            LoginResponse wrongRes = gson.fromJson(wrongResPacket.getData(), LoginResponse.class);
            LOGGER.info("<<< [Server phản hồi]: status=" + (wrongRes.isSuccess() ? "LỖI" : "ĐÚNG") + ", msg=" + wrongRes.getMessage());
            LOGGER.info("==================================================");
            LOGGER.info(" [CLIENT TEST] HOÀN TẤT TẤT CẢ KỊCH BẢN KIỂM THỬ!");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Client Test] Lỗi kết nối Socket", e);
        }
    }
}