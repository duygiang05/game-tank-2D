package com.tank2d.client.network;

import com.tank2d.common.exception.ErrorCode;
import com.tank2d.common.exception.NetworkException;
import com.tank2d.common.protocol.NetworkUtil;
import com.tank2d.common.protocol.Packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quản lý kết nối TCP Socket trực tiếp phía Client tới máy chủ.
 */
public class ClientSocket {

    private static final Logger LOGGER = Logger.getLogger(ClientSocket.class.getName());
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;

    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    private final String host;
    private final int port;

    public ClientSocket() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public ClientSocket(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Thiết lập kết nối socket và khởi tạo các luồng I/O.
     *
     * @throws IOException nếu kết nối máy chủ thất bại
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
        LOGGER.info("[ClientSocket] Đã kết nối tới " + host + ":" + port);
    }

    /**
     * Gửi một gói tin {@link Packet} tới máy chủ.
     *
     * @param packet đối tượng gói tin cần gửi
     * @throws IOException nếu socket bị ngắt hoặc gửi lỗi
     */
    public synchronized void sendPacket(Packet packet) throws IOException {
        if (!isConnected()) {
            throw new NetworkException(ErrorCode.NET_CLIENT_NOT_CONNECTED);
        }
        NetworkUtil.sendPacket(dos, packet);
    }

    /**
     * Nhận một gói tin {@link Packet} từ máy chủ.
     *
     * @return đối tượng gói tin nhận được
     * @throws IOException nếu đọc luồng thất bại
     */
    public Packet receivePacket() throws IOException {
        if (!isConnected()) {
            throw new NetworkException(ErrorCode.NET_CLIENT_NOT_CONNECTED);
        }
        return NetworkUtil.readPacket(dis);
    }

    /**
     * Gửi yêu cầu và đồng bộ đợi phản hồi tương ứng qua cùng luồng socket.
     *
     * @param packet gói tin gửi đi
     * @return gói tin phản hồi nhận về
     * @throws IOException nếu kết nối gặp sự cố
     */
    public synchronized Packet sendAndReceivePacket(Packet packet) throws IOException {
        if (!isConnected()) {
            throw new NetworkException(ErrorCode.NET_CLIENT_NOT_CONNECTED);
        }
        NetworkUtil.sendPacket(dos, packet);
        return NetworkUtil.readPacket(dis);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Đóng an toàn kết nối socket và giải phóng các luồng dữ liệu.
     */
    public void close() {
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
            LOGGER.info("[ClientSocket] Đã đóng kết nối.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[ClientSocket] Lỗi đóng kết nối", e);
        }
    }
}
