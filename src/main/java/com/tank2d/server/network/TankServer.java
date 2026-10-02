package com.tank2d.server.network;

import com.tank2d.common.config.ConfigLoader;
import com.tank2d.server.dao.MatchDAO;
import com.tank2d.server.dao.UserDAO;
import com.tank2d.server.db.DatabaseConnection;
import com.tank2d.server.room.RoomManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lõi Server Socket chạy nền quản lý luồng kết nối đa client bằng ThreadPool.
 */
public class TankServer {

    private static final Logger LOGGER = Logger.getLogger(TankServer.class.getName());

    private final int port;
    private final ExecutorService threadPool;
    private final UserDAO userDAO;
    private final MatchDAO matchDAO;
    private final RoomManager roomManager;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;

    public TankServer(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.userDAO = new UserDAO();
        this.matchDAO = new MatchDAO();
        this.roomManager = new RoomManager();
        this.isRunning = false;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;

            LOGGER.info("[TANK2D SERVER] KHỞI ĐỘNG THÀNH CÔNG trên Cổng TCP: " + port);

            Runtime.getRuntime().addShutdownHook(
                    new Thread(this::stop)
            );

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    LOGGER.info("[Server] Nhận kết nối mới từ: " + clientSocket.getRemoteSocketAddress());

                    ClientHandler handler
                            = new ClientHandler(
                                    clientSocket,
                                    userDAO,matchDAO,
                                    roomManager
                            );

                    threadPool.execute(handler);

                } catch (IOException e) {
                    if (!isRunning) {
                        break;
                    }

                    LOGGER.log(Level.SEVERE, "[Server] Lỗi tiếp nhận client", e);
                }
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Server] Không thể bind port " + port, e);

        } finally {
            stop();
        }
    }

    public synchronized void stop() {

        if (!isRunning) {
            return;
        }

        isRunning = false;

        LOGGER.info("[Server] Đang giải phóng tài nguyên và tắt Server...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Server] Lỗi đóng ServerSocket", e);
        }

        ClientHandler.shutdownBroadcastPool();
        threadPool.shutdown();
        DatabaseConnection.closePool();

        LOGGER.info("[Server] Server đã dừng hoàn toàn.");
    }

    public static void main(String[] args) {

        int port = ConfigLoader.getEnvInt("SERVER_PORT", 8888);

        // Kiểm tra kết nối Database trước khi Server bắt đầu
        try {
            DatabaseConnection.getConnection().close();
            LOGGER.info("[Server] Database kết nối thành công!");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Server] Database lỗi", e);
        }

        TankServer server = new TankServer(port);
        server.start();
    }
}
