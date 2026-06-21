package com.pocketshift.controller;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String PREFS = "controller";
    private static final String PREF_HOST = "host";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ControllerClient client;
    private ControllerPadView padView;
    private EditText ipInput;
    private TextView statusText;
    private final Runnable aimTicker = new Runnable() {
        @Override
        public void run() {
            if (client != null && padView != null) {
                client.sendAim(padView.getAimX(), padView.getAimY());
            }
            handler.postDelayed(this, 50L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        client = new ControllerClient();
        setContentView(buildUi());
        connectToSavedHost();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(aimTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(aimTicker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.close();
    }

    private LinearLayout buildUi() {
        int margin = dp(14);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(margin, margin, margin, margin);
        root.setBackgroundColor(Color.rgb(8, 11, 18));

        TextView title = new TextView(this);
        title.setText("Pocket Shift Controller");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView help = new TextView(this);
        help.setText("Enter the IP shown inside the VR phone. Both phones must be on the same Wi-Fi.");
        help.setTextColor(Color.rgb(190, 204, 225));
        help.setTextSize(15f);
        help.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helpParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(help, helpParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setInputType(InputType.TYPE_CLASS_PHONE);
        ipInput.setHint("VR phone IP");
        ipInput.setTextColor(Color.WHITE);
        ipInput.setHintTextColor(Color.rgb(130, 145, 170));
        ipInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_HOST, ""));
        row.addView(ipInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button connectButton = new Button(this);
        connectButton.setText("Connect");
        connectButton.setOnClickListener(v -> connectToInput());
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        connectParams.setMargins(dp(10), 0, 0, 0);
        row.addView(connectButton, connectParams);
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(98, 216, 255));
        statusText.setTextSize(15f);
        statusText.setText("Not connected");
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(8), 0, dp(10));
        root.addView(statusText, statusParams);

        padView = new ControllerPadView(this);
        padView.setListener((x, y) -> {
            if (client != null) {
                client.sendAim(x, y);
            }
            updateAimText();
        });
        LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(padView, padParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button grabButton = new Button(this);
        grabButton.setText("Grab / Drop");
        grabButton.setTextSize(18f);
        grabButton.setOnClickListener(v -> client.sendTap(padView.getAimX(), padView.getAimY()));
        buttons.addView(grabButton, new LinearLayout.LayoutParams(0, dp(64), 1f));

        Button resetButton = new Button(this);
        resetButton.setText("Reset");
        resetButton.setTextSize(18f);
        resetButton.setOnClickListener(v -> client.sendReset());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(64), 0.55f);
        resetParams.setMargins(dp(10), 0, 0, 0);
        buttons.addView(resetButton, resetParams);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, dp(12), 0, 0);
        root.addView(buttons, buttonParams);

        return root;
    }

    private void connectToSavedHost() {
        String host = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_HOST, "");
        if (!host.trim().isEmpty()) {
            client.connect(host, ControllerClient.DEFAULT_PORT);
            setStatus("Connected to " + host + ":" + ControllerClient.DEFAULT_PORT);
        }
    }

    private void connectToInput() {
        String host = ipInput.getText().toString().trim();
        if (host.isEmpty()) {
            setStatus("Enter the VR phone IP first");
            return;
        }
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString(PREF_HOST, host);
        editor.apply();
        client.connect(host, ControllerClient.DEFAULT_PORT);
        setStatus("Connected to " + host + ":" + ControllerClient.DEFAULT_PORT);
    }

    private void updateAimText() {
        setStatus(String.format(Locale.US, "Aim %.0f%% / %.0f%%  |  UDP %d",
                padView.getAimX() * 100f, padView.getAimY() * 100f, ControllerClient.DEFAULT_PORT));
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
