package com.tank2d.common.protocol;

import com.google.gson.Gson;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Tiện ích gửi/nhận dữ liệu an toàn qua mạng.
 * Áp dụng cơ chế Length-prefix (4 byte độ dài + UTF-8 payload) để triệt tiêu lỗi dính gói TCP.
 */
public class NetworkUtil {

    private static final Gson gson = new Gson();

    // =========================================================================
    // 1. TẦNG TRUYỀN DẪN RAW STRING (CHUỖI THÔ)
    // =========================================================================

    /**
     * Gửi chuỗi dữ liệu (JSON) qua luồng ra (DataOutputStream).
     * Hàm được đồng bộ hóa (synchronized) để tránh xung đột khi nhiều luồng cùng gửi dữ liệu qua 1 socket.
     */
    public static synchronized void send(DataOutputStream out, String payload) throws IOException {
        if (out == null || payload == null) return;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        
        // 1. Ghi 4 byte số nguyên biểu thị độ dài chính xác của dữ liệu
        out.writeInt(bytes.length);
        
        // 2. Ghi toàn bộ dữ liệu byte ra đường ống
        out.write(bytes);
        
        // 3. Đẩy dữ liệu đi ngay lập tức, không cho phép đệm (buffer) giữ lại
        out.flush();
    }

    /**
     * Đọc chuỗi dữ liệu (JSON) từ luồng vào (DataInputStream).
     * Chờ đọc đủ 4 byte độ dài, sau đó đọc đúng N byte dữ liệu tiếp theo.
     */
    public static String receive(DataInputStream in) throws IOException {
        if (in == null) return null;
        
        try {
            // 1. Đọc đúng 4 byte đầu để biết độ dài gói tin
            int length = in.readInt();
            if (length <= 0) return null;
            
            // 2. Tạo mảng byte chứa đúng kích thước đã đọc
            byte[] buffer = new byte[length];
            
            // 3. Đọc đủ N byte dữ liệu vào mảng (readFully sẽ chặn đến khi đọc đủ)
            in.readFully(buffer);
            
            // 4. Chuyển mảng byte về dạng chuỗi UTF-8
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (EOFException e) {
            // Xử lý an toàn khi đối phương đóng kết nối
            return null;
        }
    }

    // =========================================================================
    // 2. TẦNG GIAO VẬN PACKET (LÀM VIỆC TRỰC TIẾP VỚI ĐỐI TƯỢNG PACKET)
    // =========================================================================

    /**
     * Gửi trực tiếp đối tượng Packet qua socket.
     * Tự động serialize Packet sang JSON và gọi hàm send() chuẩn hóa ở trên.
     */
    public static synchronized void sendPacket(DataOutputStream out, Packet packet) throws IOException {
        if (out == null || packet == null) return;
        String jsonPayload = gson.toJson(packet);
        send(out, jsonPayload);
    }

    /**
     * Đọc trực tiếp đối tượng Packet từ socket.
     * Nhận chuỗi JSON từ hàm receive() và tự động deserialize về đối tượng Packet.
     * @return Đối tượng Packet nhận được, hoặc null nếu kết nối bị ngắt.
     */
    public static Packet readPacket(DataInputStream in) throws IOException {
        String jsonPayload = receive(in);
        if (jsonPayload == null) {
            return null;
        }
        return gson.fromJson(jsonPayload, Packet.class);
    }
}