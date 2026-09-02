package com.tank2d.server.db;

import com.tank2d.common.config.ConfigLoader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Quản lý kết nối Cơ sở dữ liệu MySQL sử dụng Connection Pool (HikariCP).
 * Đảm bảo tái sử dụng kết nối, tối ưu hiệu năng và an toàn đa luồng.
 */
public class DatabaseConnection {

    private static HikariDataSource dataSource;

    static {
        try {
            String host = ConfigLoader.getEnv("DB_HOST", "localhost");
            int port = ConfigLoader.getEnvInt("DB_PORT", 3306);
            String dbName = ConfigLoader.getEnv("DB_NAME", "tank2d_db");
            String user = ConfigLoader.getEnv("DB_USER", "root");
            String pass = ConfigLoader.getEnv("DB_PASS", "");
            int maxPoolSize = ConfigLoader.getEnvInt("DB_MAX_POOL_SIZE", 10);

            String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    host, port, dbName);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(user);
            config.setPassword(pass);
            config.setMaximumPoolSize(maxPoolSize);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Tối ưu hóa hiệu năng cache câu lệnh SQL
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            System.out.println("[DatabaseConnection] Khởi tạo HikariCP Connection Pool thành công!");
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Lỗi khởi tạo Connection Pool: " + e.getMessage());
        }
    }

    /**
     * Lấy một kết nối đang rảnh từ Pool.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("HikariDataSource chưa được khởi tạo!");
        }
        return dataSource.getConnection();
    }

    /**
     * Đóng Connection Pool khi Server tắt.
     */
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DatabaseConnection] Đã đóng HikariCP Connection Pool.");
        }
    }
}