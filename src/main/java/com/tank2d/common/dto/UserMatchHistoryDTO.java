package com.tank2d.common.dto;

import java.io.Serializable;

public class UserMatchHistoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int matchId;
    private String roomName;
    private String result; // "VICTORY", "DEFEAT", "DRAW"
    private int rankPosition;
    private int kills;
    private int hits;
    private int pointsEarned;
    private int durationSeconds;
    private String playedAt;

    public UserMatchHistoryDTO() {
    }

    public UserMatchHistoryDTO(int matchId, String roomName, String result, int rankPosition,
                               int kills, int hits, int pointsEarned, int durationSeconds, String playedAt) {
        this.matchId = matchId;
        this.roomName = roomName;
        this.result = result;
        this.rankPosition = rankPosition;
        this.kills = kills;
        this.hits = hits;
        this.pointsEarned = pointsEarned;
        this.durationSeconds = durationSeconds;
        this.playedAt = playedAt;
    }

    public int getMatchId() {
        return matchId;
    }

    public void setMatchId(int matchId) {
        this.matchId = matchId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
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

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(String playedAt) {
        this.playedAt = playedAt;
    }
}
