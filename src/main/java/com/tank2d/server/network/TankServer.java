package com.tank2d.server.network;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.dao.UserDAO;
import com.tank2d.server.db.DatabaseConnection;
import com.tank2d.server.manager.RoomManager;

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
    private final RoomManager roomManager;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;

    public TankServer(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.userDAO = new UserDAO();
        this.roomManager = new RoomManager();
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

            Runtime.getRuntime().addShutdownHook(
                    new Thread(this::stop)
            );

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    System.out.println(
                            "\n[Server] Nhận kết nối mới từ: "
                            + clientSocket.getRemoteSocketAddress()
                    );

                    ClientHandler handler =
                            new ClientHandler(
                                    clientSocket,
                                    userDAO,
                                    roomManager
                            );

                    threadPool.execute(handler);

                } catch (IOException e) {
                    if (!isRunning) {
                        break;
                    }

                    System.err.println(
                            "[Server] Lỗi tiếp nhận client: "
                            + e.getMessage()
                    );
                }
            }

        } catch (IOException e) {
            System.err.println(
                    "[Server] Không thể bind port "
                    + port + ": " + e.getMessage()
            );

        } finally {
            stop();
        }
    }

    public synchronized void stop() {

        if (!isRunning) {
            return;
        }

        isRunning = false;

        System.out.println(
                "\n[Server] Đang giải phóng tài nguyên và tắt Server..."
        );

        try {
            if (serverSocket != null
                    && !serverSocket.isClosed()) {

                serverSocket.close();
            }

        } catch (IOException e) {

            System.err.println(
                    "[Server] Lỗi đóng ServerSocket: "
                    + e.getMessage()
            );
        }

        threadPool.shutdown();

        DatabaseConnection.closePool();

        System.out.println(
                "[Server] Server đã dừng hoàn toàn."
        );
    }

    public static void main(String[] args) {

        int port =
                ConfigLoader.getEnvInt(
                        "SERVER_PORT",
                        8888
                );

        // Kiểm tra kết nối Database trước khi Server bắt đầu
        try {

            DatabaseConnection
                    .getConnection()
                    .close();

            System.out.println(
                    "[Server] Database kết nối thành công!"
            );

        } catch (Exception e) {

            System.err.println(
                    "[Server] Database lỗi: "
                    + e.getMessage()
            );
        }

        TankServer server =
                new TankServer(port);

        server.start();
    }
}