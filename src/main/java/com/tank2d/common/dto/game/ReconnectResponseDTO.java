package com.tank2d.common.dto.game;

import com.tank2d.common.dto.RoomDTO;

public class ReconnectResponseDTO {
    private RoomDTO room;
    private int myTankId;
    private double remainingTime;

    public ReconnectResponseDTO() {}

    public ReconnectResponseDTO(RoomDTO room, int myTankId, double remainingTime) {
        this.room = room;
        this.myTankId = myTankId;
        this.remainingTime = remainingTime;
    }

    public RoomDTO getRoom() {
        return room;
    }

    public void setRoom(RoomDTO room) {
        this.room = room;
    }

    public int getMyTankId() {
        return myTankId;
    }

    public void setMyTankId(int myTankId) {
        this.myTankId = myTankId;
    }

    public double getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(double remainingTime) {
        this.remainingTime = remainingTime;
    }
}
