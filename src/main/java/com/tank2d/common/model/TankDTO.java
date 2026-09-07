package com.tank2d.common.model;

public class TankDTO {
    private String id;
    private double x;
    private double y;
    private double angle;
    private double size = 36.0;

    public TankDTO() {
    }

    public TankDTO(String id, double x, double y, double angle, double size) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.size = size;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }
}