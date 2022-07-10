package net.zoda.api.command.argument;


/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
public record Rotation(float yaw, float pitch) {

    @Override
    public String toString() {
        return yaw+" "+pitch;
    }
}
