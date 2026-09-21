package com.tank2d.server.dao;

import com.tank2d.server.db.DatabaseConnection;
import com.tank2d.server.game.PlayerCombatState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

public class MatchDAO {

    /**
     * Khớp chính xác với cấu trúc bảng match_history và match_participants trong phpMyAdmin.
     */
    public int recordMatchResult(String roomName, Integer winnerUserId, int durationSeconds, List<PlayerCombatState> participants) {
        String insertMatchSql = "INSERT INTO match_history (room_name, winner_id, duration_seconds, played_at) VALUES (?, ?, ?, NOW())";
        String insertParticipantSql = "INSERT INTO match_participants (match_id, user_id, kills, hits, rank_position, points_earned) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            int matchId = -1;

            // 1. Ghi vào match_history
            try (PreparedStatement psMatch = conn.prepareStatement(insertMatchSql, Statement.RETURN_GENERATED_KEYS)) {
                psMatch.setString(1, (roomName != null && !roomName.isEmpty()) ? roomName : "Custom Match");
                if (winnerUserId != null && winnerUserId > 0) {
                    psMatch.setInt(2, winnerUserId);
                } else {
                    psMatch.setNull(2, Types.INTEGER); // Trận hòa: winner_id = NULL
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

            // 2. Ghi từng người chơi vào match_participants
            try (PreparedStatement psPart = conn.prepareStatement(insertParticipantSql)) {
                for (int i = 0; i < participants.size(); i++) {
                    PlayerCombatState p = participants.get(i);
                    psPart.setInt(1, matchId);
                    psPart.setInt(2, p.getUserId());
                    psPart.setInt(3, p.getKills());
                    psPart.setInt(4, p.getHits());
                    psPart.setInt(5, i + 1); // rank_position: 1, 2, 3...
                    psPart.setInt(6, p.getScore()); // points_earned
                    psPart.addBatch();
                }
                psPart.executeBatch();
            }

            conn.commit();
            System.out.println("[MatchDAO] Đã lưu thành công trận đấu Match ID = " + matchId);
            return matchId;

        } catch (SQLException e) {
            System.err.println("[MatchDAO] Lỗi khi lưu kết quả trận đấu: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
}