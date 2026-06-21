package com.pocketshift.vrgame;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public final class MainActivity extends Activity implements NetworkReceiver.Listener, SensorEventListener {
    private VrGameView gameView;
    private TextView leftHud;
    private TextView rightHud;
    private NetworkReceiver receiver;
    private SensorManager sensorManager;
    private Sensor rotationSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        gameView = new VrGameView(this);
        gameView.setNetworkInfo(getDeviceIp(), NetworkReceiver.PORT);
        setContentView(buildContentView());
        updateHud();

        receiver = new NetworkReceiver(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.onResume();
        receiver.start();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        receiver.stop();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        gameView.onPause();
    }

    @Override
    public void onControllerPacket(ControllerPacket packet) {
        gameView.applyControllerPacket(packet);
        updateHud();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
        float[] matrix = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);
        gameView.setHeadAngles(orientation[0], orientation[1]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No calibration UI is needed for this prototype.
    }

    private FrameLayout buildContentView() {
        FrameLayout root = new FrameLayout(this);
        root.addView(gameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout hudRow = new LinearLayout(this);
        hudRow.setOrientation(LinearLayout.HORIZONTAL);
        hudRow.setPadding(dp(10), dp(8), dp(10), 0);
        leftHud = createHudText();
        rightHud = createHudText();
        hudRow.addView(leftHud, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hudRow.addView(rightHud, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout.LayoutParams hudParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(hudRow, hudParams);
        return root;
    }

    private TextView createHudText() {
        TextView hud = new TextView(this);
        hud.setTextColor(Color.WHITE);
        hud.setTextSize(15f);
        hud.setGravity(Gravity.CENTER_HORIZONTAL);
        hud.setIncludeFontPadding(false);
        hud.setShadowLayer(4f, 1f, 1f, Color.BLACK);
        hud.setBackgroundColor(Color.argb(78, 0, 0, 0));
        hud.setPadding(dp(6), dp(4), dp(6), dp(5));
        return hud;
    }

    private void updateHud() {
        String text = gameView.getHudText();
        if (leftHud != null) {
            leftHud.setText(text);
        }
        if (rightHud != null) {
            rightHud.setText(text);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String getDeviceIp() {
        String wifiAddress = getWifiIp();
        if (wifiAddress != null && !"0.0.0.0".equals(wifiAddress)) {
            return wifiAddress;
        }
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // The overlay will show a fallback if network interface inspection fails.
        }
        return "0.0.0.0";
    }

    @SuppressWarnings("deprecation")
    private String getWifiIp() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
                return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
            }
        } catch (Exception ignored) {
            // Some Android versions restrict Wi-Fi details; network interface fallback handles that.
        }
        return null;
    }
}
