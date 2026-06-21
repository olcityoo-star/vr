package com.pocketshift.controller;

import android.os.Handler;
import android.os.HandlerThread;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ControllerClient {
    static final int DEFAULT_PORT = 45454;

    private final HandlerThread senderThread = new HandlerThread("PocketShiftUdpSender");
    private Handler sender;
    private DatagramSocket socket;
    private InetAddress host;
    private int port = DEFAULT_PORT;

    ControllerClient() {
        senderThread.start();
        sender = new Handler(senderThread.getLooper());
        try {
            socket = new DatagramSocket();
        } catch (IOException ignored) {
            socket = null;
        }
    }

    void connect(String hostName, int port) {
        this.port = port;
        sender.post(() -> {
            try {
                host = InetAddress.getByName(hostName.trim());
                sendNow("HELLO|android-controller");
            } catch (IOException ignored) {
                host = null;
            }
        });
    }

    void sendAim(float x, float y) {
        String payload = String.format(Locale.US, "AIM|%.4f|%.4f", clamp01(x), clamp01(y));
        send(payload);
    }

    void sendTap(float x, float y) {
        String payload = String.format(Locale.US, "TAP|%.4f|%.4f", clamp01(x), clamp01(y));
        send(payload);
    }

    void sendReset() {
        send("RESET");
    }

    void close() {
        sender.post(() -> {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        });
        senderThread.quitSafely();
    }

    private void send(String payload) {
        sender.post(() -> sendNow(payload));
    }

    private void sendNow(String payload) {
        if (socket == null || host == null) {
            return;
        }
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, host, port);
        try {
            socket.send(packet);
        } catch (IOException ignored) {
            // UDP drops are acceptable; the controller sends fresh aim updates continuously.
        }
    }

    private float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
