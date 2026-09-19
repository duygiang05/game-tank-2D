package com.tank2d.server.game.event;

/** Giang implement để broadcast MAP_UPDATE khi 1 ô tường gạch vỡ. */
public interface MapChangeListener {
    void onMapChanged(MapChangeEvent event);
}