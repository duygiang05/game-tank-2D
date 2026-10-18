package com.tank2d.common.dto;

/**
 * DTO yêu cầu gửi lời mời thách đấu (Player A -> Server).
 */
public class InviteBattleRequestDTO {
    private int targetUserId;

    public InviteBattleRequestDTO() {}

    public InviteBattleRequestDTO(int targetUserId) {
        this.targetUserId = targetUserId;
    }

    public int getTargetUserId() { return targetUserId; }
    public void setTargetUserId(int targetUserId) { this.targetUserId = targetUserId; }
}
