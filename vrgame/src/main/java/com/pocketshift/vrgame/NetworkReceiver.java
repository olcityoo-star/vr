package com.pocketshift.vrgame;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

final class NetworkReceiver {
    static final int PORT = 45454;

    interface Listener {
        void onControllerPacket(ControllerPacket packet);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private DatagramSocket socket;
    private Thread thread;
    private volatile boolean running;

    NetworkReceiver(Listener listener) {
        this.listener = listener;
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "PocketShiftUdpReceiver");
        thread.start();
    }

    void stop() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        thread = null;
    }

    private void runLoop() {
        byte[] buffer = new byte[256];
        try {
            socket = new DatagramSocket(PORT);
            while (running) {
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                socket.receive(datagram);
                String payload = new String(datagram.getData(), datagram.getOffset(), datagram.getLength(), StandardCharsets.UTF_8);
                String address = datagram.getAddress().getHostAddress();
                ControllerPacket packet = ControllerPacket.parse(payload, address);
                if (packet != null) {
                    mainHandler.post(() -> listener.onControllerPacket(packet));
                }
            }
        } catch (SocketException ignored) {
            // Closing the socket is the normal way to stop the blocking receive call.
        } catch (IOException ignored) {
            running = false;
        }
    }
}
