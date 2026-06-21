package com.pocketshift.controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

final class ControllerPadView extends View {
    interface Listener {
        void onAimChanged(float x, float y);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private float aimX = 0.5f;
    private float aimY = 0.5f;

    ControllerPadView(Context context) {
        super(context);
        setFocusable(true);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    float getAimX() {
        return aimX;
    }

    float getAimY() {
        return aimY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        paint.setShader(new LinearGradient(0f, 0f, 0f, height,
                Color.rgb(17, 25, 41), Color.rgb(9, 12, 20), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0f, 0f, width, height), 32f, 32f, paint);
        paint.setShader(null);

        paint.setColor(Color.rgb(47, 66, 99));
        paint.setStrokeWidth(4f);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 1; i < 4; i++) {
            float x = width * i / 4f;
            float y = height * i / 4f;
            canvas.drawLine(x, 0f, x, height, paint);
            canvas.drawLine(0f, y, width, y, paint);
        }

        float x = aimX * width;
        float y = aimY * height;
        paint.setColor(Color.rgb(98, 216, 255));
        paint.setStrokeWidth(7f);
        canvas.drawLine(x - 48f, y, x + 48f, y, paint);
        canvas.drawLine(x, y - 48f, x, y + 48f, paint);
        canvas.drawCircle(x, y, 34f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(34f);
        paint.setColor(Color.WHITE);
        canvas.drawText("TOUCHPAD AIM", width / 2f, 54f, paint);
        paint.setTextSize(24f);
        paint.setColor(Color.rgb(190, 204, 225));
        canvas.drawText("drag here, then tap Grab / Drop", width / 2f, height - 34f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_DOWN) {
            updateAim(event.getX(), event.getY());
            return true;
        }
        return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || super.onTouchEvent(event);
    }

    private void updateAim(float rawX, float rawY) {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        aimX = clamp(rawX / width);
        aimY = clamp(rawY / height);
        if (listener != null) {
            listener.onAimChanged(aimX, aimY);
        }
        invalidate();
    }

    private float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
