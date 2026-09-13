package com.tank2d.client;

import com.tank2d.client.network.ClientSocket;
import com.tank2d.common.model.User;

import java.io.IOException;

public class ClientSession {

    private static ClientSession instance;

    private ClientSocket clientSocket;
    private User currentUser;

    private ClientSession() {
    }

    public static synchronized ClientSession getInstance() {
        if (instance == null) {
            instance = new ClientSession();
        }

        return instance;
    }

    public void connect() throws IOException {
        if (clientSocket == null || !clientSocket.isConnected()) {
            clientSocket = new ClientSocket();
            clientSocket.connect();
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

    public void close() {
        if (clientSocket != null) {
            clientSocket.close();
            clientSocket = null;
        }

        currentUser = null;
    }
}