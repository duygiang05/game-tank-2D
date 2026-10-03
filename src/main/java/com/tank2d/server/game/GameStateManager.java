package com.tank2d.server.game;

import com.google.gson.Gson;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.game.GameEventEffectDTO;
import com.tank2d.common.dto.game.GameOverDTO;
import com.tank2d.common.dto.game.MapUpdateDTO;
import com.tank2d.common.exception.GameNetworkException;
import com.tank2d.common.protocol.NetworkUtil;
import com.tank2d.common.protocol.Packet;
import com.tank2d.common.protocol.PacketType;
import com.tank2d.server.dao.MatchDAO;
import com.tank2d.server.dao.UserDAO;
import com.tank2d.server.game.event.CombatEvent;
import com.tank2d.server.game.event.CombatEventListener;
import com.tank2d.server.game.event.ItemEventListener;
import com.tank2d.server.game.event.ItemPickupEvent;
import com.tank2d.server.game.event.MapChangeEvent;
import com.tank2d.server.game.event.MapChangeListener;
import com.tank2d.server.model.TankEntity;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Quản lý trạng thái trận đấu (Game State Manager).
 * <p>
 * Đảm nhiệm các chức năng cốt lõi:
 * <ul>
 *     <li>Theo dõi thời gian thi đấu đếm ngược và kiểm soát điều kiện kết thúc trận (Game Over).</li>
 *     <li>Quản lý vòng đời hồi sinh và thời gian bảo hộ bất tử (Ghost) của từng xe tăng.</li>
 *     <li>Lắng nghe và giải quyết các sự kiện chiến đấu: trúng đạn, phá khiên, hạ gục, bắn trúng tường.</li>
 *     <li>Xử lý sự kiện thoát trận (chủ động đầu hàng hoặc ngắt kết nối đột ngột/AFK) và cơ chế Reconnect.</li>
 *     <li>Tổng kết xếp hạng người chơi, phân định thắng/thua/hòa và ghi nhận lịch sử vào cơ sở dữ liệu.</li>
 * </ul>
 */
public class GameStateManager implements CombatEventListener, MapChangeListener, ItemEventListener {

    private static final Logger LOGGER = Logger.getLogger(GameStateManager.class.getName());
    private static final Gson GSON = new Gson();

    private final GameLoop gameLoop;
    private final UserDAO userDAO;
    private final MatchDAO matchDAO;
    private final Map<Integer, PlayerCombatState> playerStates = new ConcurrentHashMap<>();
    private final List<PlayerCombatState> allMatchParticipants = new ArrayList<>();
    private final Map<Integer, DataOutputStream> playerSockets = new ConcurrentHashMap<>();

    private final int pointsPerHit;
    private final int pointsPerKill;
    private final int pointsWinBonus;

    private final double protectionDuration;
    private final double respawnDelay;
    private final int maxHp;

    private double matchRemainingTime;
    private boolean isGameOver = false;

    /**
     * Điểm xuất hiện (Spawn Point) trên bản đồ cho từng vị trí xe tăng.
     */
    private static class SpawnPoint {
        final double x;
        final double y;
        final double angle;

        SpawnPoint(double x, double y, double angle) {
            this.x = x;
            this.y = y;
            this.angle = angle;
        }
    }

    private final Map<Integer, SpawnPoint> spawnPoints = new HashMap<>();
    private Runnable onGameOverCallback;

    public void setOnGameOverCallback(Runnable onGameOverCallback) {
        this.onGameOverCallback = onGameOverCallback;
    }

    /**
     * Khởi tạo trình quản lý trận đấu với các tham số cấu hình.
     *
     * @param gameLoop             luồng lặp vật lý chính của trận đấu
     * @param userDAO              đối tượng truy xuất dữ liệu người dùng
     * @param matchDAO             đối tượng truy xuất và lưu trữ lịch sử trận đấu
     * @param matchDurationSeconds thời lượng trận đấu tính bằng giây
     */
    public GameStateManager(GameLoop gameLoop, UserDAO userDAO, MatchDAO matchDAO, double matchDurationSeconds) {
        this.gameLoop = gameLoop;
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
        this.matchDAO = matchDAO != null ? matchDAO : new MatchDAO();

        this.matchRemainingTime = matchDurationSeconds > 0 ? matchDurationSeconds : ConfigLoader.getDefaultMatchDuration();

        this.pointsPerHit = ConfigLoader.getPointsPerHit();
        this.pointsPerKill = ConfigLoader.getPointsPerKill();
        this.pointsWinBonus = ConfigLoader.getPointsWinBonus();

        this.maxHp = ConfigLoader.getMaxHp();
        this.protectionDuration = ConfigLoader.getGhostDurationSeconds();

        if (this.matchRemainingTime <= 45.0) {
            this.respawnDelay = ConfigLoader.getRespawnTime45s();
        } else if (this.matchRemainingTime <= 60.0) {
            this.respawnDelay = ConfigLoader.getRespawnTime60s();
        } else if (this.matchRemainingTime <= 90.0) {
            this.respawnDelay = ConfigLoader.getRespawnTime90s();
        } else {
            this.respawnDelay = ConfigLoader.getRespawnTime180s();
        }

        initSpawnPoints();
    }

    /**
     * Khởi tạo các điểm xuất hiện (Spawn Points) cho 4 góc bản đồ từ cấu hình.
     */
    private void initSpawnPoints() {
        int tileSize = ConfigLoader.getTileSize();
        int cols = ConfigLoader.getMapCols();
        int rows = ConfigLoader.getMapRows();

        double minCoord = tileSize / 2.0;
        double maxCoordX = (cols - 1) * tileSize + tileSize / 2.0;
        double maxCoordY = (rows - 1) * tileSize + tileSize / 2.0;

        spawnPoints.put(1, new SpawnPoint(ConfigLoader.getSpawnX(1, minCoord),  ConfigLoader.getSpawnY(1, minCoord),  ConfigLoader.getSpawnAngle(1, 135.0)));
        spawnPoints.put(2, new SpawnPoint(ConfigLoader.getSpawnX(2, maxCoordX), ConfigLoader.getSpawnY(2, minCoord),  ConfigLoader.getSpawnAngle(2, 225.0)));
        spawnPoints.put(3, new SpawnPoint(ConfigLoader.getSpawnX(3, minCoord),  ConfigLoader.getSpawnY(3, maxCoordY), ConfigLoader.getSpawnAngle(3, 45.0)));
        spawnPoints.put(4, new SpawnPoint(ConfigLoader.getSpawnX(4, maxCoordX), ConfigLoader.getSpawnY(4, maxCoordY), ConfigLoader.getSpawnAngle(4, 315.0)));
    }

    /**
     * Đăng ký người chơi mới vào trận đấu.
     *
     * @param tankId ID xe tăng trong trận
     * @param userId ID tài khoản người dùng
     * @param dos    kênh socket gửi dữ liệu tới client
     */
    public void registerPlayer(int tankId, int userId, DataOutputStream dos) {
        PlayerCombatState state = new PlayerCombatState(tankId, userId);
        playerStates.put(tankId, state);
        allMatchParticipants.add(state);
        if (dos != null) {
            playerSockets.put(tankId, dos);
        }
    }

    /**
     * Cập nhật logic tiến trình trận đấu trong mỗi tick vật lý.
     *
     * @param deltaTime khoảng thời gian trôi qua giữa 2 tick (giây)
     */
    public void update(double deltaTime) {
        if (isGameOver) {
            return;
        }

        matchRemainingTime -= deltaTime;
        if (matchRemainingTime <= 0.0) {
            matchRemainingTime = 0.0;
            triggerGameOver("TIMEOUT");
            return;
        }

        for (PlayerCombatState state : playerStates.values()) {
            TankEntity tank = gameLoop.getTank(state.getTankId());
            if (tank == null) {
                continue;
            }

            if (!tank.isAlive() && state.isWaitingRespawn()) {
                state.reduceRespawnTimer(deltaTime);
                if (!state.isWaitingRespawn()) {
                    respawnTank(tank, state);
                }
            }
        }
    }

    @Override
    public void onCombatEvent(CombatEvent event) {
        if (isGameOver) {
            return;
        }

        if (event.getType() == CombatEvent.EventType.WALL_HIT) {
            int row = event.getTargetTankId() / 10000;
            int col = event.getTargetTankId() % 10000;
            double tileSize = ConfigLoader.getTileSize();
            double cx = col * tileSize + tileSize / 2.0;
            double cy = row * tileSize + tileSize / 2.0;

            broadcastEffect(new GameEventEffectDTO("WALL_HIT", cx, cy, -1));
            return;
        }

        if (event.getType() == CombatEvent.EventType.SHIELD_BLOCKED) {
            return;
        }

        TankEntity targetTank = gameLoop.getTank(event.getTargetTankId());
        TankEntity shooterTank = gameLoop.getTank(event.getShooterId());
        PlayerCombatState targetState = playerStates.get(event.getTargetTankId());
        PlayerCombatState shooterState = playerStates.get(event.getShooterId());

        if (targetTank == null || targetState == null || !targetTank.isAlive()) {
            return;
        }

        if (event.getType() == CombatEvent.EventType.SHIELD_BROKEN) {
            targetTank.setShieldActiveUntilMillis(0L);
        }

        if (targetTank.isProtected() || (shooterTank != null && shooterTank.isProtected())) {
            return;
        }

        if (shooterState != null) {
            shooterState.addHit(pointsPerHit);
        }

        if (shooterTank != null && shooterTank.getRocketBuffActiveUntilMillis() > 0L) {
            shooterTank.setRocketBuffActiveUntilMillis(0L);
        }

        int newHp = Math.max(0, targetTank.getHp() - event.getDamage());
        targetTank.setHp(newHp);

        if (newHp == 0) {
            targetTank.setAlive(false);
            targetTank.setProtected(false);
            targetState.addDeath();
            targetState.setRespawnTimer(respawnDelay);

            if (shooterState != null) {
                shooterState.addKill(pointsPerKill);
            }

            broadcastEffect(new GameEventEffectDTO("EXPLOSION", targetTank.getX(), targetTank.getY(), targetTank.getId()));
            LOGGER.info("[Combat] Tank " + targetTank.getId() + " bị hạ bởi Tank " + event.getShooterId());
        }
    }

    /**
     * Hồi sinh xe tăng tại điểm xuất phát tương ứng và cấp thời gian bảo hộ bất tử.
     *
     * @param tank  xe tăng hồi sinh
     * @param state trạng thái thi đấu của người chơi
     */
    private void respawnTank(TankEntity tank, PlayerCombatState state) {
        tank.setHp(maxHp);
        tank.setAlive(true);

        SpawnPoint sp = spawnPoints.getOrDefault(tank.getId(), new SpawnPoint(400.0, 400.0, 0.0));
        tank.setX(sp.x);
        tank.setY(sp.y);
        tank.setAngle(sp.angle);

        tank.setProtectionTimer(protectionDuration);
        LOGGER.info("[Combat] Tank " + tank.getId() + " hồi sinh tại góc P" + tank.getId() + " (" + sp.x + ", " + sp.y + ") với bảo hộ " + protectionDuration + "s!");
    }

    /**
     * Xử lý khi người chơi chủ động bấm nút Thoát giữa trận (đầu hàng).
     * Xe bị nổ ngay lập tức, bị loại vĩnh viễn khỏi ván đấu hiện tại.
     *
     * @param tankId ID xe tăng thoát trận
     */
    public synchronized void handlePlayerSurrender(int tankId) {
        TankEntity tank = gameLoop.getTank(tankId);
        if (tank != null) {
            broadcastEffect(new GameEventEffectDTO("EXPLOSION", tank.getX(), tank.getY(), tankId));
            tank.setAlive(false);
            tank.setHp(0);
        }

        PlayerCombatState state = playerStates.get(tankId);
        if (state != null) {
            state.setEliminated(true);
            state.setRespawnTimer(0.0);
        }

        playerSockets.remove(tankId);
        LOGGER.info("[Room] Tank " + tankId + " đã chủ động thoát trận và bị loại khỏi cuộc chơi.");

        long activeCount = playerStates.values().stream()
                .filter(s -> !s.isEliminated())
                .count();

        if (activeCount <= 1 && !isGameOver) {
            triggerGameOver("PLAYER_LEFT");
        }
    }

    /**
     * Xử lý khi người chơi mất kết nối mạng đột ngột hoặc tắt app bằng nút 'X'.
     * Xe sẽ chuyển sang trạng thái AFK đứng yên nhưng vẫn ở lại trong trận để cho phép Reconnect.
     *
     * @param tankId ID xe tăng mất kết nối
     */
    public synchronized void handlePlayerDisconnected(int tankId) {
        TankEntity tank = gameLoop.getTank(tankId);
        if (tank != null) {
            tank.setMoveState(TankEntity.MoveState.NONE);
            tank.setRotateState(TankEntity.RotateState.NONE);
        }

        PlayerCombatState state = playerStates.get(tankId);
        if (state != null) {
            state.setDisconnected(true);
        }

        playerSockets.remove(tankId);
        LOGGER.info("[Room] Tank " + tankId + " mất kết nối đột ngột -> chuyển sang chế độ AFK, xe vẫn ở lại trận đấu.");
    }

    /**
     * Xử lý tái kết nối (Reconnect) vào trận đấu đang diễn ra.
     *
     * @param tankId ID xe tăng cần khôi phục kết nối
     * @param newDos kênh truyền socket mới của người chơi
     * @return {@code true} nếu khôi phục thành công, {@code false} nếu người chơi đã bị loại hoặc trận đã kết thúc
     */
    public synchronized boolean reconnectPlayer(int tankId, DataOutputStream newDos) {
        PlayerCombatState state = playerStates.get(tankId);
        if (state != null && !state.isEliminated() && !isGameOver) {
            state.setDisconnected(false);
            if (newDos != null) {
                playerSockets.put(tankId, newDos);
            }
            LOGGER.info("[Combat] Tank " + tankId + " đã reconnect thành công vào trận đấu!");
            return true;
        }
        return false;
    }

    /**
     * Kiểm tra xem một người chơi có đang trong trạng thái mất kết nối (AFK) hay không.
     *
     * @param tankId mã số xe tăng
     * @return {@code true} nếu đang mất kết nối
     */
    public boolean isPlayerDisconnected(int tankId) {
        PlayerCombatState state = playerStates.get(tankId);
        return state != null && state.isDisconnected();
    }

    /**
     * Hủy đăng ký người chơi khỏi trận đấu và dọn dẹp kết nối socket.
     *
     * @param tankId mã số xe tăng
     */
    public void unregisterPlayer(int tankId) {
        playerStates.remove(tankId);
        playerSockets.remove(tankId);
    }

    /**
     * Kích hoạt kết thúc trận đấu, tổng kết kết quả, xếp hạng và lưu trữ vào CSDL.
     *
     * @param reason lý do kết thúc ("TIMEOUT", "PLAYER_LEFT", ...)
     */
    private void triggerGameOver(String reason) {
        if (isGameOver) {
            return;
        }
        isGameOver = true;
        gameLoop.stopLoop();

        List<PlayerCombatState> players = new ArrayList<>(playerStates.values());

        boolean isDraw = !"PLAYER_LEFT".equals(reason)
                && !players.isEmpty()
                && players.stream().allMatch(p -> p.getKills() == 0 && p.getHits() == 0);

        int winnerTankId = -1;

        if ("PLAYER_LEFT".equals(reason) && !players.isEmpty()) {
            List<PlayerCombatState> activeSurvivors = players.stream()
                    .filter(p -> !p.isEliminated())
                    .toList();
            if (!activeSurvivors.isEmpty()) {
                PlayerCombatState survivor = activeSurvivors.get(0);
                winnerTankId = survivor.getTankId();
                survivor.addBonusScore(pointsWinBonus);
            }
        } else if (!isDraw && !players.isEmpty()) {
            players.sort((p1, p2) -> {
                if (p1.isEliminated() != p2.isEliminated()) {
                    return p1.isEliminated() ? 1 : -1;
                }
                if (p2.getKills() != p1.getKills()) {
                    return Integer.compare(p2.getKills(), p1.getKills());
                }
                if (p2.getHits() != p1.getHits()) {
                    return Integer.compare(p2.getHits(), p1.getHits());
                }
                if (p1.getKills() > 0 && p1.getFirstKillTimeMillis() != p2.getFirstKillTimeMillis()) {
                    return Long.compare(p1.getFirstKillTimeMillis(), p2.getFirstKillTimeMillis());
                }
                return Long.compare(p1.getFirstHitTimeMillis(), p2.getFirstHitTimeMillis());
            });

            for (PlayerCombatState candidate : players) {
                if (!candidate.isEliminated()) {
                    winnerTankId = candidate.getTankId();
                    candidate.addBonusScore(pointsWinBonus);
                    break;
                }
            }
        }

        Map<Integer, Integer> finalScores = new HashMap<>();
        Map<Integer, Integer> finalKills = new HashMap<>();
        Map<Integer, Integer> finalHits = new HashMap<>();

        for (PlayerCombatState state : players) {
            finalScores.put(state.getTankId(), state.getScore());
            finalKills.put(state.getTankId(), state.getKills());
            finalHits.put(state.getTankId(), state.getHits());
        }

        String finalReason = isDraw ? "DRAW" : reason;
        GameOverDTO dto = new GameOverDTO(winnerTankId, finalReason, finalScores, finalKills, finalHits);
        broadcastPacket(new Packet(PacketType.GAME_OVER_NOTIFY, GSON.toJson(dto)));
        LOGGER.info("[Game] Đã broadcast GAME_OVER_NOTIFY thành công tới toàn bộ client!");

        try {
            saveMatchResultToDatabase(winnerTankId, isDraw, players);
        } catch (GameNetworkException e) {
            LOGGER.severe("[DB] Lỗi cơ sở dữ liệu (" + e.getErrorCode() + "): " + e.getMessage());
        } catch (Exception e) {
            LOGGER.severe("[DB] Lỗi lưu kết quả trận đấu: " + e.getMessage());
        }

        if (onGameOverCallback != null) {
            try {
                onGameOverCallback.run();
                LOGGER.info("[GameStateManager] Đã kích hoạt Callback reset trạng thái phòng!");
            } catch (Exception e) {
                LOGGER.severe("[GameStateManager] Lỗi khi gọi onGameOverCallback: " + e.getMessage());
            }
        }
    }

    /**
     * Ghi nhận kết quả trận đấu, cập nhật thống kê người dùng và lịch sử đối đầu vào CSDL.
     *
     * @param winnerTankId mã xe chiến thắng
     * @param isDraw       kết quả hòa
     * @param players      danh sách người chơi
     */
    private void saveMatchResultToDatabase(int winnerTankId, boolean isDraw, List<PlayerCombatState> players) {
        Integer winnerUserId = null;

        for (PlayerCombatState state : players) {
            boolean isWin = (!isDraw && !state.isEliminated() && state.getTankId() == winnerTankId);
            if (isWin) {
                winnerUserId = state.getUserId();
            }

            if (userDAO != null && state.getUserId() > 0) {
                userDAO.updateMatchStats(
                        state.getUserId(),
                        state.getScore(),
                        state.getKills(),
                        state.getHits(),
                        isWin
                );
            } else {
                LOGGER.warning("[DB] Bỏ qua updateMatchStats vì UserID không hợp lệ: " + state.getUserId());
            }
        }

        if (matchDAO != null) {
            int duration = (int) (ConfigLoader.getDefaultMatchDuration() - this.matchRemainingTime);
            String roomName = "Room " + (gameLoop != null ? "Game" : "1");

            allMatchParticipants.sort((p1, p2) -> {
                if (p1.getTankId() == winnerTankId) return -1;
                if (p2.getTankId() == winnerTankId) return 1;
                return Integer.compare(p2.getScore(), p1.getScore());
            });

            matchDAO.recordMatchResult(roomName, winnerUserId, duration, allMatchParticipants);
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

    /**
     * Kiểm tra xem trận đấu đã kết thúc hay chưa.
     *
     * @return {@code true} nếu đã kết thúc
     */
    public boolean isGameOver() {
        return isGameOver;
    }

    /**
     * Lấy thời gian còn lại của trận đấu (tính bằng giây).
     *
     * @return số giây còn lại
     */
    public double getMatchRemainingTime() {
        return matchRemainingTime;
    }

    /**
     * Lấy độ trễ hồi sinh sau khi xe bị tiêu diệt (giây).
     *
     * @return thời gian hồi sinh (giây)
     */
    public double getRespawnDelay() {
        return respawnDelay;
    }

    @Override
    public void onMapChanged(MapChangeEvent event) {
        MapUpdateDTO dto = new MapUpdateDTO(event.getRow(), event.getCol(), event.getNewTileCode());
        broadcastPacket(new Packet(PacketType.MAP_UPDATE, GSON.toJson(dto)));

        double tileSize = ConfigLoader.getTileSize();
        double centerX = event.getCol() * tileSize + tileSize / 2.0;
        double centerY = event.getRow() * tileSize + tileSize / 2.0;
        broadcastEffect(new GameEventEffectDTO("WALL_BREAK", centerX, centerY, -1));
    }

    @Override
    public void onItemPickup(ItemPickupEvent event) {
        TankEntity tank = gameLoop.getTank(event.getTankId());
        if (tank == null || !tank.isAlive() || tank.isProtected()) {
            return;
        }
        long now = System.currentTimeMillis();

        switch (event.getItemType()) {
            case SHIELD -> tank.setShieldActiveUntilMillis(now + (long) (ConfigLoader.getShieldDurationSeconds() * 1000));
            case NITRO -> tank.setNitroActiveUntilMillis(now + (long) (ConfigLoader.getNitroDurationSeconds() * 1000));
            case ROCKET_AMMO -> tank.setRocketBuffActiveUntilMillis(now + (long) (ConfigLoader.getMissileBuffDurationSeconds() * 1000));
            case HEALTH_PACK -> tank.setHp(Math.min(maxHp, tank.getHp() + ConfigLoader.getHealAmount()));
        }
        broadcastEffect(new GameEventEffectDTO("ITEM_PICKUP_" + event.getItemType(), tank.getX(), tank.getY(), tank.getId()));
    }
}
