package com.tank2d.common.dto.game;

public class PlayerStatsDTO {
    private int tankId;
    private String username;
    private int kills;
    private int hits;
    private int hp;

    public PlayerStatsDTO(int tankId, String username, int kills, int hits, int hp) {
        this.tankId = tankId;
        this.username = username;
        this.kills = kills;
        this.hits = hits;
        this.hp = hp;
    }

    public int getTankId() { return tankId; }
    public String getUsername() { return username; }
    public int getKills() { return kills; }
    public int getHits() { return hits; }
    public int getHp() { return hp; }
}