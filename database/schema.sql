CREATE DATABASE IF NOT EXISTS tank2d_db 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE tank2d_db;

-- 1. Bảng tài khoản người dùng
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Bảng chỉ số thống kê / xếp hạng
CREATE TABLE IF NOT EXISTS user_stats (
    user_id INT PRIMARY KEY,
    total_points INT DEFAULT 0,
    total_kills INT DEFAULT 0,
    total_wins INT DEFAULT 0,
    total_hits INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_stats_user 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- Index cho bảng xếp hạng
CREATE INDEX idx_leaderboard_points ON user_stats(total_points DESC);
CREATE INDEX idx_leaderboard_kills ON user_stats(total_kills DESC);
CREATE INDEX idx_leaderboard_wins ON user_stats(total_wins DESC);

-- 3. Bảng lịch sử ván đấu
CREATE TABLE IF NOT EXISTS match_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    room_name VARCHAR(100) NOT NULL,
    winner_id INT NULL,
    duration_seconds INT NOT NULL,
    played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_match_winner 
        FOREIGN KEY (winner_id) REFERENCES users(id) 
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- 4. Bảng thành viên tham gia ván đấu
CREATE TABLE IF NOT EXISTS match_participants (
    id INT AUTO_INCREMENT PRIMARY KEY,
    match_id INT NOT NULL,
    user_id INT NOT NULL,
    kills INT DEFAULT 0,
    hits INT DEFAULT 0,
    rank_position INT NOT NULL,
    points_earned INT DEFAULT 0,
    CONSTRAINT fk_participant_match 
        FOREIGN KEY (match_id) REFERENCES match_history(id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_participant_user 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5. Trigger tự động cấp phát dòng thống kê khi tạo tài khoản mới
DROP TRIGGER IF EXISTS trg_after_user_insert;
DELIMITER $$
CREATE TRIGGER trg_after_user_insert
AFTER INSERT ON users
FOR EACH ROW
BEGIN
    INSERT INTO user_stats (user_id, total_points, total_kills, total_wins, total_hits)
    VALUES (NEW.id, 0, 0, 0, 0);
END$$
DELIMITER ;