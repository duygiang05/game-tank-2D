package com.tank2d.client;

import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.model.User;
import com.tank2d.common.protocol.Packet;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ClientSession {

    private static ClientSession instance;

    private ClientSocket clientSocket;
    private User currentUser;

    // Danh sách các nơi muốn nhận packet từ Server
    private final List<Consumer<Packet>> packetListeners
            = new CopyOnWriteArrayList<>();

    // Luồng đọc Server duy nhất
    private Thread readerThread;
    private volatile boolean reading = false;

    private ClientSession() {
    }

    public static synchronized ClientSession getInstance() {
        if (instance == null) {
            instance = new ClientSession();
        }

        return instance;
    }

    public synchronized void connect() throws IOException {

        if (clientSocket == null
                || !clientSocket.isConnected()) {

            clientSocket = new ClientSocket();
            clientSocket.connect();
        }

        // Chỉ tạo một Reader Thread
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
        return clientSocket != null
                && clientSocket.isConnected();
    }

    /**
     * Đăng ký nơi nhận packet từ Server
     */
    public void addPacketListener(Consumer<Packet> listener) {

        if (listener != null
                && !packetListeners.contains(listener)) {

            packetListeners.add(listener);
        }
    }

    /**
     * Hủy đăng ký nhận packet
     */
    public void removePacketListener(Consumer<Packet> listener) {

        if (listener != null) {
            packetListeners.remove(listener);
        }
    }

    /**
     * Reader Thread duy nhất đọc dữ liệu từ Server
     */
    private void startReaderThread() {

        if (reading) {
            return;
        }

        reading = true;

        readerThread = new Thread(() -> {

            System.out.println(
                    "[ClientSession] Bắt đầu Reader Thread..."
            );

            try {

                while (reading
                        && clientSocket != null
                        && clientSocket.isConnected()) {

                    Packet packet =
                            clientSocket.receivePacket();

                    if (packet == null) {
                        break;
                    }

                    // Chuyển packet cho các Controller đã đăng ký
                    for (Consumer<Packet> listener
                            : packetListeners) {

                        try {
                            listener.accept(packet);

                        } catch (Exception e) {

                            System.err.println(
                                    "[ClientSession] Lỗi xử lý packet: "
                                    + e.getMessage()
                            );
                        }
                    }
                }

            } catch (IOException e) {

                if (reading) {

                    System.err.println(
                            "[ClientSession] Lỗi Reader Thread: "
                            + e.getMessage()
                    );
                }

            } finally {

                reading = false;

                System.out.println(
                        "[ClientSession] Reader Thread đã dừng."
                );
            }

        });

        readerThread.setDaemon(true);
        readerThread.setName("Client-Server-Reader");
        readerThread.start();
    }

    /**
     * Đóng Session và kết nối Server
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