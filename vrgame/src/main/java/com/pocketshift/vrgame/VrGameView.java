package com.pocketshift.vrgame;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

final class VrGameView extends GLSurfaceView {
    private final GameRenderer renderer;

    VrGameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        renderer = new GameRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    void setNetworkInfo(String ip, int port) {
        renderer.setNetworkInfo(ip, port);
    }

    void setHeadAngles(float yaw, float pitch) {
        renderer.setHeadAngles(yaw, pitch);
    }

    void applyControllerPacket(ControllerPacket packet) {
        renderer.applyControllerPacket(packet);
    }

    String getHudText() {
        return renderer.getHudText();
    }

    private static final class GameRenderer implements Renderer {
        private static final float TABLE_Y = 0.82f;
        private static final float EYE_HEIGHT = 1.35f;
        private static final float CAMERA_Z = 3.7f;
        private static final float IPD = 0.07f;

        private static final float[] CUBE = new float[] {
                -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,
                -0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
                 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
                 0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,
                -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
                -0.5f, -0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f,
                 0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,
                 0.5f, -0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,
                -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,
                -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
                -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,
                -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f
        };

        private static final float[][] COLORS = new float[][] {
                {1.00f, 0.86f, 0.28f, 1f},
                {1.00f, 0.43f, 0.18f, 1f},
                {0.18f, 0.87f, 1.00f, 1f}
        };

        private final FloatBuffer cubeBuffer = ByteBuffer
                .allocateDirect(CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(CUBE);
        private final Item[] items = new Item[] {
                new Item("MUG", -0.92f, -0.68f, 0),
                new Item("BOX",  0.00f, -0.62f, 1),
                new Item("ORB",  0.92f, -0.68f, 2)
        };
        private final Station[] stations = new Station[] {
                new Station("COFFEE", -1.15f, -1.62f, 0),
                new Station("SCAN",    0.00f, -1.68f, 1),
                new Station("TRAY",    1.15f, -1.62f, 2)
        };

        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] viewProjection = new float[16];
        private final float[] mvp = new float[16];

        private int program;
        private int positionHandle;
        private int mvpHandle;
        private int colorHandle;
        private int width;
        private int height;

        private String deviceIp = "0.0.0.0";
        private int devicePort = NetworkReceiver.PORT;
        private String controllerAddress = "waiting";
        private float aimX = 0.5f;
        private float aimY = 0.5f;
        private float headYaw;
        private float headPitch;
        private float baseYaw;
        private float basePitch;
        private boolean hasBaseHeadPose;
        private int currentTask;
        private int completedLoops;
        private int grabbedIndex = -1;
        private long lastPacketMs;
        private long completeFlashUntilMs;

        GameRenderer() {
            cubeBuffer.position(0);
            resetGame();
        }

        @Override
        public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig config) {
            GLES20.glClearColor(0.02f, 0.03f, 0.06f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
            program = createProgram();
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvp");
            colorHandle = GLES20.glGetUniformLocation(program, "uColor");
        }

        @Override
        public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            int eyeWidth = Math.max(1, width / 2);
            int eyeHeight = Math.max(1, height);
            float aspect = eyeWidth / (float) eyeHeight;
            Matrix.perspectiveM(projection, 0, 82f, aspect, 0.08f, 40f);

            drawEye(0, 0, eyeWidth, eyeHeight, -1f);
            drawEye(eyeWidth, 0, eyeWidth, eyeHeight, 1f);
        }

        synchronized void setNetworkInfo(String ip, int port) {
            deviceIp = ip;
            devicePort = port;
        }

        synchronized void setHeadAngles(float yaw, float pitch) {
            if (!hasBaseHeadPose) {
                baseYaw = yaw;
                basePitch = pitch;
                hasBaseHeadPose = true;
            }
            headYaw = clamp(wrapRadians(yaw - baseYaw), -0.95f, 0.95f);
            headPitch = clamp(pitch - basePitch, -0.55f, 0.55f);
        }

        synchronized void applyControllerPacket(ControllerPacket packet) {
            controllerAddress = packet.sourceAddress;
            lastPacketMs = System.currentTimeMillis();
            if (packet.reset) {
                resetGame();
                return;
            }

            aimX = packet.aimX;
            aimY = packet.aimY;
            if (grabbedIndex >= 0) {
                moveGrabbedItem();
            }
            if (packet.grabTap) {
                handleGrabTap();
            }
        }

        synchronized String getHudText() {
            String connection = System.currentTimeMillis() - lastPacketMs < 1800L ? controllerAddress : "waiting";
            String task = currentTask == 0 ? "MUG -> COFFEE" : currentTask == 1 ? "BOX -> SCAN" : "ORB -> TRAY";
            return String.format(Locale.US, "IP %s:%d\n%s\nTask: %s  Score: %d",
                    deviceIp, devicePort, connection, task, completedLoops);
        }

        private void drawEye(int x, int y, int eyeWidth, int eyeHeight, float eyeSign) {
            GLES20.glViewport(x, y, eyeWidth, eyeHeight);
            float eyeOffset = eyeSign * IPD * 0.5f;

            float yaw;
            float pitch;
            synchronized (this) {
                yaw = headYaw;
                pitch = headPitch;
            }

            float cameraX = eyeOffset;
            float cameraY = EYE_HEIGHT;
            float cameraZ = CAMERA_Z;
            float lookX = cameraX + (float) Math.sin(yaw);
            float lookY = cameraY - (float) Math.sin(pitch);
            float lookZ = cameraZ - (float) Math.cos(yaw);
            Matrix.setLookAtM(view, 0, cameraX, cameraY, cameraZ, lookX, lookY, lookZ, 0f, 1f, 0f);
            Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0);

            drawRoom();
            drawStations();
            drawItems();
            drawReticle();
        }

        private void drawRoom() {
            drawBox(0f, -0.08f, -1.15f, 4.4f, 0.12f, 5.6f, 0.18f, 0.20f, 0.27f, 1f);
            drawBox(0f, 2.5f, -3.0f, 4.6f, 2.7f, 0.14f, 0.13f, 0.19f, 0.30f, 1f);
            drawBox(-2.28f, 1.15f, -1.0f, 0.12f, 2.7f, 5.4f, 0.10f, 0.14f, 0.24f, 1f);
            drawBox(2.28f, 1.15f, -1.0f, 0.12f, 2.7f, 5.4f, 0.10f, 0.14f, 0.24f, 1f);
            drawBox(0f, 2.48f, -1.0f, 4.4f, 0.10f, 5.4f, 0.08f, 0.10f, 0.16f, 1f);

            drawBox(0f, 0.70f, -1.18f, 3.15f, 0.22f, 1.55f, 0.35f, 0.25f, 0.17f, 1f);
            drawBox(-1.35f, 0.32f, -0.55f, 0.18f, 0.72f, 0.18f, 0.20f, 0.15f, 0.11f, 1f);
            drawBox(1.35f, 0.32f, -0.55f, 0.18f, 0.72f, 0.18f, 0.20f, 0.15f, 0.11f, 1f);
            drawBox(-1.35f, 0.32f, -1.75f, 0.18f, 0.72f, 0.18f, 0.20f, 0.15f, 0.11f, 1f);
            drawBox(1.35f, 0.32f, -1.75f, 0.18f, 0.72f, 0.18f, 0.20f, 0.15f, 0.11f, 1f);

            drawBox(0f, 1.62f, -2.85f, 1.25f, 0.45f, 0.08f, 0.06f, 0.25f, 0.34f, 1f);
            drawBox(0f, 1.64f, -2.80f, 0.98f, 0.08f, 0.10f, 0.30f, 0.90f, 1.00f, 1f);
        }

        private void drawStations() {
            long now = System.currentTimeMillis();
            int task;
            synchronized (this) {
                task = currentTask;
            }
            for (int i = 0; i < stations.length; i++) {
                Station station = stations[i];
                float[] color = COLORS[station.colorIndex];
                float pulse = i == task ? 0.30f + 0.18f * (float) Math.sin(now * 0.008f) : 0f;
                drawBox(station.x, TABLE_Y + 0.05f, station.z, 0.58f, 0.10f, 0.42f,
                        Math.min(1f, color[0] + pulse), Math.min(1f, color[1] + pulse), Math.min(1f, color[2] + pulse), 1f);
                drawBox(station.x, TABLE_Y + 0.24f, station.z - 0.08f, 0.30f, 0.25f, 0.16f,
                        color[0] * 0.65f, color[1] * 0.65f, color[2] * 0.65f, 1f);
            }
        }

        private void drawItems() {
            Item[] snapshot;
            int grabbed;
            synchronized (this) {
                snapshot = new Item[] {items[0].copy(), items[1].copy(), items[2].copy()};
                grabbed = grabbedIndex;
            }
            for (int i = 0; i < snapshot.length; i++) {
                Item item = snapshot[i];
                float[] color = COLORS[item.colorIndex];
                float lift = i == grabbed ? 0.24f : 0f;
                if (item.colorIndex == 0) {
                    drawMug(item.x, TABLE_Y + 0.25f + lift, item.z, color);
                } else if (item.colorIndex == 1) {
                    drawBox(item.x, TABLE_Y + 0.25f + lift, item.z, 0.34f, 0.34f, 0.34f, color[0], color[1], color[2], 1f);
                    drawBox(item.x, TABLE_Y + 0.44f + lift, item.z, 0.38f, 0.04f, 0.38f, 0.80f, 0.32f, 0.12f, 1f);
                } else {
                    drawOrb(item.x, TABLE_Y + 0.27f + lift, item.z, color);
                }
                if (i == grabbed) {
                    drawBox(item.x, TABLE_Y + 0.58f + lift, item.z, 0.50f, 0.04f, 0.50f, 1f, 1f, 1f, 1f);
                }
            }
        }

        private void drawMug(float x, float y, float z, float[] color) {
            drawBox(x, y, z, 0.28f, 0.42f, 0.28f, color[0], color[1], color[2], 1f);
            drawBox(x, y + 0.18f, z, 0.30f, 0.05f, 0.30f, 1f, 0.96f, 0.55f, 1f);
            drawBox(x + 0.20f, y + 0.02f, z, 0.07f, 0.24f, 0.08f, color[0], color[1] * 0.82f, color[2] * 0.82f, 1f);
            drawBox(x + 0.28f, y + 0.02f, z, 0.07f, 0.24f, 0.08f, color[0], color[1] * 0.82f, color[2] * 0.82f, 1f);
            drawBox(x + 0.24f, y + 0.15f, z, 0.16f, 0.06f, 0.08f, color[0], color[1] * 0.82f, color[2] * 0.82f, 1f);
        }

        private void drawOrb(float x, float y, float z, float[] color) {
            drawBox(x, y, z, 0.34f, 0.34f, 0.34f, color[0], color[1], color[2], 1f);
            drawBox(x, y, z, 0.46f, 0.10f, 0.46f, color[0] * 0.75f, color[1], color[2], 1f);
            drawBox(x, y, z, 0.10f, 0.46f, 0.46f, color[0] * 0.75f, color[1], color[2], 1f);
            drawBox(x, y, z, 0.46f, 0.46f, 0.10f, color[0] * 0.75f, color[1], color[2], 1f);
        }

        private void drawReticle() {
            float x;
            float z;
            synchronized (this) {
                x = aimToWorldX(aimX);
                z = aimToWorldZ(aimY);
            }
            drawBox(x, TABLE_Y + 0.12f, z, 0.54f, 0.025f, 0.025f, 0.15f, 0.95f, 1f, 1f);
            drawBox(x, TABLE_Y + 0.12f, z, 0.025f, 0.025f, 0.54f, 0.15f, 0.95f, 1f, 1f);
            drawBox(x, TABLE_Y + 0.26f, z, 0.08f, 0.28f, 0.08f, 0.15f, 0.95f, 1f, 1f);
        }

        private void drawBox(float x, float y, float z, float sx, float sy, float sz,
                             float r, float g, float b, float a) {
            Matrix.setIdentityM(model, 0);
            Matrix.translateM(model, 0, x, y, z);
            Matrix.scaleM(model, 0, sx, sy, sz);
            Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0);

            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
            GLES20.glUniform4f(colorHandle, r, g, b, a);
            cubeBuffer.position(0);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, cubeBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, CUBE.length / 3);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private int createProgram() {
            String vertexShader =
                    "uniform mat4 uMvp;" +
                    "attribute vec4 aPosition;" +
                    "void main() {" +
                    "  gl_Position = uMvp * aPosition;" +
                    "}";
            String fragmentShader =
                    "precision mediump float;" +
                    "uniform vec4 uColor;" +
                    "void main() {" +
                    "  gl_FragColor = uColor;" +
                    "}";
            int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
            int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
            int glProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(glProgram, vertex);
            GLES20.glAttachShader(glProgram, fragment);
            GLES20.glLinkProgram(glProgram);
            return glProgram;
        }

        private int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            return shader;
        }

        private void handleGrabTap() {
            if (grabbedIndex >= 0) {
                moveGrabbedItem();
                maybeCompleteTask(grabbedIndex);
                grabbedIndex = -1;
                return;
            }

            int nearest = -1;
            float best = 0.36f;
            float targetX = aimToWorldX(aimX);
            float targetZ = aimToWorldZ(aimY);
            for (int i = 0; i < items.length; i++) {
                float distance = distance(items[i].x, items[i].z, targetX, targetZ);
                if (distance < best) {
                    best = distance;
                    nearest = i;
                }
            }
            if (nearest >= 0) {
                grabbedIndex = nearest;
                moveGrabbedItem();
            }
        }

        private void moveGrabbedItem() {
            if (grabbedIndex < 0) {
                return;
            }
            items[grabbedIndex].x = aimToWorldX(aimX);
            items[grabbedIndex].z = aimToWorldZ(aimY);
        }

        private void maybeCompleteTask(int itemIndex) {
            if (itemIndex != currentTask) {
                return;
            }
            Station target = stations[currentTask];
            Item item = items[itemIndex];
            if (distance(item.x, item.z, target.x, target.z) > 0.45f) {
                return;
            }
            currentTask++;
            if (currentTask >= stations.length) {
                currentTask = 0;
                completedLoops++;
                resetItemPositions();
            }
            completeFlashUntilMs = System.currentTimeMillis() + 900L;
        }

        private void resetGame() {
            currentTask = 0;
            completedLoops = 0;
            grabbedIndex = -1;
            aimX = 0.5f;
            aimY = 0.5f;
            resetItemPositions();
        }

        private void resetItemPositions() {
            for (Item item : items) {
                item.reset();
            }
        }

        private float aimToWorldX(float value) {
            return (value - 0.5f) * 2.75f;
        }

        private float aimToWorldZ(float value) {
            return -1.95f + clamp(value, 0f, 1f) * 1.45f;
        }

        private float distance(float ax, float az, float bx, float bz) {
            float dx = ax - bx;
            float dz = az - bz;
            return (float) Math.sqrt(dx * dx + dz * dz);
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

        private float wrapRadians(float value) {
            while (value > Math.PI) {
                value -= Math.PI * 2f;
            }
            while (value < -Math.PI) {
                value += Math.PI * 2f;
            }
            return value;
        }

        private static final class Item {
            final String label;
            final float homeX;
            final float homeZ;
            final int colorIndex;
            float x;
            float z;

            Item(String label, float homeX, float homeZ, int colorIndex) {
                this.label = label;
                this.homeX = homeX;
                this.homeZ = homeZ;
                this.colorIndex = colorIndex;
                reset();
            }

            void reset() {
                x = homeX;
                z = homeZ;
            }

            Item copy() {
                Item copy = new Item(label, homeX, homeZ, colorIndex);
                copy.x = x;
                copy.z = z;
                return copy;
            }
        }

        private static final class Station {
            final String label;
            final float x;
            final float z;
            final int colorIndex;

            Station(String label, float x, float z, int colorIndex) {
                this.label = label;
                this.x = x;
                this.z = z;
                this.colorIndex = colorIndex;
            }
        }
    }
}
