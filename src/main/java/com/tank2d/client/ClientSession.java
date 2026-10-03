package com.tank2d.client;

import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.Packet;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phiên làm việc duy nhất (Singleton Session) phía Client.
 * <p>
 * Quản lý kết nối socket, thông tin tài khoản hiện hành, luồng đọc gói tin độc lập (Reader Thread)
 * và phân phối sự kiện gói tin tới các Controller thông qua danh sách Listener.
 */
public class ClientSession {

    private static final Logger LOGGER = Logger.getLogger(ClientSession.class.getName());
    private static ClientSession instance;

    private ClientSocket clientSocket;
    private User currentUser;
    private final List<Consumer<Packet>> packetListeners = new CopyOnWriteArrayList<>();

    private Thread readerThread;
    private volatile boolean reading = false;

    private ClientSession() {}

    public static synchronized ClientSession getInstance() {
        if (instance == null) {
            instance = new ClientSession();
        }
        return instance;
    }

    /**
     * Khởi tạo kết nối mạng socket và bắt đầu luồng đọc dữ liệu nền từ máy chủ.
     *
     * @throws IOException nếu kết nối không thành công
     */
    public synchronized void connect() throws IOException {
        if (clientSocket == null || !clientSocket.isConnected()) {
            clientSocket = new ClientSocket();
            clientSocket.connect();
        }
        if (!reading) {
            startReaderThread();
        }
    }

    public ClientSocket getClientSocket() {
        return clientSocket;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected();
    }

    /**
     * Đăng ký đối tượng nhận gói tin phản hồi từ Server.
     *
     * @param listener hàm callback xử lý gói tin
     */
    public void addPacketListener(Consumer<Packet> listener) {
        if (listener != null && !packetListeners.contains(listener)) {
            packetListeners.add(listener);
        }
    }

    /**
     * Hủy đăng ký nhận gói tin.
     *
     * @param listener hàm callback đã đăng ký trước đó
     */
    public void removePacketListener(Consumer<Packet> listener) {
        if (listener != null) {
            packetListeners.remove(listener);
        }
    }

    /**
     * Khởi động Reader Thread duy nhất để liên tục đọc gói tin từ máy chủ và phân phối tới listener.
     */
    private void startReaderThread() {
        if (reading) {
            return;
        }

        reading = true;

        readerThread = new Thread(() -> {
            LOGGER.info("[ClientSession] Bắt đầu Reader Thread...");

            try {
                while (reading && clientSocket != null && clientSocket.isConnected()) {
                    Packet packet = clientSocket.receivePacket();
                    if (packet == null) {
                        break;
                    }

                    for (Consumer<Packet> listener : packetListeners) {
                        try {
                            listener.accept(packet);
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "[ClientSession] Lỗi xử lý packet", e);
                        }
                    }
                }
            } catch (IOException e) {
                if (reading) {
                    LOGGER.log(Level.SEVERE, "[ClientSession] Lỗi Reader Thread", e);
                }
            } finally {
                reading = false;
                LOGGER.info("[ClientSession] Reader Thread đã dừng.");
            }
        });

        readerThread.setDaemon(true);
        readerThread.setName("Client-Server-Reader");
        readerThread.start();
    }

    /**
     * Đóng phiên làm việc hiện tại, giải phóng socket và hủy bỏ luồng đọc.
     */
    public synchronized void close() {
        reading = false;

        if (clientSocket != null) {
            clientSocket.close();
            clientSocket = null;
        }

        packetListeners.clear();
        currentUser = null;
        readerThread = null;
    }
}