package com.pocketshift.vrgame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Locale;

final class VrGameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Item[] items = new Item[] {
            new Item("MUG", 0.28f, 0.66f, 0.055f, Color.rgb(255, 231, 122)),
            new Item("BOX", 0.56f, 0.62f, 0.065f, Color.rgb(246, 153, 85)),
            new Item("ORB", 0.74f, 0.69f, 0.052f, Color.rgb(115, 232, 255))
    };
    private final Station[] stations = new Station[] {
            new Station("COFFEE", 0.18f, 0.42f, 0.11f, Color.rgb(129, 96, 64)),
            new Station("SCAN", 0.50f, 0.39f, 0.12f, Color.rgb(85, 159, 255)),
            new Station("TRAY", 0.82f, 0.42f, 0.11f, Color.rgb(139, 242, 148))
    };
    private final String[] taskText = new String[] {
            "Task 1: put MUG on the coffee bot",
            "Task 2: put BOX on the scanner",
            "Task 3: put ORB into the green tray"
    };

    private String deviceIp = "0.0.0.0";
    private int devicePort = NetworkReceiver.PORT;
    private String controllerAddress = "waiting";
    private float aimX = 0.5f;
    private float aimY = 0.5f;
    private float headYaw;
    private float headPitch;
    private int currentTask;
    private int completedLoops;
    private Item grabbedItem;
    private long lastPacketMs;
    private long completeFlashUntilMs;

    VrGameView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setFakeBoldText(true);
        resetGame();
    }

    void setNetworkInfo(String ip, int port) {
        deviceIp = ip;
        devicePort = port;
        invalidate();
    }

    void setHeadAngles(float yaw, float pitch) {
        headYaw = yaw;
        headPitch = pitch;
        postInvalidateOnAnimation();
    }

    void applyControllerPacket(ControllerPacket packet) {
        controllerAddress = packet.sourceAddress;
        lastPacketMs = System.currentTimeMillis();
        if (packet.reset) {
            resetGame();
            return;
        }
        aimX = packet.aimX;
        aimY = packet.aimY;
        if (grabbedItem != null) {
            moveGrabbedItem();
        }
        if (packet.grabTap) {
            handleGrabTap();
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int eyeWidth = width / 2;
        drawEye(canvas, new RectF(0, 0, eyeWidth, height), -1);
        drawEye(canvas, new RectF(eyeWidth, 0, width, height), 1);

        paint.setColor(Color.argb(220, 255, 255, 255));
        canvas.drawRect(eyeWidth - 2, 0, eyeWidth + 2, height, paint);
        drawOverlay(canvas, width, height);
        postInvalidateDelayed(33L);
    }

    private void drawEye(Canvas canvas, RectF viewport, int eye) {
        int save = canvas.save();
        canvas.clipRect(viewport);

        float viewW = viewport.width();
        float viewH = viewport.height();
        paint.setShader(new LinearGradient(0f, viewport.top, 0f, viewport.bottom,
                Color.rgb(23, 31, 48), Color.rgb(9, 11, 18), Shader.TileMode.CLAMP));
        canvas.drawRect(viewport, paint);
        paint.setShader(null);

        float parallax = eye * 9f;
        float lookX = (float) Math.sin(headYaw) * 28f;
        float lookY = (float) Math.sin(headPitch) * 20f;

        drawRoom(canvas, viewport, parallax, lookX, lookY);
        for (Station station : stations) {
            drawStation(canvas, viewport, station, parallax, lookX, lookY);
        }
        for (Item item : items) {
            drawItem(canvas, viewport, item, parallax, lookX, lookY);
        }
        drawAim(canvas, viewport, parallax);

        canvas.restoreToCount(save);
    }

    private void drawRoom(Canvas canvas, RectF viewport, float parallax, float lookX, float lookY) {
        float left = viewport.left;
        float top = viewport.top;
        float viewW = viewport.width();
        float viewH = viewport.height();

        paint.setColor(Color.rgb(36, 48, 74));
        RectF backWall = new RectF(left + viewW * 0.08f + lookX * 0.3f, top + viewH * 0.11f + lookY * 0.2f,
                left + viewW * 0.92f + lookX * 0.3f, top + viewH * 0.78f + lookY * 0.2f);
        canvas.drawRoundRect(backWall, 24f, 24f, paint);

        paint.setColor(Color.rgb(45, 56, 83));
        RectF desk = new RectF(left + viewW * 0.08f + parallax + lookX * 0.55f, top + viewH * 0.57f + lookY * 0.4f,
                left + viewW * 0.92f + parallax + lookX * 0.55f, top + viewH * 0.83f + lookY * 0.4f);
        canvas.drawRoundRect(desk, 30f, 30f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.rgb(82, 103, 145));
        canvas.drawLine(left + viewW * 0.16f + lookX * 0.35f, top + viewH * 0.17f,
                left + viewW * 0.84f + lookX * 0.35f, top + viewH * 0.17f, paint);
        canvas.drawLine(left + viewW * 0.16f + lookX * 0.35f, top + viewH * 0.25f,
                left + viewW * 0.84f + lookX * 0.35f, top + viewH * 0.25f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawStation(Canvas canvas, RectF viewport, Station station, float parallax, float lookX, float lookY) {
        float x = sceneX(viewport, station.x, parallax + lookX * 0.5f);
        float y = sceneY(viewport, station.y, lookY * 0.35f);
        float radius = station.radius * viewport.width();

        paint.setColor(Color.argb(210, Color.red(station.color), Color.green(station.color), Color.blue(station.color)));
        canvas.drawRoundRect(new RectF(x - radius * 1.3f, y - radius * 0.72f, x + radius * 1.3f, y + radius * 0.72f), 18f, 18f, paint);
        paint.setColor(Color.argb(95, 255, 255, 255));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        canvas.drawCircle(x, y, radius * 0.78f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.WHITE);
        paint.setTextSize(18f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(station.label, x, y + radius * 1.15f, paint);
    }

    private void drawItem(Canvas canvas, RectF viewport, Item item, float parallax, float lookX, float lookY) {
        float x = sceneX(viewport, item.x, parallax * 1.45f + lookX * 0.7f);
        float y = sceneY(viewport, item.y, lookY * 0.55f);
        float radius = item.radius * viewport.width();

        paint.setColor(Color.argb(65, 0, 0, 0));
        canvas.drawOval(new RectF(x - radius * 1.35f, y + radius * 0.65f, x + radius * 1.35f, y + radius * 1.05f), paint);

        paint.setColor(item.color);
        if ("BOX".equals(item.label)) {
            canvas.drawRoundRect(new RectF(x - radius, y - radius, x + radius, y + radius), 12f, 12f, paint);
        } else if ("MUG".equals(item.label)) {
            canvas.drawRoundRect(new RectF(x - radius * 0.85f, y - radius, x + radius * 0.65f, y + radius), 16f, 16f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(7f);
            canvas.drawCircle(x + radius * 0.85f, y, radius * 0.42f, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            canvas.drawCircle(x, y, radius, paint);
        }

        if (item == grabbedItem) {
            paint.setColor(Color.argb(180, 255, 255, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            canvas.drawCircle(x, y, radius * 1.35f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        paint.setColor(Color.rgb(12, 17, 28));
        paint.setTextSize(17f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(item.label, x, y + radius * 0.14f, paint);
    }

    private void drawAim(Canvas canvas, RectF viewport, float parallax) {
        float x = sceneX(viewport, aimX, parallax * 0.65f);
        float y = sceneY(viewport, aimY, 0f);
        paint.setColor(Color.argb(230, 98, 216, 255));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        canvas.drawCircle(x, y, 18f, paint);
        canvas.drawLine(x - 28f, y, x - 8f, y, paint);
        canvas.drawLine(x + 8f, y, x + 28f, y, paint);
        canvas.drawLine(x, y - 28f, x, y - 8f, paint);
        canvas.drawLine(x, y + 8f, x, y + 28f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawOverlay(Canvas canvas, int width, int height) {
        long now = System.currentTimeMillis();
        paint.setColor(Color.argb(130, 0, 0, 0));
        canvas.drawRoundRect(new RectF(18f, 18f, width - 18f, 116f), 20f, 20f, paint);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(25f);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Pocket Shift VR  |  connect controller to " + deviceIp + ":" + devicePort, 34f, 52f, textPaint);

        textPaint.setTextSize(22f);
        String status = now - lastPacketMs < 1800L ? "controller: " + controllerAddress : "controller: waiting";
        canvas.drawText(status + "  |  loop score: " + completedLoops, 34f, 86f, textPaint);

        paint.setColor(Color.argb(175, 0, 0, 0));
        canvas.drawRoundRect(new RectF(18f, height - 86f, width - 18f, height - 18f), 18f, 18f, paint);
        textPaint.setTextSize(28f);
        textPaint.setColor(now < completeFlashUntilMs ? Color.rgb(139, 242, 148) : Color.WHITE);
        canvas.drawText(taskText[currentTask], 34f, height - 42f, textPaint);
    }

    private void handleGrabTap() {
        if (grabbedItem != null) {
            moveGrabbedItem();
            maybeCompleteTask(grabbedItem);
            grabbedItem = null;
            return;
        }

        Item nearest = null;
        float bestDistance = 0.09f;
        for (Item item : items) {
            float distance = distance(item.x, item.y, aimX, aimY);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = item;
            }
        }
        if (nearest != null) {
            grabbedItem = nearest;
            moveGrabbedItem();
        }
    }

    private void moveGrabbedItem() {
        grabbedItem.x = clamp(aimX, 0.12f, 0.88f);
        grabbedItem.y = clamp(aimY, 0.30f, 0.78f);
    }

    private void maybeCompleteTask(Item item) {
        int requiredIndex = currentTask;
        if (item != items[requiredIndex]) {
            return;
        }
        Station target = stations[requiredIndex];
        if (distance(item.x, item.y, target.x, target.y) > target.radius * 1.35f) {
            return;
        }
        currentTask++;
        if (currentTask >= taskText.length) {
            currentTask = 0;
            completedLoops++;
            resetItemPositions();
        }
        completeFlashUntilMs = System.currentTimeMillis() + 1000L;
    }

    private void resetGame() {
        currentTask = 0;
        completedLoops = 0;
        grabbedItem = null;
        aimX = 0.5f;
        aimY = 0.5f;
        resetItemPositions();
        postInvalidateOnAnimation();
    }

    private void resetItemPositions() {
        for (Item item : items) {
            item.reset();
        }
    }

    private float sceneX(RectF viewport, float x, float offset) {
        return viewport.left + x * viewport.width() + offset;
    }

    private float sceneY(RectF viewport, float y, float offset) {
        return viewport.top + y * viewport.height() + offset;
    }

    private float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static final class Item {
        final String label;
        final float homeX;
        final float homeY;
        final float radius;
        final int color;
        float x;
        float y;

        Item(String label, float x, float y, float radius, int color) {
            this.label = label;
            this.homeX = x;
            this.homeY = y;
            this.radius = radius;
            this.color = color;
            reset();
        }

        void reset() {
            x = homeX;
            y = homeY;
        }
    }

    private static final class Station {
        final String label;
        final float x;
        final float y;
        final float radius;
        final int color;

        Station(String label, float x, float y, float radius, int color) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.color = color;
        }
    }
}
