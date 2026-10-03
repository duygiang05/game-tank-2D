package com.tank2d.client;

import javafx.application.Application;

/**
 * Điểm khởi động phụ hỗ trợ đóng gói JAR không kế thừa trực tiếp từ {@link javafx.application.Application}.
 */
public class MainLauncher {

    /**
     * Khởi chạy ứng dụng JavaFX.
     *
     * @param args tham số dòng lệnh
     */
    public static void main(String[] args) {
        Application.launch(com.tank2d.Tank2DOnline.class, args);
    }
}