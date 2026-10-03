package com.tank2d.common.dto.game;

import java.util.Map;

/**
 * DTO chứa kết quả tổng kết khi trận đấu kết thúc (GAME_OVER).
 * Bao gồm người chiến thắng, lý do kết thúc và bảng thống kê điểm, kills, hits của từng xe.
 */
public class GameOverDTO {
    private int winnerTankId;
    private String reason;
    private Map<Integer, Integer> finalScores;
    private Map<Integer, Integer> finalKills;
    private Map<Integer, Integer> finalHits;

    public GameOverDTO() {}

    /**
     * Khởi tạo thông báo kết thúc trận đấu.
     *
     * @param winnerTankId mã xe tăng chiến thắng (-1 nếu hòa)
     * @param reason lý do kết thúc (hết giờ hoặc đối thủ bị hạ)
     * @param finalScores bảng điểm tổng kết theo tankId
     * @param finalKills bảng số mạng hạ gục theo tankId
     * @param finalHits bảng số phát bắn trúng theo tankId
     */
    public GameOverDTO(int winnerTankId, String reason, 
                       Map<Integer, Integer> finalScores, 
                       Map<Integer, Integer> finalKills, 
                       Map<Integer, Integer> finalHits) {
        this.winnerTankId = winnerTankId;
        this.reason = reason;
        this.finalScores = finalScores;
        this.finalKills = finalKills;
        this.finalHits = finalHits;
    }

    public int getWinnerTankId() { return winnerTankId; }
    public String getReason() { return reason; }
    public Map<Integer, Integer> getFinalScores() { return finalScores; }
    public Map<Integer, Integer> getFinalKills() { return finalKills; }
    public Map<Integer, Integer> getFinalHits() { return finalHits; }
}