package com.tank2d.server.dao;

import com.tank2d.common.model.User;
import com.tank2d.server.db.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    /**
     * Đăng ký tài khoản mới: băm mật khẩu bằng BCrypt và lưu vào CSDL.
     * CSDL có sẵn trigger tự động tạo bản ghi trong bảng user_stats.
     */
    public boolean register(String username, String plainPassword) {
        if (username == null || plainPassword == null || username.trim().isEmpty() || plainPassword.isEmpty()) {
            return false;
        }

        if (isUsernameTaken(username)) {
            System.err.println("[UserDAO] Đăng ký thất bại: Tên đăng nhập '" + username + "' đã tồn tại.");
            return false;
        }

        // Băm mật khẩu bằng BCrypt (cost factor mặc định là 10)
        String hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt());

        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            ps.setString(2, hashedPassword);

            int rowsInserted = ps.executeUpdate();
            return rowsInserted > 0;

        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi khi đăng ký tài khoản: " + e.getMessage());
            return false;
        }
    }

    /**
     * Xác thực đăng nhập: lấy hash từ CSDL và so khớp với plain text bằng BCrypt.checkpw().
     * @return User object nếu hợp lệ, null nếu sai tên hoặc mật khẩu.
     */
    public User login(String username, String plainPassword) {
        if (username == null || plainPassword == null || username.trim().isEmpty() || plainPassword.isEmpty()) {
            return null;
        }

        String sql = "SELECT id, username, password_hash FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String dbUsername = rs.getString("username");
                    String dbPasswordHash = rs.getString("password_hash");

                    // So khớp chuỗi mật khẩu gõ vào với hash BCrypt trong DB
                    if (BCrypt.checkpw(plainPassword, dbPasswordHash)) {
                        return new User(id, dbUsername);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi khi thực hiện đăng nhập: " + e.getMessage());
        }

        return null;
    }

    /**
     * Cập nhật điểm tích lũy sau mỗi trận đấu vào bảng user_stats.
     */
    public boolean updateMatchStats(int userId, int pointsEarned, int kills, int hits, boolean isWin) {
        String sql = "UPDATE user_stats SET "
                   + "total_points = total_points + ?, "
                   + "total_kills = total_kills + ?, "
                   + "total_hits = total_hits + ?, "
                   + "total_wins = total_wins + ? "
                   + "WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, pointsEarned);
            ps.setInt(2, kills);
            ps.setInt(3, hits);
            ps.setInt(4, isWin ? 1 : 0);
            ps.setInt(5, userId);

            int rowsUpdated = ps.executeUpdate();
            return rowsUpdated > 0;

        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi khi cập nhật chỉ số user_stats: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra nhanh sự tồn tại của username để tránh lỗi duplicate key.
     */
    public boolean isUsernameTaken(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi khi kiểm tra username tồn tại: " + e.getMessage());
            return true;
        }
    }
}