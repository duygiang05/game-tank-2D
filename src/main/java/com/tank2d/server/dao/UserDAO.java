package com.tank2d.server.dao;

import com.tank2d.common.model.User;

public interface UserDAO {
    User findByUsername(String username);
    boolean register(String username, String passwordHash);
    void updateScore(int userId, int points);
}