/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tank2d.server.physics;

/**
 *
 * @author Admin
 */
public class TankEntity {
    public enum MoveState { FORWARD, BACKWARD, NONE}
    public enum RotateState { LEFT, RIGHT, NONE}
    
    private final int id;
    private double x;
    private double y;
    private double angle;
    private double speed; 
    private double rotationSpeed;
    
    private MoveState moveState = MoveState.NONE;
    private RotateState rotateState = RotateState.NONE;
    
    public TankEntity(int id, double startX, double startY, double startAngle, double speed, double rotationSpeed) {
        this.id = id;
        this.x = startX;
        this.y = startY;
        this.angle = startAngle;
        this.speed = speed;
        this.rotationSpeed = rotationSpeed;
    }
    
    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    
    public void setMoveState(MoveState state) { this.moveState = state != null ? state : MoveState.NONE; }
    public void setRotateState(RotateState state) { this.rotateState = state != null ? state : RotateState.NONE; }
    
    public void setSpeed(double speed) { this.speed = speed; }
    public void setRotationSpeed(double rotationSpeed) { this.rotationSpeed = rotationSpeed; }
    
    public void update(double deltaTime) {
        if (rotateState == RotateState.LEFT) {
            angle -= rotationSpeed * deltaTime;
        } else if (rotateState == RotateState.RIGHT) {
            angle += rotationSpeed * deltaTime;
        }
        angle = angle % 360.0;
        if (angle < 0) {
            angle += 360.0;
        }
        if (moveState != MoveState.NONE) {
            double radians = Math.toRadians(angle);
            double velocityX = speed * Math.cos(radians) * deltaTime;
            double velocityY = speed * Math.sin(radians) * deltaTime;

            if (moveState == MoveState.FORWARD) {
                x += velocityX;
                y += velocityY;
            } else if (moveState == MoveState.BACKWARD) {
                x -= velocityX;
                y -= velocityY;
            }
        }
    }
}
