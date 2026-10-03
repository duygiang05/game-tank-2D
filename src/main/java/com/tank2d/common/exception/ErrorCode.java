package com.tank2d.common.exception;

/**
 * Danh sách mã lỗi chuẩn hóa toàn dải cho hệ thống Tank2D Online.
 * Được phân loại theo từng miền nghiệp vụ cụ thể.
 */
public enum ErrorCode {

    /** Miền xác thực người dùng (1000 - 1099) */
    AUTH_INVALID_CREDENTIALS(1001, "Sai tên tài khoản hoặc mật khẩu!"),
    AUTH_USER_ALREADY_LOGGED_IN(1002, "Tài khoản đang đăng nhập ở thiết bị hoặc cửa sổ khác!"),
    AUTH_USERNAME_TAKEN(1003, "Đăng ký thất bại! Tài khoản đã tồn tại trong hệ thống."),
    AUTH_INVALID_INPUT(1004, "Tên tài khoản hoặc mật khẩu không hợp lệ!"),
    AUTH_NOT_LOGGED_IN(1005, "Người dùng chưa đăng nhập vào hệ thống!"),

    /** Miền phòng chơi và sảnh chờ (2000 - 2099) */
    ROOM_NOT_FOUND(2001, "Không tìm thấy phòng chơi yêu cầu!"),
    ROOM_FULL(2002, "Phòng chơi đã đầy, không thể tham gia!"),
    ROOM_IN_GAME(2003, "Trận đấu trong phòng đang diễn ra, không thể tham gia!"),
    ROOM_NOT_HOST(2004, "Chỉ Host của phòng mới có quyền thực hiện thao tác này!"),
    ROOM_NOT_IN_ROOM(2005, "Người chơi hiện không ở trong phòng nào!"),
    ROOM_INVALID_DURATION(2006, "Thời lượng phòng không hợp lệ (chỉ chấp nhận 45s, 60s, 90s)!"),
    ROOM_CANNOT_START(2007, "Chưa đủ điều kiện bắt đầu trận đấu (cần ít nhất 2 người và tất cả phải sẵn sàng)!"),

    /** Miền giao thức mạng (3000 - 3099) */
    NET_CLIENT_NOT_CONNECTED(3001, "Client chưa kết nối tới Server!"),
    NET_CONNECTION_FAILED(3002, "Lỗi kết nối hoặc mất kết nối mạng giữa Client và Server!"),
    NET_PACKET_PARSE_ERROR(3003, "Lỗi đọc hoặc giải mã gói tin (Packet) truyền qua mạng!"),
    NET_UNSUPPORTED_PACKET(3004, "Gói tin nhận được không được hỗ trợ hoặc không hợp lệ!"),

    /** Miền kết nối và cơ sở dữ liệu (4000 - 4099) */
    DB_POOL_NOT_INITIALIZED(4001, "HikariDataSource Connection Pool chưa được khởi tạo!"),
    DB_CONNECTION_ERROR(4002, "Không thể lấy kết nối tới cơ sở dữ liệu MySQL!"),
    DB_QUERY_ERROR(4003, "Lỗi khi truy vấn hoặc cập nhật dữ liệu CSDL!"),

    /** Miền cấu hình hệ thống và bản đồ (5000 - 5099) */
    CONFIG_LOAD_ERROR(5001, "Không thể tải file cấu hình hệ thống (.env, stats.json, game_rules.yml)!"),
    MAP_LOAD_ERROR(5002, "File bản đồ rỗng, không tìm thấy hoặc sai định dạng JSON!"),

    /** Miền tài nguyên đồ họa và âm thanh (6000 - 6099) */
    RESOURCE_NOT_FOUND(6001, "Không tìm thấy file tài nguyên ảnh hoặc âm thanh trong hệ thống!"),

    /** Miền cơ chế chơi và vật lý (7000 - 7099) */
    GAME_TANK_NOT_FOUND(7001, "Không tìm thấy dữ liệu xe tank trong ván đấu!"),
    GAME_INVALID_STATE(7002, "Trạng thái trận đấu không hợp lệ cho thao tác này!"),

    /** Miền lỗi hệ thống chung (9000 - 9099) */
    INTERNAL_SERVER_ERROR(9000, "Lỗi hệ thống nội bộ chưa xác định!"),
    INVALID_PARAM(9001, "Tham số truyền vào không hợp lệ!");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public String toString() {
        return "[" + code + "] " + defaultMessage;
    }
}
