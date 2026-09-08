package com.tank2d.client.network;

import com.tank2d.common.protocol.NetworkUtil;
import com.tank2d.common.protocol.Packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientSocket {

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

        System.out.println("[ClientSocket] Đã kết nối tới "
                + host + ":" + port);
    }

    public void sendPacket(Packet packet) throws IOException {
        if (!isConnected()) {
            throw new IOException("Client chưa kết nối tới Server!");
        }

        NetworkUtil.sendPacket(dos, packet);
    }

    public Packet receivePacket() throws IOException {
        if (!isConnected()) {
            throw new IOException("Client chưa kết nối tới Server!");
        }

        return NetworkUtil.readPacket(dis);
    }

    public boolean isConnected() {
        return socket != null
                && socket.isConnected()
                && !socket.isClosed();
    }

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

            System.out.println("[ClientSocket] Đã đóng kết nối.");

        } catch (IOException e) {
            System.err.println(
                    "[ClientSocket] Lỗi đóng kết nối: "
                    + e.getMessage()
            );
        }
    }
}