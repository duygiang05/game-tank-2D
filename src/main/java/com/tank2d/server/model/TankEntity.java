package com.tank2d.server.model;

public class TankEntity {
    public enum MoveState { FORWARD, BACKWARD, NONE }
    public enum RotateState { LEFT, RIGHT, NONE }

    private long lastShotTimeMillis = 0L;
    
    private final int id;
    private double x;
    private double y;
    private double angle;
    private double speed;
    private double rotationSpeed;
    private int hp;
    private boolean isAlive = true;

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
    public double getSpeed() { return speed; }
    public double getRotationSpeed() { return rotationSpeed; }
    public MoveState getMoveState() { return moveState; }
    public RotateState getRotateState() { return rotateState; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setSpeed(double speed) { this.speed = speed; }
    public void setRotationSpeed(double rotationSpeed) { this.rotationSpeed = rotationSpeed; }
    public void setMoveState(MoveState state) { this.moveState = state != null ? state : MoveState.NONE; }
    public void setRotateState(RotateState state) { this.rotateState = state != null ? state : RotateState.NONE; }
    
    public boolean canShoot(long cooldownMs, long nowMillis) {
        return (nowMillis - lastShotTimeMillis) >= cooldownMs;
    }
    public void registerShot(long nowMillis) {
        this.lastShotTimeMillis = nowMillis;
    }
    public void initHp(int maxHp) {
        this.hp = maxHp;
        this.isAlive = true;
    }
    public int getHp() { return hp; }
    public boolean isAlive() { return isAlive; }
    public void takeDamage(int amount) {
        if (!isAlive) return; // xe đã chết, không trừ thêm
        hp -= amount;
        if (hp <= 0) {
            hp = 0;
            isAlive = false;
        }
    }
}