package com.tank2d.server.game;

public class PlayerCombatState {
    private final int tankId;
    private final int userId;
    private int kills = 0;
    private int deaths = 0;
    private int hits = 0;
    private int score = 0; // Tương ứng với points trong trận

    private double respawnTimer = 0.0;
    private double invulnerableTimer = 0.0;
    
    // Mốc thời gian bắn trúng đầu tiên và tiêu diệt đầu tiên
    private long firstKillTimeMillis = Long.MAX_VALUE;
    private long firstHitTimeMillis = Long.MAX_VALUE;

    public PlayerCombatState(int tankId, int userId) {
        this.tankId = tankId;
        this.userId = userId;
    }

    public int getTankId() { return tankId; }
    public int getUserId() { return userId; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public int getHits() { return hits; }
    public int getScore() { return score; }
    public long getFirstKillTimeMillis() { return firstKillTimeMillis; }
    public long getFirstHitTimeMillis() { return firstHitTimeMillis; }

    public void addHit(int points) {
        this.hits++;
        this.score += points;
        // Chỉ ghi nhận mốc thời gian của phát bắn trúng đầu tiên trong trận
        if (this.firstHitTimeMillis == Long.MAX_VALUE) {
            this.firstHitTimeMillis = System.currentTimeMillis();
        }
    }

    public void addKill(int points) {
        this.kills++;
        this.score += points;
        // Chỉ ghi nhận mốc thời gian của mạng hạ gục đầu tiên trong trận
        if (this.firstKillTimeMillis == Long.MAX_VALUE) {
            this.firstKillTimeMillis = System.currentTimeMillis();
        }
    }

    public void addBonusScore(int bonusPoints) {
        this.score += bonusPoints;
    }

    public void addDeath() { this.deaths++; }

    public boolean isWaitingRespawn() { return respawnTimer > 0; }
    public void setRespawnTimer(double seconds) { this.respawnTimer = seconds; }
    public void reduceRespawnTimer(double dt) {
        this.respawnTimer = Math.max(0, this.respawnTimer - dt);
    }

    public boolean isInvulnerable() { return invulnerableTimer > 0; }
    public void setInvulnerableTimer(double seconds) { this.invulnerableTimer = seconds; }
    public void reduceInvulnerableTimer(double dt) {
        this.invulnerableTimer = Math.max(0, this.invulnerableTimer - dt);
    }
}