package com.tank2d.server.dao;

import com.tank2d.common.exception.DatabaseException;
import com.tank2d.common.exception.ErrorCode;
import com.tank2d.server.db.DatabaseConnection;
import com.tank2d.server.game.PlayerCombatState;

import com.tank2d.common.dto.MatchDetailDTO;
import com.tank2d.common.dto.MatchParticipantDTO;
import com.tank2d.common.dto.UserMatchHistoryDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lớp truy xuất cơ sở dữ liệu đối với lịch sử trận đấu và kết quả thi đấu (Match DAO).
 */
public class MatchDAO {

    private static final Logger LOGGER = Logger.getLogger(MatchDAO.class.getName());

    /**
     * Ghi nhận kết quả trận đấu hoàn tất vào CSDL và lưu chỉ số từng người tham gia.
     *
     * @param roomName tên phòng thi đấu
     * @param winnerUserId mã ID người chiến thắng (null nếu hòa)
     * @param durationSeconds thời lượng trận đấu tính bằng giây
     * @param participants danh sách trạng thái người chơi tham gia trận đấu
     * @return mã ID trận đấu vừa tạo (match_id), hoặc -1 nếu thất bại
     * @throws DatabaseException khi gặp sự cố ghi dữ liệu vào CSDL
     */
    public int recordMatchResult(String roomName, Integer winnerUserId, int durationSeconds, List<PlayerCombatState> participants) {
        String insertMatchSql = "INSERT INTO match_history (room_name, winner_id, duration_seconds, played_at) VALUES (?, ?, ?, NOW())";
        String insertParticipantSql = "INSERT INTO match_participants (match_id, user_id, kills, hits, rank_position, points_earned) VALUES (?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            int matchId = -1;

            try (PreparedStatement psMatch = conn.prepareStatement(insertMatchSql, Statement.RETURN_GENERATED_KEYS)) {
                psMatch.setString(1, (roomName != null && !roomName.isEmpty()) ? roomName : "Custom Match");
                if (winnerUserId != null && winnerUserId > 0) {
                    psMatch.setInt(2, winnerUserId);
                } else {
                    psMatch.setNull(2, Types.INTEGER);
                }
                psMatch.setInt(3, durationSeconds);
                psMatch.executeUpdate();

                try (ResultSet rs = psMatch.getGeneratedKeys()) {
                    if (rs.next()) {
                        matchId = rs.getInt(1);
                    }
                }
            }

            if (matchId == -1) {
                conn.rollback();
                return -1;
            }

            try (PreparedStatement psPart = conn.prepareStatement(insertParticipantSql)) {
                for (int i = 0; i < participants.size(); i++) {
                    PlayerCombatState p = participants.get(i);
                    psPart.setInt(1, matchId);
                    psPart.setInt(2, p.getUserId());
                    psPart.setInt(3, p.getKills());
                    psPart.setInt(4, p.getHits());
                    psPart.setInt(5, i + 1);
                    psPart.setInt(6, p.getScore());
                    psPart.addBatch();
                }
                psPart.executeBatch();
            }

            conn.commit();
            LOGGER.info("[MatchDAO] Đã lưu thành công trận đấu Match ID = " + matchId);
            return matchId;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, "[MatchDAO] Lỗi khi rollback transaction", ex);
                }
            }
            LOGGER.log(Level.SEVERE, "[MatchDAO] Lỗi khi lưu kết quả trận đấu", e);
            throw new DatabaseException(ErrorCode.DB_QUERY_ERROR, "Lỗi khi lưu kết quả trận đấu!", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, "[MatchDAO] Lỗi khi đóng connection", ex);
                }
            }
        }
    }

    /**
     * Lấy danh sách lịch sử thi đấu của người chơi (tối đa 30 trận gần nhất).
     *
     * @param userId mã ID người dùng
     * @return danh sách các {@link UserMatchHistoryDTO} sắp xếp theo thời gian mới nhất
     * @throws DatabaseException khi gặp lỗi truy vấn CSDL
     */
    public List<UserMatchHistoryDTO> getMatchHistoryByUserId(int userId) {
        String sql = "SELECT "
                   + "    m.id AS match_id, "
                   + "    m.room_name, "
                   + "    m.winner_id, "
                   + "    m.duration_seconds, "
                   + "    m.played_at, "
                   + "    p.kills, "
                   + "    p.hits, "
                   + "    p.rank_position, "
                   + "    p.points_earned "
                   + "FROM match_participants p "
                   + "JOIN match_history m ON p.match_id = m.id "
                   + "WHERE p.user_id = ? "
                   + "ORDER BY m.played_at DESC "
                   + "LIMIT 30";

        List<UserMatchHistoryDTO> historyList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int matchId = rs.getInt("match_id");
                    String roomName = rs.getString("room_name");
                    Integer winnerId = (Integer) rs.getObject("winner_id");
                    int durationSeconds = rs.getInt("duration_seconds");
                    java.sql.Timestamp playedAtTs = rs.getTimestamp("played_at");
                    String playedAtStr = (playedAtTs != null) ? sdf.format(playedAtTs) : "";

                    int kills = rs.getInt("kills");
                    int hits = rs.getInt("hits");
                    int rankPosition = rs.getInt("rank_position");
                    int pointsEarned = rs.getInt("points_earned");

                    String result;
                    if (winnerId == null) {
                        result = "DRAW";
                    } else if (winnerId == userId) {
                        result = "VICTORY";
                    } else {
                        result = "DEFEAT";
                    }

                    historyList.add(new UserMatchHistoryDTO(
                            matchId,
                            roomName,
                            result,
                            rankPosition,
                            kills,
                            hits,
                            pointsEarned,
                            durationSeconds,
                            playedAtStr
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[MatchDAO] Lỗi khi lấy lịch sử đấu của user " + userId, e);
            throw new DatabaseException(ErrorCode.DB_QUERY_ERROR, "Lỗi khi lấy lịch sử đấu!", e);
        }

        return historyList;
    }

    /**
     * Lấy chi tiết toàn bộ trận đấu bao gồm tất cả người chơi và thông số của họ.
     *
     * @param matchId mã ID trận đấu
     * @return đối tượng {@link MatchDetailDTO} hoặc null nếu không tìm thấy
     * @throws DatabaseException khi gặp sự cố truy vấn CSDL
     */
    public MatchDetailDTO getMatchDetail(int matchId) {
        String matchSql = "SELECT m.id, m.room_name, m.winner_id, m.duration_seconds, m.played_at, u.username AS winner_name "
                        + "FROM match_history m "
                        + "LEFT JOIN users u ON m.winner_id = u.id "
                        + "WHERE m.id = ?";

        String partSql = "SELECT p.user_id, u.username, p.kills, p.hits, p.rank_position, p.points_earned "
                       + "FROM match_participants p "
                       + "JOIN users u ON p.user_id = u.id "
                       + "WHERE p.match_id = ? "
                       + "ORDER BY p.rank_position ASC, p.points_earned DESC";

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        MatchDetailDTO detail = null;

        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer winnerId = null;
            try (PreparedStatement psMatch = conn.prepareStatement(matchSql)) {
                psMatch.setInt(1, matchId);
                try (ResultSet rs = psMatch.executeQuery()) {
                    if (rs.next()) {
                        String roomName = rs.getString("room_name");
                        winnerId = (Integer) rs.getObject("winner_id");
                        int duration = rs.getInt("duration_seconds");
                        Timestamp ts = rs.getTimestamp("played_at");
                        String playedAt = (ts != null) ? sdf.format(ts) : "";
                        String winnerName = rs.getString("winner_name");

                        detail = new MatchDetailDTO(matchId, roomName, duration, playedAt, winnerName, new ArrayList<>());
                    }
                }
            }

            if (detail == null) {
                return null;
            }

            try (PreparedStatement psPart = conn.prepareStatement(partSql)) {
                psPart.setInt(1, matchId);
                try (ResultSet rs = psPart.executeQuery()) {
                    while (rs.next()) {
                        int userId = rs.getInt("user_id");
                        String username = rs.getString("username");
                        int kills = rs.getInt("kills");
                        int hits = rs.getInt("hits");
                        int rank = rs.getInt("rank_position");
                        int points = rs.getInt("points_earned");
                        boolean isWinner = (winnerId != null && winnerId == userId);

                        detail.getParticipants().add(new MatchParticipantDTO(userId, username, rank, kills, hits, points, isWinner));
                    }
                }
            }

            return detail;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[MatchDAO] Lỗi khi lấy chi tiết trận đấu " + matchId, e);
            throw new DatabaseException(ErrorCode.DB_QUERY_ERROR, "Lỗi khi lấy chi tiết trận đấu!", e);
        }
    }
}