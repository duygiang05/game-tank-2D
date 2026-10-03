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
 * Máy chủ TCP Socket chính của hệ thống Tank 2D Online.
 * <p>
 * Quản lý vòng đời socket, lắng nghe các kết nối TCP từ client và điều phối phiên làm việc
 * thông qua ThreadPool đa luồng để tối ưu hiệu năng.
 */
public class TankServer {

    private static final Logger LOGGER = Logger.getLogger(TankServer.class.getName());
    private static final int DEFAULT_PORT = 8888;

    private final int port;
    private final ExecutorService threadPool;
    private final UserDAO userDAO;
    private final MatchDAO matchDAO;
    private final RoomManager roomManager;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;

    /**
     * Khởi tạo Server với cổng kết nối mạng chỉ định.
     *
     * @param port cổng TCP (ví dụ: 8888)
     */
    public TankServer(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.userDAO = new UserDAO();
        this.matchDAO = new MatchDAO();
        this.roomManager = new RoomManager();
        this.isRunning = false;
    }

    /**
     * Khởi động máy chủ, lắng nghe kết nối socket từ client và phân phối vào thread pool.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;

            LOGGER.info("[TANK2D SERVER] KHỞI ĐỘNG THÀNH CÔNG trên Cổng TCP: " + port);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("[Server] Nhận kết nối mới từ: " + clientSocket.getRemoteSocketAddress());

                    ClientHandler handler = new ClientHandler(
                            clientSocket,
                            userDAO,
                            matchDAO,
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

    /**
     * Dừng máy chủ an toàn và giải phóng tài nguyên.
     */
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
        int defaultPort = ConfigLoader.getEnvInt("SERVER_PORT", DEFAULT_PORT);
        int port = ConfigLoader.getPropertyInt("server.bind_port", defaultPort);

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
