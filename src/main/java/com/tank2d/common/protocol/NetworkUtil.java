package com.tank2d.common.protocol;

import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Tiện ích truyền nhận gói tin qua giao thức mạng TCP theo cơ chế Length-Prefix Framing.
 * <p>
 * <b>Giải pháp triệt tiêu hiện tượng dính gói / vỡ gói TCP (TCP Sticky / Fragmented Packets):</b>
 * Mỗi thông điệp được cấu trúc gồm 2 phần:
 * <ol>
 *     <li><b>Header (4 bytes):</b> Số nguyên 32-bit (Big-Endian) biểu diễn độ dài chính xác (N bytes) của phần thân dữ liệu (Payload).</li>
 *     <li><b>Payload (N bytes):</b> Chuỗi ký tự JSON mã hóa theo chuẩn UTF-8.</li>
 * </ol>
 * Phía nhận sẽ sử dụng {@link DataInputStream#readFully(byte[])} để chặn và đọc đủ đúng N bytes của một gói tin hoàn chỉnh.
 */
public final class NetworkUtil {

    private static final Gson GSON = new Gson();

    private NetworkUtil() {}

    /**
     * Gửi chuỗi dữ liệu (JSON) qua luồng ra socket.
     * Hàm được đồng bộ hóa (synchronized trên {@code out}) để tránh tranh chấp luồng khi nhiều thread cùng ghi.
     *
     * @param out     luồng dữ liệu ra của socket
     * @param payload chuỗi ký tự JSON cần gửi
     * @throws IOException nếu đường truyền mạng gặp sự cố đứt gãy
     */
    public static void send(DataOutputStream out, String payload) throws IOException {
        if (out == null || payload == null) {
            return;
        }
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

        synchronized (out) {
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
        }
    }

    /**
     * Đọc chuỗi dữ liệu (JSON) từ luồng vào socket bằng cơ chế đóng khung độ dài.
     *
     * @param in luồng dữ liệu vào của socket
     * @return chuỗi JSON hoàn chỉnh, hoặc {@code null} nếu kết nối đã bị đóng bởi đối tác (EOF)
     * @throws IOException nếu luồng mạng gặp sự cố truyền tải bất thường
     */
    public static String receive(DataInputStream in) throws IOException {
        if (in == null) {
            return null;
        }

        try {
            int length = in.readInt();
            if (length <= 0) {
                return null;
            }

            byte[] buffer = new byte[length];
            in.readFully(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (EOFException e) {
            return null;
        }
    }

    /**
     * Tuần tự hóa (Serialize) đối tượng {@link Packet} thành JSON và gửi qua socket.
     *
     * @param out    luồng dữ liệu ra của socket
     * @param packet đối tượng gói tin cần gửi
     * @throws IOException nếu đường truyền gặp sự cố
     */
    public static void sendPacket(DataOutputStream out, Packet packet) throws IOException {
        if (out == null || packet == null) {
            return;
        }
        String jsonPayload = GSON.toJson(packet);
        send(out, jsonPayload);
    }

    /**
     * Đọc và giải tuần tự hóa (Deserialize) gói tin từ luồng vào socket thành đối tượng {@link Packet}.
     *
     * @param in luồng dữ liệu vào của socket
     * @return thực thể {@link Packet} nhận được, hoặc {@code null} nếu kết nối kết thúc
     * @throws IOException nếu đọc luồng thất bại
     */
    public static Packet readPacket(DataInputStream in) throws IOException {
        String jsonPayload = receive(in);
        if (jsonPayload == null) {
            return null;
        }
        return GSON.fromJson(jsonPayload, Packet.class);
    }
}