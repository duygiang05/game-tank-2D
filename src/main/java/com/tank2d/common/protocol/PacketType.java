package com.tank2d.common.protocol;

/**
 * Định nghĩa danh mục tất cả các loại gói tin (Packet Types) trao đổi giữa Client và Server.
 */
public enum PacketType {

    /** Nhóm gói tin xác thực tài khoản và chỉ số người chơi */
    AUTH_LOGIN_REQ,
    AUTH_LOGIN_RES,
    AUTH_REGISTER_REQ,
    AUTH_REGISTER_RES,
    LEADERBOARD_REQ,
    LEADERBOARD_RES,
    MATCH_HISTORY_REQ,
    MATCH_HISTORY_RES,
    MATCH_DETAIL_REQ,
    MATCH_DETAIL_RES,
    LOGOUT_REQ,

    /** Nhóm gói tin quản lý sảnh chờ và phòng đấu */
    LOBBY_GET_ROOMS_REQ,
    LOBBY_ROOMS_RES,
    ROOM_LIST_UPDATE,
    ROOM_CREATE_REQ,
    ROOM_JOIN_REQ,
    ROOM_STATE_UPDATE,
    ROOM_READY_REQ,
    ROOM_LEAVE_REQ,
    ROOM_START_REQ,
    ROOM_DURATION_REQ,

    /** Nhóm gói tin đồng bộ trận đấu, chiến đấu và bản đồ */
    GAME_START_NOTIFY,
    TANK_PLAYER_INFO_REQ,
    TANK_PLAYER_INFO,
    PLAYER_INPUT,
    PLAYER_SHOOT_REQ,
    GAME_SNAPSHOT,
    GAME_EVENT_EFFECT,
    GAME_OVER_NOTIFY,
    MAP_UPDATE,

    /** Nhóm gói tin xử lý kết nối lại và trạng thái kỷ luật thoát trận */
    GAME_RECONNECT_PROMPT,
    GAME_RECONNECT_REQ,
    GAME_RECONNECT_RES,
    GAME_PENALTY_NOTIFY
}
