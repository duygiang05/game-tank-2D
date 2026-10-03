package com.tank2d.server.dao;

import com.tank2d.common.exception.DatabaseException;
import com.tank2d.common.exception.ErrorCode;
import com.tank2d.common.model.User;
import com.tank2d.common.dto.LeaderboardDTO;
import com.tank2d.server.db.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lớp truy xuất cơ sở dữ liệu đối với thông tin người dùng và chỉ số tích lũy (User DAO).
 */
public class UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());

    /**
     * Đăng ký tài khoản mới: băm mật khẩu bằng BCrypt và lưu vào CSDL.
     * CSDL có sẵn trigger tự động tạo bản ghi trong bảng user_stats.
     *
     * @param username tên tài khoản người chơi
     * @param plainPassword mật khẩu dạng thô
     * @return true nếu đăng ký thành công, false nếu thông tin không hợp lệ hoặc đã tồn tại
     * @throws DatabaseException khi gặp lỗi truy vấn cơ sở dữ liệu
     */
    public boolean register(String username, String plainPassword) {
        if (username == null || plainPassword == null || username.trim().isEmpty() || plainPassword.isEmpty()) {
            return false;
        }

        if (isUsernameTaken(username)) {
            LOGGER.warning("[UserDAO] Đăng ký thất bại: Tên đăng nhập '" + username + "' đã tồn tại.");
            return false;
        }

        String hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt());

        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            ps.setString(2, hashedPassword);

            int rowsInserted = ps.executeUpdate();
            return rowsInserted > 0;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[UserDAO] Lỗi khi đăng ký tài khoản", e);
            throw new DatabaseException(ErrorCode.DB_QUERY_ERROR, "Lỗi cơ sở dữ liệu khi đăng ký!", e);
        }
    }

    /**
     * Xác thực đăng nhập: lấy chuỗi băm từ CSDL và so khớp với mật khẩu thô bằng BCrypt.checkpw().
     *
     * @param username tên đăng nhập
     * @param plainPassword mật khẩu thô
     * @return đối tượng {@link User} nếu thông tin hợp lệ, null nếu sai tài khoản hoặc mật khẩu
     * @throws DatabaseException khi gặp lỗi truy vấn cơ sở dữ liệu
     */
    public User login(String username, String plainPassword) {
        if (username == null || plainPassword == null || username.trim().isEmpty() || plainPassword.isEmpty()) {
            return null;
        }

        String sql = "SELECT id, username, password_hash FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String dbUsername = rs.getString("username");
                    String dbPasswordHash = rs.getString("password_hash");

                    if (BCrypt.checkpw(plainPassword, dbPasswordHash)) {
                        return new User(id, dbUsername);
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[UserDAO] Lỗi khi thực hiện đăng nhập", e);
            throw new DatabaseException(ErrorCode.DB_QUERY_ERROR, "Lỗi cơ sở dữ liệu khi đăng nhập!", e);
        }

        return null;
    }

    /**
     * Cập nhật điểm tích lũy sau mỗi trận đấu vào bảng user_stats.
     *
     * @param userId mã định danh người dùng
     * @param pointsEarned điểm số nhận được từ trận đấu
     * @param kills số mạng hạ gục
     * @param hits số phát bắn trúng đích
     * @param isWin true nếu là người chiến thắng
     * @return true nếu cập nhật thành công, false nếu thất bại
     */
    public boolean updateMatchStats(int userId, int pointsEarned, int kills, int hits, boolean isWin) {
        String sql = "UPDATE user_stats SET "
                + "total_points = total_points + ?, "
                + "total_kills = total_kills + ?, "
                + "total_hits = total_hits + ?, "
                + "total_wins = total_wins + ? "
                + "WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, pointsEarned);
            ps.setInt(2, kills);
            ps.setInt(3, hits);
            ps.setInt(4, isWin ? 1 : 0);
            ps.setInt(5, userId);

            int rowsUpdated = ps.executeUpdate();
            return rowsUpdated > 0;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[UserDAO] Lỗi khi cập nhật chỉ số user_stats", e);
            return false;
        }
    }

    /**
     * Lấy bảng xếp hạng Top 10 người chơi theo đặc tả:
     * 1. Tổng điểm giảm dần (total_points DESC)
     * 2. Tổng kills giảm dần (total_kills DESC)
     * 3. Tổng số trận thắng giảm dần (total_wins DESC)
     * 4. Id người dùng tăng dần (u.id ASC - ổn định thứ hạng khi bằng điểm)
     *
     * @return danh sách các {@link LeaderboardDTO} của Top 10 người chơi
     */
    public List<LeaderboardDTO> getLeaderboard() {
        List<LeaderboardDTO> leaderboard = new ArrayList<>();

        String sql = "SELECT "
                + "u.id AS user_id, "
                + "u.username, "
                + "s.total_points, "
                + "s.total_kills, "
                + "s.total_wins "
                + "FROM users u "
                + "JOIN user_stats s ON u.id = s.user_id "
                + "ORDER BY "
                + "s.total_points DESC, "
                + "s.total_kills DESC, "
                + "s.total_wins DESC, "
                + "u.id ASC "
                + "LIMIT 10";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            int rank = 1;
            while (rs.next()) {
                LeaderboardDTO dto = new LeaderboardDTO(
                        rank,
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getInt("total_points"),
                        rs.getInt("total_kills"),
                        rs.getInt("total_wins")
                );
                leaderboard.add(dto);
                rank++;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[UserDAO] Lỗi khi lấy Top 10 Leaderboard", e);
        }

        return leaderboard;
    }

    /**
     * Kiểm tra sự tồn tại của tên đăng nhập để tránh lỗi trùng khóa (duplicate key).
     *
     * @param username tên tài khoản cần kiểm tra
     * @return true nếu tài khoản đã tồn tại, ngược lại false
     * @throws DatabaseException khi gặp sự cố truy vấn CSDL
     */
    public boolean isUsernameTaken(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[UserDAO] Lỗi khi kiểm tra username tồn tại", e);
            throw new DatabaseException(ErrorCode.DB_QUERY_ERROR, "Lỗi kiểm tra tài khoản tồn tại!", e);
        }
    }
}
