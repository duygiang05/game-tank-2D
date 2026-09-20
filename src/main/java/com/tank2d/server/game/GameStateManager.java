package com.tank2d.server.game;

import com.google.gson.Gson;
import com.tank2d.common.config.ConfigLoader;
import com.tank2d.common.dto.game.GameEventEffectDTO;
import com.tank2d.common.dto.game.GameOverDTO;
import com.tank2d.common.dto.game.MapUpdateDTO;
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

public class GameStateManager implements CombatEventListener, MapChangeListener, ItemEventListener {
    private static final Logger LOGGER = Logger.getLogger(GameStateManager.class.getName());
    private static final Gson GSON = new Gson();

    private final GameLoop gameLoop;
    private final UserDAO userDAO;
    private final MatchDAO matchDAO ;
    private final Map<Integer, PlayerCombatState> playerStates = new ConcurrentHashMap<>();
    // Giữ lại trạng thái của tất cả người chơi trong ván để lưu lịch sử match_participants
    private final List<PlayerCombatState> allMatchParticipants = new ArrayList<>();
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

    public GameStateManager(GameLoop gameLoop, UserDAO userDAO, MatchDAO matchDAO, double matchDurationSeconds) {
        this.gameLoop = gameLoop;
        this.userDAO = userDAO != null ? userDAO : new UserDAO();
        this.matchDAO = matchDAO != null ? matchDAO : new MatchDAO();

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
        PlayerCombatState state = new PlayerCombatState(tankId, userId);
        playerStates.put(tankId, state);
        allMatchParticipants.add(state); // Ghi nhớ để lưu lịch sử đầy đủ kể cả khi out game
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

        //  quản lý xe đang chết: đếm ngược hồi sinh
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
        if (isGameOver) return;
        if (event.getType() == CombatEvent.EventType.SHIELD_BLOCKED) return;
        TankEntity targetTank = gameLoop.getTank(event.getTargetTankId());
        TankEntity shooterTank = gameLoop.getTank(event.getShooterId());
        PlayerCombatState targetState = playerStates.get(event.getTargetTankId());
        PlayerCombatState shooterState = playerStates.get(event.getShooterId());

        if (targetTank == null || targetState == null || !targetTank.isAlive()) return;
        if (event.getType() == CombatEvent.EventType.SHIELD_BROKEN) {
            targetTank.setShieldActiveUntilMillis(0); // tắt khiên
        }
        // MIỄN NHIỄM KHI BẢO HỘ: Xe đang bảo hộ không bị nhận damage và không gây damage
        if (targetTank.isProtected() || (shooterTank != null && shooterTank.isProtected())) {
            return;
        }

        // Bắn trúng đích -> Cộng điểm hit theo cấu hình
        if (shooterState != null) {
            shooterState.addHit(pointsPerHit);
        }

        // Nếu xe bắn vừa bắn trúng bằng Rocket -> Tiêu hao buff tên lửa ngay lập tức (chỉ 1 phát 3 damage)
        if (shooterTank != null && shooterTank.getRocketBuffActiveUntilMillis() > 0) {
            shooterTank.setRocketBuffActiveUntilMillis(0);
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

    // Xử lý khi người chơi bấm nút Thoát giữa trận (ROOM_LEAVE_REQ) hoặc rớt mạng
    public void handlePlayerLeave(int tankId) {
        TankEntity tank = gameLoop.getTank(tankId);
        if (tank != null) {
            tank.setAlive(false);
            tank.setHp(0);
        }

        // 1. Chốt lưu dữ liệu (Kills, Hits, Score) của người thoát vào MySQL trước khi xóa khỏi RAM
        PlayerCombatState leaverState = playerStates.get(tankId);
        if (leaverState != null && userDAO != null) {
            try {
                userDAO.updateMatchStats(
                        leaverState.getUserId(),
                        leaverState.getScore(),
                        leaverState.getKills(),
                        leaverState.getHits(),
                        false // Thoát giữa chừng tính là Thua (không cộng total_wins)
                );
                LOGGER.info("[DB] Đã chốt lưu thành tích cho người chơi thoát: UserID " + leaverState.getUserId());
            } catch (Exception e) {
                LOGGER.severe("[DB] Lỗi lưu stats cho người thoát UserID " + leaverState.getUserId() + ": " + e.getMessage());
            }
        }

        // 2. Dọn sạch trạng thái và Socket của người này khỏi GameStateManager
        unregisterPlayer(tankId);
        LOGGER.info("[Room] Tank " + tankId + " đã dọn dẹp state và thoát trận an toàn.");

        // 3. Nếu phòng chỉ còn lại 1 người duy nhất -> Kết thúc trận sớm, trao giải cho người ở lại
        if (playerStates.size() <= 1 && !isGameOver) {
            triggerGameOver("PLAYER_LEFT");
        }
    }

    //hàm xử lí khi người chơi out trận 
    public void unregisterPlayer(int tankId) {
        playerStates.remove(tankId);
        playerSockets.remove(tankId);
    }

    // trigger kết thúc ván đấu
    private void triggerGameOver(String reason) {
        if (isGameOver) return; // Chặn trigger lặp lại
        isGameOver = true;
        gameLoop.stopLoop();

        List<PlayerCombatState> players = new ArrayList<>(playerStates.values());

        // 1. ĐIỀU KIỆN HÒA:
        // CHỈ xét Hòa khi trận đấu hết giờ bình thường (TIMEOUT) và TẤT CẢ đều có 0 Kill, 0 Hit.
        // NẾU LÀ "PLAYER_LEFT" (đối thủ bỏ chạy) THÌ TUYỆT ĐỐI KHÔNG HÒA!
        boolean isDraw = !"PLAYER_LEFT".equals(reason) 
                         && !players.isEmpty() 
                         && players.stream().allMatch(p -> p.getKills() == 0 && p.getHits() == 0);

        int winnerTankId = -1;

        if ("PLAYER_LEFT".equals(reason) && !players.isEmpty()) {
            // 2. NHÁNH ĐẶC BIỆT: Đối thủ thoát giữa chừng -> Người ở lại duy nhất THẮNG NGAY LẬP TỨC
            PlayerCombatState survivor = players.get(0);
            winnerTankId = survivor.getTankId();
            survivor.addBonusScore(pointsWinBonus); // Cộng ngay +50 điểm cho người ở lại
        } else if (!isDraw && !players.isEmpty()) {
            // 3. NHÁNH HẾT GIỜ CÓ GIAO TRANH: Xếp hạng theo 4 tiêu chí
            players.sort((p1, p2) -> {
                // Tiêu chí 1: Số mạng hạ gục (Kill) - Giảm dần
                if (p2.getKills() != p1.getKills()) {
                    return Integer.compare(p2.getKills(), p1.getKills());
                }

                // Tiêu chí 2: Độ chuẩn xác (Hits) - Giảm dần
                if (p2.getHits() != p1.getHits()) {
                    return Integer.compare(p2.getHits(), p1.getHits());
                }

                // Tiêu chí 3: Ghi mạng hạ gục đầu tiên sớm hơn (Earliest First Kill) - Tăng dần
                if (p1.getKills() > 0 && p1.getFirstKillTimeMillis() != p2.getFirstKillTimeMillis()) {
                    return Long.compare(p1.getFirstKillTimeMillis(), p2.getFirstKillTimeMillis());
                }

                // Tiêu chí 4: Bắn trúng viên đạn đầu tiên sớm hơn (Earliest First Hit) - Tăng dần
                return Long.compare(p1.getFirstHitTimeMillis(), p2.getFirstHitTimeMillis());
            });

            // Xác định người thắng cuộc (Top 1)
            PlayerCombatState winner = players.get(0);
            winnerTankId = winner.getTankId();

            // Điểm thưởng thắng trận: +50 điểm duy nhất cho người xếp hạng 1
            winner.addBonusScore(pointsWinBonus);
        }

        // 4. Đóng gói danh sách điểm số, kill, hit cuối cùng
        Map<Integer, Integer> finalScores = new HashMap<>();
        Map<Integer, Integer> finalKills = new HashMap<>();
        Map<Integer, Integer> finalHits = new HashMap<>();

        for (PlayerCombatState state : players) {
            finalScores.put(state.getTankId(), state.getScore());
            finalKills.put(state.getTankId(), state.getKills());
            finalHits.put(state.getTankId(), state.getHits());
        }

        // 5. Lưu kết quả vào CSDL (Người ở lại có winnerTankId hợp lệ -> được cộng total_wins + 1)
        saveMatchResultToDatabase(winnerTankId, isDraw, players);

        // 6. Gửi thông báo kết thúc trận về toàn bộ Client
        String finalReason = isDraw ? "DRAW" : reason;
        GameOverDTO dto = new GameOverDTO(winnerTankId, finalReason, finalScores, finalKills, finalHits);
        broadcastPacket(new Packet(PacketType.GAME_OVER_NOTIFY, GSON.toJson(dto)));
    }
    
    private void saveMatchResultToDatabase(int winnerTankId, boolean isDraw, List<PlayerCombatState> players) {
        Integer winnerUserId = null;

        // 1. Cập nhật tích lũy vào user_stats cho những người kết thúc ván đấu
        for (PlayerCombatState state : players) {
            boolean isWin = (!isDraw && state.getTankId() == winnerTankId);
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

        // 2. Lưu lịch sử trận đấu khớp với 2 bảng phpMyAdmin (LƯU ĐẦY ĐỦ CẢ NGƯỜI THOÁT)
        if (matchDAO != null) {
            int duration = (int) (ConfigLoader.getDefaultMatchDuration() - this.matchRemainingTime);
            String roomName = "Room " + (gameLoop != null ? "Game" : "1");

            // Sắp xếp thứ hạng: Người thắng đứng đầu (Hạng 1), người thoát hoặc thua xếp sau
            allMatchParticipants.sort((p1, p2) -> {
                if (p1.getTankId() == winnerTankId) return -1;
                if (p2.getTankId() == winnerTankId) return 1;
                return Integer.compare(p2.getScore(), p1.getScore());
            });

            // DÙNG allMatchParticipants ĐỂ BẢNG match_participants LƯU ĐỦ CẢ 2 XE
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

    public boolean isGameOver() { return isGameOver; }
    public double getMatchRemainingTime() { return matchRemainingTime; }
    public double getRespawnDelay() { return respawnDelay; }
    @Override
    public void onMapChanged(MapChangeEvent event) {
            MapUpdateDTO dto = new MapUpdateDTO(event.getRow(), event.getCol(), event.getNewTileCode());
            broadcastPacket(new Packet(PacketType.MAP_UPDATE, GSON.toJson(dto)));
        }

    @Override
    public void onItemPickup(ItemPickupEvent event) {
            TankEntity tank = gameLoop.getTank(event.getTankId());
            if (tank == null || !tank.isAlive() || tank.isProtected() ) return;
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
