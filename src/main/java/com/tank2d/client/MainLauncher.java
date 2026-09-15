package com.tank2d.client;

import com.tank2d.client.view.GameCanvasApp;
import com.tank2d.client.view.GameClientApp;

public class MainLauncher {
    public static void main(String[] args) {
        // Muốn chạy bản Render Offline Mock (Sprint 1) để xem Canvas:
        // GameCanvasApp.main(args);

        // Muốn chạy bản Online kết nối Socket Server Realtime (Sprint 2):
        GameClientApp.main(args);
    }
}