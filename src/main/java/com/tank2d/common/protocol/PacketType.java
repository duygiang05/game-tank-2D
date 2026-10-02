package com.tank2d.common.protocol;

public enum PacketType {

    // =========================
    // AUTH
    // =========================
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
    // =========================
    // LOBBY
    // =========================

    LOBBY_GET_ROOMS_REQ,
    LOBBY_ROOMS_RES,
    ROOM_LIST_UPDATE,
    // =========================
    // ROOM
    // =========================

    ROOM_CREATE_REQ,
    ROOM_JOIN_REQ,
    ROOM_STATE_UPDATE,
    ROOM_READY_REQ,
    ROOM_LEAVE_REQ,
    ROOM_START_REQ,
    // Host chọn thời lượng trận đấu
    ROOM_DURATION_REQ,
    // =========================
    // GAME
    // =========================

    GAME_START_NOTIFY,
    TANK_PLAYER_INFO_REQ,
    TANK_PLAYER_INFO,
    PLAYER_INPUT,
    PLAYER_SHOOT_REQ,
    GAME_SNAPSHOT,
    GAME_EVENT_EFFECT,
    GAME_OVER_NOTIFY,
    MAP_UPDATE,
    GAME_RECONNECT_PROMPT,
    GAME_RECONNECT_REQ,
    GAME_RECONNECT_RES,
    GAME_PENALTY_NOTIFY
}
