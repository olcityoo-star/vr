package com.pocketshift.vrgame;

final class ControllerPacket {
    final float aimX;
    final float aimY;
    final boolean grabTap;
    final boolean reset;
    final String sourceAddress;

    private ControllerPacket(float aimX, float aimY, boolean grabTap, boolean reset, String sourceAddress) {
        this.aimX = clamp01(aimX);
        this.aimY = clamp01(aimY);
        this.grabTap = grabTap;
        this.reset = reset;
        this.sourceAddress = sourceAddress;
    }

    static ControllerPacket parse(String payload, String sourceAddress) {
        String[] parts = payload.trim().split("\\|");
        if (parts.length == 0) {
            return null;
        }

        if ("RESET".equals(parts[0])) {
            return new ControllerPacket(0.5f, 0.5f, false, true, sourceAddress);
        }

        if ("TAP".equals(parts[0]) && parts.length >= 3) {
            return new ControllerPacket(parseFloat(parts[1], 0.5f), parseFloat(parts[2], 0.5f), true, false, sourceAddress);
        }

        if ("AIM".equals(parts[0]) && parts.length >= 3) {
            return new ControllerPacket(parseFloat(parts[1], 0.5f), parseFloat(parts[2], 0.5f), false, false, sourceAddress);
        }

        return null;
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
