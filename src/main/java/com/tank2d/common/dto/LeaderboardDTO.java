package com.tank2d.common.dto;

public class LeaderboardDTO {

    private int rank;
    private int userId;
    private String username;
    private int totalPoints;
    private int totalKills;
    private int totalWins;

    public LeaderboardDTO() {
    }

    public LeaderboardDTO(
            int rank,
            int userId,
            String username,
            int totalPoints,
            int totalKills,
            int totalWins) {

        this.rank = rank;
        this.userId = userId;
        this.username = username;
        this.totalPoints = totalPoints;
        this.totalKills = totalKills;
        this.totalWins = totalWins;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
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

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(int totalWins) {
        this.totalWins = totalWins;
    }
}