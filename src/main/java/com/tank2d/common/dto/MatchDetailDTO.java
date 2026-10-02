package com.tank2d.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MatchDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int matchId;
    private String roomName;
    private int durationSeconds;
    private String playedAt;
    private String winnerUsername;
    private List<MatchParticipantDTO> participants;

    public MatchDetailDTO() {
        this.participants = new ArrayList<>();
    }

    public MatchDetailDTO(int matchId, String roomName, int durationSeconds, String playedAt, String winnerUsername, List<MatchParticipantDTO> participants) {
        this.matchId = matchId;
        this.roomName = roomName;
        this.durationSeconds = durationSeconds;
        this.playedAt = playedAt;
        this.winnerUsername = winnerUsername;
        this.participants = participants != null ? participants : new ArrayList<>();
    }

    public int getMatchId() {
        return matchId;
    }

    public void setMatchId(int matchId) {
        this.matchId = matchId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(String playedAt) {
        this.playedAt = playedAt;
    }

    public String getWinnerUsername() {
        return winnerUsername;
    }

    public void setWinnerUsername(String winnerUsername) {
        this.winnerUsername = winnerUsername;
    }

    public List<MatchParticipantDTO> getParticipants() {
        return participants;
    }

    public void setParticipants(List<MatchParticipantDTO> participants) {
        this.participants = participants;
    }
}
