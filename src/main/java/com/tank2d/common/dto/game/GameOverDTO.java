package com.tank2d.common.dto.game;

import java.util.Map;

public class GameOverDTO {
    private int winnerTankId;
    private String reason;
    private Map<Integer, Integer> finalScores; // tankId -> score (points)
    private Map<Integer, Integer> finalKills;  // tankId -> kills
    private Map<Integer, Integer> finalHits;   // tankId -> hits

    public GameOverDTO() {}

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