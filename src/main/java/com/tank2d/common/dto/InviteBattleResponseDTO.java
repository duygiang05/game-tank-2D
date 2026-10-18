package com.tank2d.common.dto;

/**
 * DTO phản hồi lời mời thách đấu (Player B -> Server).
 */
public class InviteBattleResponseDTO {
    private int inviterUserId;
    private boolean accepted;

    public InviteBattleResponseDTO() {}

    public InviteBattleResponseDTO(int inviterUserId, boolean accepted) {
        this.inviterUserId = inviterUserId;
        this.accepted = accepted;
    }

    public int getInviterUserId() { return inviterUserId; }
    public void setInviterUserId(int inviterUserId) { this.inviterUserId = inviterUserId; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
}
