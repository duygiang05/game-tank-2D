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

    // Điểm thưởng cho mỗi hành động
    private static final int POINTS_PER_HIT = 10;
    private static final int POINTS_PER_KILL = 100;
    private static final int POINTS_WIN_BONUS = 50;

    private final GameLoop gameLoop;
    private final UserDAO userDAO;
    private final Map<Integer, PlayerCombatState> playerStates = new ConcurrentHashMap<>();
    private final Map<Integer, DataOutputStream> playerSockets = new ConcurrentHashMap<>();

    private double matchRemainingTime;
    private final double respawnDelay = 3.0;
    private final double ghostDuration;
    private final int maxHp;
    private boolean isGameOver = false;

    public GameStateManager(GameLoop gameLoop, UserDAO userDAO, double matchDurationSeconds) {
        this.gameLoop = gameLoop;
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
        this.matchRemainingTime = matchDurationSeconds > 0 ? matchDurationSeconds : 180.0;
        this.ghostDuration = ConfigLoader.getGhostDurationSeconds();
        this.maxHp = ConfigLoader.getMaxHp();
    }

    public void registerPlayer(int tankId, int userId, DataOutputStream dos) {
        playerStates.put(tankId, new PlayerCombatState(tankId, userId));
        if (dos != null) {
            playerSockets.put(tankId, dos);
        }
    }

    //đếm ngược thời gian trận đấu
    public void update(double deltaTime) {
        if (isGameOver) return;

        matchRemainingTime -= deltaTime;
        if (matchRemainingTime <= 0) {
            matchRemainingTime = 0;
            triggerGameOver("TIMEOUT"); //hết giờ thì dừng
            return;
        }

        //đếm ngược thời gian hồi sinh từng người chơi
        for (PlayerCombatState state : playerStates.values()) {
            TankEntity tank = gameLoop.getTank(state.getTankId());
            if (tank == null) continue;

            if (state.isWaitingRespawn()) {
                state.reduceRespawnTimer(deltaTime); //giảm tgian
                if (!state.isWaitingRespawn()) {
                    respawnTank(tank, state); // hết tgian thì hồi sinh
                }
            }

            if (state.isInvulnerable()) {
                state.reduceInvulnerableTimer(deltaTime); //giảm tgian khiên bất tử
            }
        }
    }

    @Override
    public void onCombatEvent(CombatEvent event) {
        if (isGameOver) return;

        TankEntity targetTank = gameLoop.getTank(event.getTargetTankId());
        PlayerCombatState targetState = playerStates.get(event.getTargetTankId());
        PlayerCombatState shooterState = playerStates.get(event.getShooterId());

        if (targetTank == null || targetState == null || !targetTank.isAlive()) return;
        if (targetState.isInvulnerable()) return;

        // Bắn trúng: tăng hit và cộng điểm thưởng
        if (shooterState != null) {
            shooterState.addHit(POINTS_PER_HIT);
        }

        int newHp = Math.max(0, targetTank.getHp() - event.getDamage());
        targetTank.setHp(newHp);

        if (newHp == 0) {
            targetTank.setAlive(false);
            targetState.addDeath();
            targetState.setRespawnTimer(respawnDelay);

            // Hạ gục: tăng kill và cộng điểm hạ gục
            if (shooterState != null) {
                shooterState.addKill(POINTS_PER_KILL);
            }

            broadcastEffect(new GameEventEffectDTO("EXPLOSION", targetTank.getX(), targetTank.getY(), targetTank.getId()));
            LOGGER.info("[Combat] Tank " + targetTank.getId() + " bị tiêu diệt bởi Tank " + event.getShooterId());
        }
    }

    private void respawnTank(TankEntity tank, PlayerCombatState state) {
        tank.setHp(maxHp);
        tank.setAlive(true);

        if (tank.getId() == 1) {
            tank.setX(100);
            tank.setY(100);
            tank.setAngle(0);
        } else {
            tank.setX(600);
            tank.setY(600);
            tank.setAngle(180);
        }

        state.setInvulnerableTimer(ghostDuration);
        LOGGER.info("[Combat] Tank " + tank.getId() + " đã hồi sinh!");
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

        // Xác định người thắng theo kills -> hits
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

        // Thưởng thêm điểm cho người thắng cuộc
        PlayerCombatState winnerState = playerStates.get(winnerTankId);
        if (winnerState != null) {
            winnerState.addBonusScore(POINTS_WIN_BONUS);
        }

        for (PlayerCombatState state : playerStates.values()) {
            finalScores.put(state.getTankId(), state.getScore());
            finalKills.put(state.getTankId(), state.getKills());
            finalHits.put(state.getTankId(), state.getHits());
        }

        LOGGER.info("[Game Over] Lý do: " + reason + " | Người thắng: Tank " + winnerTankId);

        // Lưu vào MySQL XAMPP (khớp chính xác với cột total_points trong DB)
        saveGameResultToDatabase(winnerTankId);

        // Gửi kết quả cho Client
        GameOverDTO dto = new GameOverDTO(winnerTankId, reason, finalScores, finalKills, finalHits);
        broadcastPacket(new Packet(PacketType.GAME_OVER_NOTIFY, GSON.toJson(dto)));
    }

    private void saveGameResultToDatabase(int winnerTankId) {
        if (userDAO == null) return;

        for (PlayerCombatState state : playerStates.values()) {
            try {
                boolean isWin = (state.getTankId() == winnerTankId);
                // Truyền trực tiếp state.getScore() vào tham số pointsEarned
                userDAO.updateMatchStats(
                        state.getUserId(),
                        state.getScore(),
                        state.getKills(),
                        state.getHits(),
                        isWin
                );
                LOGGER.info("[DB] Cập nhật stats UserID: " + state.getUserId() 
                        + " | Points: " + state.getScore() 
                        + " | Kills: " + state.getKills() 
                        + " | Hits: " + state.getHits() 
                        + " | Win: " + isWin);
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
}