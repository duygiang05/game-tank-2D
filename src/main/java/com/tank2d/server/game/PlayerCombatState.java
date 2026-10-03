package com.tank2d.server.game;

/**
 * Lưu trữ trạng thái chiến đấu, điểm số và thông số thống kê của một người chơi trong ván đấu.
 */
public class PlayerCombatState {

    private final int tankId;
    private final int userId;
    private int kills = 0;
    private int deaths = 0;
    private int hits = 0;
    private int score = 0;

    private double respawnTimer = 0.0;
    private double invulnerableTimer = 0.0;

    private long firstKillTimeMillis = Long.MAX_VALUE;
    private long firstHitTimeMillis = Long.MAX_VALUE;

    private boolean disconnected = false;
    private boolean eliminated = false;

    /**
     * Khởi tạo trạng thái chiến đấu cho người chơi.
     *
     * @param tankId ID xe tăng đại diện
     * @param userId ID tài khoản người dùng
     */
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

    /**
     * Ghi nhận một phát bắn trúng xe đối phương và cộng điểm tương ứng.
     *
     * @param points số điểm được cộng
     */
    public void addHit(int points) {
        this.hits++;
        this.score += points;
        if (this.firstHitTimeMillis == Long.MAX_VALUE) {
            this.firstHitTimeMillis = System.currentTimeMillis();
        }
    }

    /**
     * Ghi nhận một mạng hạ gục đối phương và cộng điểm tương ứng.
     *
     * @param points số điểm được cộng
     */
    public void addKill(int points) {
        this.kills++;
        this.score += points;
        if (this.firstKillTimeMillis == Long.MAX_VALUE) {
            this.firstKillTimeMillis = System.currentTimeMillis();
        }
    }

    /**
     * Cộng thêm điểm thưởng (ví dụ: điểm thắng trận).
     *
     * @param bonusPoints số điểm thưởng
     */
    public void addBonusScore(int bonusPoints) {
        this.score += bonusPoints;
    }

    public void addDeath() {
        this.deaths++;
    }

    public boolean isWaitingRespawn() {
        return respawnTimer > 0.0;
    }

    public void setRespawnTimer(double seconds) {
        this.respawnTimer = seconds;
    }

    public void reduceRespawnTimer(double dt) {
        this.respawnTimer = Math.max(0.0, this.respawnTimer - dt);
    }

    public boolean isInvulnerable() {
        return invulnerableTimer > 0.0;
    }

    public void setInvulnerableTimer(double seconds) {
        this.invulnerableTimer = seconds;
    }

    public void reduceInvulnerableTimer(double dt) {
        this.invulnerableTimer = Math.max(0.0, this.invulnerableTimer - dt);
    }

    public boolean isDisconnected() {
        return disconnected;
    }

    public void setDisconnected(boolean disconnected) {
        this.disconnected = disconnected;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }
}