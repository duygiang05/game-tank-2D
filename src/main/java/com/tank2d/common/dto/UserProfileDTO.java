package com.tank2d.common.dto;

/**
  * DTO chứa thông tin hồ sơ và toàn bộ chỉ số thống kê sự nghiệp của người chơi.
  */
public class UserProfileDTO {

    private int userId;
    private String username;
    private int totalPoints;
    private int totalKills;
    private int totalWins;
    private int totalHits;
    private int totalMatches;
    private int rankPosition;
    private String rankTier;
    private double winRate;

    public UserProfileDTO() {
    }

    public UserProfileDTO(int userId, String username, int totalPoints, int totalKills,
                          int totalWins, int totalHits, int totalMatches,
                          int rankPosition, String rankTier, double winRate) {
        this.userId = userId;
        this.username = username;
        this.totalPoints = totalPoints;
        this.totalKills = totalKills;
        this.totalWins = totalWins;
        this.totalHits = totalHits;
        this.totalMatches = totalMatches;
        this.rankPosition = rankPosition;
        this.rankTier = rankTier;
        this.winRate = winRate;
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

    public int getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public void setTotalMatches(int totalMatches) {
        this.totalMatches = totalMatches;
    }

    public int getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(int rankPosition) {
        this.rankPosition = rankPosition;
    }

    public String getRankTier() {
        return rankTier;
    }

    public void setRankTier(String rankTier) {
        this.rankTier = rankTier;
    }

    public double getWinRate() {
        return winRate;
    }

    public void setWinRate(double winRate) {
        this.winRate = winRate;
    }
}
