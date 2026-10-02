package com.tank2d.common.dto;

import java.io.Serializable;

public class MatchParticipantDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int userId;
    private String username;
    private int rankPosition;
    private int kills;
    private int hits;
    private int pointsEarned;
    private boolean isWinner;

    public MatchParticipantDTO() {
    }

    public MatchParticipantDTO(int userId, String username, int rankPosition, int kills, int hits, int pointsEarned, boolean isWinner) {
        this.userId = userId;
        this.username = username;
        this.rankPosition = rankPosition;
        this.kills = kills;
        this.hits = hits;
        this.pointsEarned = pointsEarned;
        this.isWinner = isWinner;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(int rankPosition) {
        this.rankPosition = rankPosition;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int getHits() {
        return hits;
    }

    public void setHits(int hits) {
        this.hits = hits;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void setPointsEarned(int pointsEarned) {
        this.pointsEarned = pointsEarned;
    }

    public boolean isWinner() {
        return isWinner;
    }

    public void setWinner(boolean winner) {
        isWinner = winner;
    }
}
