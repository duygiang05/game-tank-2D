package com.tank2d.common.protocol;

public class Packet {
    private PacketType type;
    private String data;

    public Packet() {}

    public Packet(PacketType type, String data) {
        this.type = type;
        this.data = data;
    }

    public PacketType getType() { return type; }
    public void setType(PacketType type) { this.type = type; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}