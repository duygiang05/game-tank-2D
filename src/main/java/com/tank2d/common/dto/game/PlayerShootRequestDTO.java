package com.tank2d.common.dto.game;

/**
 * DTO đại diện cho yêu cầu bắn đạn từ client lên server (PLAYER_SHOOT_REQ).
 */
public class PlayerShootRequestDTO {

    /** Loại đạn: NORMAL hoặc ROCKET (null tương đương NORMAL). */
    private String bulletType;

    public PlayerShootRequestDTO() {}

    /**
     * Khởi tạo yêu cầu bắn đạn theo loại đạn chỉ định.
     *
     * @param bulletType loại đạn ("NORMAL" hoặc "ROCKET")
     */
    public PlayerShootRequestDTO(String bulletType) { 
        this.bulletType = bulletType; 
    }

    public String getBulletType() { 
        return bulletType; 
    }
}