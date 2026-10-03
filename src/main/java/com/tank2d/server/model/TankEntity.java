package com.tank2d.server.model;

/**
 * Thực thể biểu diễn một chiếc xe tăng trong thế giới game 2D.
 */
public class TankEntity {

    public enum MoveState { FORWARD, BACKWARD, NONE }
    public enum RotateState { LEFT, RIGHT, NONE }

    private final int id;
    private double x;
    private double y;
    private double angle;
    private double speed;
    private double rotationSpeed;

    private MoveState moveState = MoveState.NONE;
    private RotateState rotateState = RotateState.NONE;

    private int hp;
    private boolean alive = true;
    private long lastShotTimeMillis = 0L;

    private boolean isProtected = false;
    private double protectionTimer = 0.0;

    private long shieldActiveUntilMillis = 0L;
    private long nitroActiveUntilMillis = 0L;
    private long rocketBuffActiveUntilMillis = 0L;

    /**
     * Khởi tạo một thực thể xe tăng mới với các thông số ban đầu.
     *
     * @param id            mã định danh xe tăng
     * @param startX        tọa độ khởi tạo X
     * @param startY        tọa độ khởi tạo Y
     * @param startAngle    góc quay ban đầu tính bằng độ [0, 360)
     * @param speed         tốc độ di chuyển cơ bản (pixel/s)
     * @param rotationSpeed tốc độ quay thân xe (độ/s)
     */
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
    public int getHp() { return hp; }
    public boolean isAlive() { return alive; }
    public long getLastShotTimeMillis() { return lastShotTimeMillis; }

    public boolean isProtected() { return isProtected; }
    public double getProtectionTimer() { return protectionTimer; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setSpeed(double speed) { this.speed = speed; }
    public void setRotationSpeed(double rotationSpeed) { this.rotationSpeed = rotationSpeed; }
    public void setMoveState(MoveState state) { this.moveState = state != null ? state : MoveState.NONE; }
    public void setRotateState(RotateState state) { this.rotateState = state != null ? state : RotateState.NONE; }
    public void setHp(int hp) { this.hp = hp; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public void setLastShotTimeMillis(long t) { this.lastShotTimeMillis = t; }

    public void setProtected(boolean isProtected) {
        this.isProtected = isProtected;
    }

    public void setProtectionTimer(double timer) {
        this.protectionTimer = Math.max(0.0, timer);
        this.isProtected = (this.protectionTimer > 0.0);
    }

    public long getShieldActiveUntilMillis() { return shieldActiveUntilMillis; }
    public long getNitroActiveUntilMillis() { return nitroActiveUntilMillis; }
    public long getRocketBuffActiveUntilMillis() { return rocketBuffActiveUntilMillis; }

    public void setShieldActiveUntilMillis(long t) { this.shieldActiveUntilMillis = t; }
    public void setNitroActiveUntilMillis(long t) { this.nitroActiveUntilMillis = t; }
    public void setRocketBuffActiveUntilMillis(long t) { this.rocketBuffActiveUntilMillis = t; }
}