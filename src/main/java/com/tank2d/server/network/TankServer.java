package com.tank2d.server.network;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.dao.UserDAO;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lõi Server Socket chạy nền quản lý luồng kết nối đa client bằng ThreadPool.
 */
public class TankServer {
    private final int port;
    private final ExecutorService threadPool;
    private final UserDAO userDAO;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;

    public TankServer(int port) {
        this.port = port;
        // Dùng CachedThreadPool để tự động cấp phát thread mới và tái sử dụng thread cũ
        this.threadPool = Executors.newCachedThreadPool();
        this.userDAO = new UserDAO();
        this.isRunning = false;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            System.out.println("==================================================");
            System.out.println("   [TANK2D SERVER] KHỞI ĐỘNG THÀNH CÔNG");
            System.out.println("   Cổng TCP: " + port);
            System.out.println("   Mô hình: Thread Pool đa luồng (Multi-threading)");
            System.out.println("   Sẵn sàng lắng nghe kết nối từ Client...");
            System.out.println("==================================================");

            // Hook shutdown để đóng socket giải phóng port sạch sẽ khi dừng app
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("\n[Server] Nhận kết nối mới từ: " + clientSocket.getRemoteSocketAddress());

                    // Đẩy ClientHandler vào ThreadPool xử lý bất đồng bộ
                    ClientHandler handler = new ClientHandler(clientSocket, userDAO);
                    threadPool.execute(handler);

                } catch (IOException e) {
                    if (!isRunning) {
                        break;
                    }
                    System.err.println("[Server] Lỗi tiếp nhận client: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("[Server] Không thể bind port " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        System.out.println("\n[Server] Đang giải phóng tài nguyên và tắt Server...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi đóng ServerSocket: " + e.getMessage());
        }

        threadPool.shutdown();
        System.out.println("[Server] Server đã dừng hoàn toàn.");
    }
    public static void main(String[] args) {
        // Đọc SERVER_PORT từ .env, nếu không có thì tự lấy giá trị mặc định là 8888
        int port = ConfigLoader.getEnvInt("SERVER_PORT", 8888);

        TankServer server = new TankServer(port);
        server.start();
    }
}