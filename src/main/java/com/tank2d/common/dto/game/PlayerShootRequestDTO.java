package com.tank2d.common.dto.game;

/** Payload mở rộng cho PLAYER_SHOOT_REQ — client CHỌN loại đạn muốn bắn, server tự quyết định có cho phép không. */
public class PlayerShootRequestDTO {
    private String bulletType; // "NORMAL" | "ROCKET", có thể null -> mặc định NORMAL

    public PlayerShootRequestDTO() {}
    public PlayerShootRequestDTO(String bulletType) { this.bulletType = bulletType; }

    public String getBulletType() { return bulletType; }
}