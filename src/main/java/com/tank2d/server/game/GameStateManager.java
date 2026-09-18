package com.tank2d.server.game;

import com.google.gson.Gson;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.game.GameEventEffectDTO;
import com.tank2d.common.dto.game.GameOverDTO;
import com.tank2d.common.protocol.NetworkUtil;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
import com.tank2d.server.dao.UserDAO;
import com.tank2d.server.game.event.CombatEvent;
import com.tank2d.server.game.event.CombatEventListener;
import com.tank2d.server.model.TankEntity;

import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class GameStateManager implements CombatEventListener {
    private static final Logger LOGGER = Logger.getLogger(GameStateManager.class.getName());
    private static final Gson GSON = new Gson();

    private final GameLoop gameLoop;
    private final UserDAO userDAO;
    private final Map<Integer, PlayerCombatState> playerStates = new ConcurrentHashMap<>();
    private final Map<Integer, DataOutputStream> playerSockets = new ConcurrentHashMap<>();

    // --- CÁC BIẾN ĐỌC HOÀN TOÀN TỪ CẤU HÌNH (KHÔNG HARDCODE) ---
    private final int pointsPerHit;
    private final int pointsPerKill;
    private final int pointsWinBonus;

    private final double protectionDuration;
    private final double respawnDelay;
    private final int maxHp;

    private double matchRemainingTime;
    private boolean isGameOver = false;

    // Tọa độ 4 góc Spawn cấu hình từ file Map JSON
    private static class SpawnPoint {
        final double x, y, angle;
        SpawnPoint(double x, double y, double angle) {
            this.x = x;
            this.y = y;
            this.angle = angle;
        }
    }
    private final Map<Integer, SpawnPoint> spawnPoints = new HashMap<>();

    public GameStateManager(GameLoop gameLoop, UserDAO userDAO, double matchDurationSeconds) {
        this.gameLoop = gameLoop;
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
        this.matchRemainingTime = matchDurationSeconds > 0 ? matchDurationSeconds : ConfigLoader.getDefaultMatchDuration();

        // 1. Nạp điểm số từ game.scoring (YAML)
        this.pointsPerHit = ConfigLoader.getPointsPerHit();         // 10
        this.pointsPerKill = ConfigLoader.getPointsPerKill();       // 30
        this.pointsWinBonus = ConfigLoader.getPointsWinBonus();     // 50

        // 2. Nạp chỉ số xe & thời gian bảo hộ từ game.tank (YAML)
        this.maxHp = ConfigLoader.getMaxHp();                       // 3
        this.protectionDuration = ConfigLoader.getGhostDurationSeconds(); // 5.0s

        // 3. Tính thời gian hồi sinh theo mốc trận đấu từ game.respawn_times (YAML)
        if (this.matchRemainingTime <= 45.0) {
            this.respawnDelay = ConfigLoader.getRespawnTime45s();   // 3.0s
        } else if (this.matchRemainingTime <= 60.0) {
            this.respawnDelay = ConfigLoader.getRespawnTime60s();   // 5.0s
        } else {
            this.respawnDelay = ConfigLoader.getRespawnTime90s();   // 7.0s
        }

        // 4. Nạp 4 điểm Spawn từ Map JSON
        initSpawnPoints();
    }

    private void initSpawnPoints() {
        // Tự động load từ MapLoader/ConfigLoader hoặc map JSON hiện hành
        spawnPoints.put(1, new SpawnPoint(ConfigLoader.getSpawnX(1, 20.0), ConfigLoader.getSpawnY(1, 20.0), ConfigLoader.getSpawnAngle(1, 135.0)));
        spawnPoints.put(2, new SpawnPoint(ConfigLoader.getSpawnX(2, 780.0), ConfigLoader.getSpawnY(2, 20.0), ConfigLoader.getSpawnAngle(2, 225.0)));
        spawnPoints.put(3, new SpawnPoint(ConfigLoader.getSpawnX(3, 20.0), ConfigLoader.getSpawnY(3, 780.0), ConfigLoader.getSpawnAngle(3, 45.0)));
        spawnPoints.put(4, new SpawnPoint(ConfigLoader.getSpawnX(4, 780.0), ConfigLoader.getSpawnY(4, 780.0), ConfigLoader.getSpawnAngle(4, 315.0)));
    }

    public void registerPlayer(int tankId, int userId, DataOutputStream dos) {
        playerStates.put(tankId, new PlayerCombatState(tankId, userId));
        if (dos != null) {
            playerSockets.put(tankId, dos);
        }
    }

    public void update(double deltaTime) {
        if (isGameOver) return;

        // 1. Đếm ngược thời gian trận đấu
        matchRemainingTime -= deltaTime;
        if (matchRemainingTime <= 0.0) {
            matchRemainingTime = 0.0;
            triggerGameOver("TIMEOUT");
            return;
        }

        // 2. Cập nhật vòng đời xe
        for (PlayerCombatState state : playerStates.values()) {
            TankEntity tank = gameLoop.getTank(state.getTankId());
            if (tank == null) continue;

            if (tank.isAlive()) {
                // Xe đang sống: trừ dần thời gian bảo hộ 5s
                if (tank.isProtected()) {
                    tank.updateProtection(deltaTime);
                }
            } else if (state.isWaitingRespawn()) {
                // Xe đang chết: đếm ngược hồi sinh
                state.reduceRespawnTimer(deltaTime);
                if (!state.isWaitingRespawn()) {
                    respawnTank(tank, state);
                }
            }
        }
    }

    @Override
    public void onCombatEvent(CombatEvent event) {
        if (isGameOver) return;

        TankEntity targetTank = gameLoop.getTank(event.getTargetTankId());
        TankEntity shooterTank = gameLoop.getTank(event.getShooterId());
        PlayerCombatState targetState = playerStates.get(event.getTargetTankId());
        PlayerCombatState shooterState = playerStates.get(event.getShooterId());

        if (targetTank == null || targetState == null || !targetTank.isAlive()) return;

        // MIỄN NHIỄM KHI BẢO HỘ: Xe đang bảo hộ không bị nhận damage và không gây damage
        if (targetTank.isProtected() || (shooterTank != null && shooterTank.isProtected())) {
            return;
        }

        // Bắn trúng đích -> Cộng điểm hit theo cấu hình
        if (shooterState != null) {
            shooterState.addHit(pointsPerHit);
        }

        int newHp = Math.max(0, targetTank.getHp() - event.getDamage());
        targetTank.setHp(newHp);

        if (newHp == 0) {
            targetTank.setAlive(false);
            targetTank.setProtected(false);
            targetState.addDeath();
            targetState.setRespawnTimer(respawnDelay);

            // Hạ gục đối phương -> Cộng điểm kill theo cấu hình
            if (shooterState != null) {
                shooterState.addKill(pointsPerKill);
            }

            broadcastEffect(new GameEventEffectDTO("EXPLOSION", targetTank.getX(), targetTank.getY(), targetTank.getId()));
            LOGGER.info("[Combat] Tank " + targetTank.getId() + " bị hạ bởi Tank " + event.getShooterId());
        }
    }

    private void respawnTank(TankEntity tank, PlayerCombatState state) {
        tank.setHp(maxHp);
        tank.setAlive(true);

        // Đưa xe về đúng vị trí spawn cấu hình trong Map
        SpawnPoint sp = spawnPoints.getOrDefault(tank.getId(), new SpawnPoint(400.0, 400.0, 0.0));
        tank.setX(sp.x);
        tank.setY(sp.y);
        tank.setAngle(sp.angle);

        // Kích hoạt cờ bảo hộ đọc từ cấu hình
        tank.setProtectionTimer(protectionDuration);
        LOGGER.info("[Combat] Tank " + tank.getId() + " hồi sinh tại góc P" + tank.getId() + " (" + sp.x + ", " + sp.y + ") với bảo hộ " + protectionDuration + "s!");
    }

    // Xử lý nút Thoát giữa trận (ROOM_LEAVE_REQ)
    public void handlePlayerLeave(int tankId) {
        TankEntity tank = gameLoop.getTank(tankId);
        if (tank != null) {
            tank.setAlive(false);
            tank.setHp(0);
        }

        playerStates.remove(tankId);
        playerSockets.remove(tankId);

        LOGGER.info("[Room] Tank " + tankId + " đã thoát ván đấu an toàn.");

        // Nếu phòng chỉ còn lại 1 người -> Trao giải kết thúc trận ngay
        if (playerStates.size() <= 1 && !isGameOver) {
            triggerGameOver("PLAYER_LEFT");
        }
    }

    private void triggerGameOver(String reason) {
        isGameOver = true;
        gameLoop.stopLoop();

        int winnerTankId = -1;
        int highestKills = -1;
        int highestHits = -1;

        Map<Integer, Integer> finalScores = new HashMap<>();
        Map<Integer, Integer> finalKills = new HashMap<>();
        Map<Integer, Integer> finalHits = new HashMap<>();

        for (PlayerCombatState state : playerStates.values()) {
            if (state.getKills() > highestKills) {
                highestKills = state.getKills();
                highestHits = state.getHits();
                winnerTankId = state.getTankId();
            } else if (state.getKills() == highestKills && state.getHits() > highestHits) {
                highestHits = state.getHits();
                winnerTankId = state.getTankId();
            }
        }

        // Cộng điểm thưởng thắng cuộc theo cấu hình YAML
        PlayerCombatState winnerState = playerStates.get(winnerTankId);
        if (winnerState != null) {
            winnerState.addBonusScore(pointsWinBonus);
        }

        for (PlayerCombatState state : playerStates.values()) {
            finalScores.put(state.getTankId(), state.getScore());
            finalKills.put(state.getTankId(), state.getKills());
            finalHits.put(state.getTankId(), state.getHits());
        }

        saveGameResultToDatabase(winnerTankId);

        GameOverDTO dto = new GameOverDTO(winnerTankId, reason, finalScores, finalKills, finalHits);
        broadcastPacket(new Packet(PacketType.GAME_OVER_NOTIFY, GSON.toJson(dto)));
    }

    private void saveGameResultToDatabase(int winnerTankId) {
        if (userDAO == null) return;

        for (PlayerCombatState state : playerStates.values()) {
            try {
                boolean isWin = (state.getTankId() == winnerTankId);
                userDAO.updateMatchStats(
                        state.getUserId(),
                        state.getScore(),
                        state.getKills(),
                        state.getHits(),
                        isWin
                );
            } catch (Exception e) {
                LOGGER.severe("[DB] Lỗi lưu stats UserID " + state.getUserId() + ": " + e.getMessage());
            }
        }
    }

    private void broadcastEffect(GameEventEffectDTO effect) {
        broadcastPacket(new Packet(PacketType.GAME_EVENT_EFFECT, GSON.toJson(effect)));
    }

    private void broadcastPacket(Packet packet) {
        for (DataOutputStream dos : playerSockets.values()) {
            try {
                NetworkUtil.sendPacket(dos, packet);
            } catch (Exception e) {
                LOGGER.warning("[Network] Lỗi broadcast: " + e.getMessage());
            }
        }
    }

    public boolean isGameOver() { return isGameOver; }
    public double getMatchRemainingTime() { return matchRemainingTime; }
    public double getRespawnDelay() { return respawnDelay; }
}