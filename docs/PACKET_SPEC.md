# ĐẶC TẢ CẤU TRÚC GÓI TIN (PACKET SPECIFICATION)

**Dự án:** Tank2D-Online
**Mô hình:** Client - Server (TCP Socket đa luồng)
**Công nghệ:** Java 21, Maven, MySQL, HikariCP, Gson

---

## I. CƠ CHẾ ĐÓNG GÓI MẠNG (FRAMING & PROTOCOL ENVELOPE)

### 1. Chống dính gói (Length-Prefix Framing)

Mọi dữ liệu gửi qua `NetworkUtil` tuân theo cấu trúc 2 phần:

| Phần | Kích thước | Mô tả |
|---|---|---|
| Header | 4 bytes (Big-Endian int) | Độ dài (byte) của Payload JSON sau khi encode UTF-8 |
| Payload | N bytes (UTF-8) | Chuỗi JSON sinh ra từ class `com.tank2d.common.protocol.Packet` |

```
+--------------------------+----------------------------------------------------------+
| Header (4 bytes, int)    | Payload (N bytes UTF-8 JSON)                              |
| Độ dài byte của Payload  | {"type":"AUTH_LOGIN_REQ","data":"{\"username\":\"...\"}"} |
+--------------------------+----------------------------------------------------------+
```

### 2. Cấu trúc Packet Envelope (`Packet.java`)

```json
{
  "type": "PACKET_TYPE_ENUM",
  "data": "STRINGIFIED_JSON_PAYLOAD"
}
```

- **`type`** (`PacketType`): enum định danh mục đích gói tin, dùng để định tuyến nhanh ở tầng mạng.
- **`data`** (`String`): chuỗi JSON payload chi tiết. Giữ dạng `String` (không phải `Object`) để:
  - Tránh lỗi mất kiểu dữ liệu (type erasure) và lỗi ép kiểu `LinkedTreeMap` của Gson.
  - Cho phép **lazy parsing**: tầng mạng chỉ đọc `type` để định tuyến, tầng nghiệp vụ mới parse `data` sang POJO cụ thể.

---

## II. BẢNG ĐẶC TẢ GÓI TIN THEO PHÂN HỆ

### 1. Xác thực & Tài khoản (Authentication)

| Packet Type | Chiều | Payload (`data`) mẫu |
|---|---|---|
| `AUTH_LOGIN_REQ` | Client → Server | `{"username":"player1","password":"mat_khau_123"}` |
| `AUTH_LOGIN_RES` | Server → Client | `{"success":true,"userId":101,"username":"player1","message":"Đăng nhập thành công"}` |
| `AUTH_REGISTER_REQ` | Client → Server | `{"username":"player2","password":"mat_khau_123"}` |
| `AUTH_REGISTER_RES` | Server → Client | `{"success":true,"message":"Đăng ký tài khoản thành công"}` |

### 2. Sảnh chờ & Quản lý phòng (Lobby & Room)

| Packet Type | Chiều | Payload (`data`) mẫu |
|---|---|---|
| `LOBBY_GET_ROOMS_REQ` | Client → Server | `{}` |
| `LOBBY_ROOMS_RES` | Server → Client | `{"rooms":[{"roomId":"ROOM_101","roomName":"Phòng Chiến 01","currentPlayers":1,"maxPlayers":2,"state":"WAITING"}]}` |
| `ROOM_CREATE_REQ` | Client → Server | `{"roomName":"Đại chiến xe tăng","maxPlayers":2}` |
| `ROOM_JOIN_REQ` | Client → Server | `{"roomId":"ROOM_101"}` |
| `ROOM_STATE_UPDATE` | Server → Client | `{"roomId":"ROOM_101","players":[{"userId":101,"username":"player1","isHost":true,"isReady":true},{"userId":102,"username":"player2","isHost":false,"isReady":false}]}` |
| `ROOM_READY_REQ` | Client → Server | `{"isReady":true}` |
| `GAME_START_NOTIFY` | Server → Client | `{"mapId":1,"countdown":3}` |

### 3. Điều khiển & Đồng bộ trận đấu (Gameplay Sync)

| Packet Type | Chiều | Ghi chú | Payload (`data`) mẫu |
|---|---|---|---|
| `PLAYER_INPUT` | Client → Server | Gửi ngay khi phím W/A/S/D hoặc chuột thay đổi. `move` ∈ {`UP`,`DOWN`,`LEFT`,`RIGHT`,`NONE`} | `{"move":"UP","turretAngle":45.5}` |
| `PLAYER_SHOOT_REQ` | Client → Server | Gửi lệnh bắn đạn | `{"turretAngle":45.5}` |
| `GAME_SNAPSHOT` | Server → Client | Broadcast định kỳ, tickrate 30–60 FPS | `{"tick":1450,"tanks":[{"id":101,"x":150.5,"y":200.0,"bodyAngle":0.0,"turretAngle":45.5,"hp":3,"isAlive":true}],"bullets":[{"id":1,"ownerId":101,"x":175.0,"y":215.0,"vx":7.07,"vy":7.07}]}` |
| `GAME_EVENT_EFFECT` | Server → Client | Hiệu ứng va chạm/nổ | `{"eventType":"EXPLOSION","x":175.0,"y":215.0,"targetTankId":102}` |
| `GAME_OVER_NOTIFY` | Server → Client | Kết thúc ván đấu | `{"winnerId":101,"winnerName":"player1","scoreGained":100}` |

---

## III. HƯỚNG DẪN LẬP TRÌNH DÙNG CHUNG (CODE RECIPES)

### 1. Phía gửi (Sender — dùng cho cả Client và Server)

```java
// 1. Tạo đối tượng dữ liệu cụ thể (POJO)
LoginRequest request = new LoginRequest("player1", "123456");

// 2. Chuyển đổi POJO sang JSON String
String jsonPayload = new Gson().toJson(request);

// 3. Đóng gói vào Packet
Packet packet = new Packet(PacketType.AUTH_LOGIN_REQ, jsonPayload);

// 4. Gửi an toàn qua NetworkUtil
NetworkUtil.sendPacket(dataOutputStream, packet);
```

### 2. Phía nhận và xử lý (Receiver / Dispatcher)

```java
// 1. Đọc gói tin từ Socket (chống dính gói tự động)
Packet packet = NetworkUtil.readPacket(dataInputStream);

if (packet != null) {
    Gson gson = new Gson();

    switch (packet.getType()) {
        case AUTH_LOGIN_REQ:
            LoginRequest loginData = gson.fromJson(packet.getData(), LoginRequest.class);
            // Xử lý xác thực tài khoản qua UserDAO
            break;

        case PLAYER_INPUT:
            PlayerInput input = gson.fromJson(packet.getData(), PlayerInput.class);
            // Cập nhật hướng di chuyển của xe trong GameRoom
            break;

        case GAME_SNAPSHOT:
            GameSnapshot snapshot = gson.fromJson(packet.getData(), GameSnapshot.class);
            // Render danh sách tanks và bullets lên Canvas
            break;

        default:
            System.err.println("Gói tin chưa được đăng ký định tuyến: " + packet.getType());
            break;
    }
}
```
