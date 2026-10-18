package com.tank2d.common.dto;

/**
 * DTO thông báo lời mời thách đấu (Server -> Player B).
 */
public class InviteBattleNotifyDTO {
    private int inviterUserId;
    private String inviterUsername;

    public InviteBattleNotifyDTO() {}

    public InviteBattleNotifyDTO(int inviterUserId, String inviterUsername) {
        this.inviterUserId = inviterUserId;
        this.inviterUsername = inviterUsername;
    }

    public int getInviterUserId() { return inviterUserId; }
    public void setInviterUserId(int inviterUserId) { this.inviterUserId = inviterUserId; }

    public String getInviterUsername() { return inviterUsername; }
    public void setInviterUsername(String inviterUsername) { this.inviterUsername = inviterUsername; }
}
