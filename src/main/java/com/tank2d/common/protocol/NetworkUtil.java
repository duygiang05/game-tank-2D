package com.tank2d.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Tiện ích gửi/nhận dữ liệu an toàn qua mạng.
 * Áp dụng cơ chế Length-prefix (4 byte độ dài + UTF-8 payload) để triệt tiêu lỗi dính gói TCP.
 */
public class NetworkUtil {

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
        
        // 1. Đọc đúng 4 byte đầu để biết độ dài gói tin
        int length = in.readInt();
        
        // 2. Tạo mảng byte chứa đúng kích thước đã đọc
        byte[] buffer = new byte[length];
        
        // 3. Đọc đủ N byte dữ liệu vào mảng (readFully sẽ chặn đến khi đọc đủ)
        in.readFully(buffer);
        
        // 4. Chuyển mảng byte về dạng chuỗi UTF-8
        return new String(buffer, StandardCharsets.UTF_8);
    }
}