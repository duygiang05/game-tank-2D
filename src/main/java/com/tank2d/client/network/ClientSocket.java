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

public class ClientSocket {

    private static final Logger LOGGER = Logger.getLogger(ClientSocket.class.getName());

    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    private final String host;
    private final int port;

    public ClientSocket() {
        this("localhost", 8888);
    }

    public ClientSocket(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
        LOGGER.info("[ClientSocket] Đã kết nối tới " + host + ":" + port);
    }

    public synchronized void sendPacket(Packet packet) throws IOException {
        if (!isConnected()) {
            throw new NetworkException(ErrorCode.NET_CLIENT_NOT_CONNECTED);
        }
        NetworkUtil.sendPacket(dos, packet);
    }

    public Packet receivePacket() throws IOException {
        if (!isConnected()) {
            throw new NetworkException(ErrorCode.NET_CLIENT_NOT_CONNECTED);
        }
        return NetworkUtil.readPacket(dis);
    }

    public synchronized Packet sendAndReceivePacket(Packet packet) throws IOException {
        if (!isConnected()) {
            throw new NetworkException(ErrorCode.NET_CLIENT_NOT_CONNECTED);
        }
        // Gửi request
        NetworkUtil.sendPacket(dos, packet);
        // Chờ đúng response trước khi thread khác được dùng socket
        return NetworkUtil.readPacket(dis);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void close() {
        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) socket.close();
            LOGGER.info("[ClientSocket] Đã đóng kết nối.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[ClientSocket] Lỗi đóng kết nối", e);
        }
    }
}
