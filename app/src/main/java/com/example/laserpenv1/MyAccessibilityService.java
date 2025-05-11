package com.example.laserpenv1;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {

    private Handler handler = new Handler();
    private Runnable dragRunnable;
    private int startX = 0;
    private int startY = 0;
    private int endX = 0;
    private int endY = 0;
    private boolean isDragging = false;
    private boolean isDraggingInProgress = false;  // 标记当前是否有拖移进行中
    String TAG = "MyAccessibilityService";


    @Override
    public void onServiceConnected() {

        super.onServiceConnected();
        Log.d(TAG, "Service connected");
        Toast.makeText(this, "Accessibility Service Connected", Toast.LENGTH_SHORT).show();

        // 启动后返回主屏幕
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);

        // 延迟启动拖移操作，确保主屏幕显示
//        handler.postDelayed(() -> startDragging(), 2000);
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(dragRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(dragRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String actionType = intent.getStringExtra("action_type");

            if ("click".equals(actionType)) {
                int clickX = intent.getIntExtra("x", -1);
                int clickY = intent.getIntExtra("y", -1);
                performClick(clickX, clickY);
            } else if ("drag".equals(actionType)) {
                ArrayList<int[]> coordinates = (ArrayList<int[]>) intent.getSerializableExtra("coordinates");
                if (coordinates != null && coordinates.size() >= 2) {
                    performDrag(coordinates);
                } else {
                    Log.e(TAG, "Invalid coordinates for drag");
                }
            } else if ("drag_single".equals(actionType)) {
                int startX = intent.getIntExtra("startX", -1);
                int startY = intent.getIntExtra("startY", -1);
                int endX = intent.getIntExtra("endX", -1);
                int endY = intent.getIntExtra("endY", -1);
                performSingleDrag(startX, startY, endX, endY);
            } else if ("pressBack".equals(actionType)) {
                performGlobalBack(1);
            } else if ("pressHome".equals(actionType)) {
                performGlobalBack(2);
            } else if ("pressRecent".equals(actionType)) {
                performGlobalBack(3);
            }
        }
        return START_STICKY;
    }

    // 在 MyAccessibilityService 類別新增以下成員變數
    private GestureDescription.StrokeDescription currentStroke = null;
    private static final int BATCH_SIZE = 10; // 每10個點發送一次拖曳段
    private long lastDragTime = 0;

    // 修改後的 performDrag 方法
    private void performDrag(ArrayList<int[]> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) return;

        // 動態計算持續時間（根據移動速度調整）
        int[] first = coordinates.get(0);
        int[] last = coordinates.get(coordinates.size()-1);
        long timeDiff = System.currentTimeMillis() - lastDragTime;
        double speed = calculateSpeed(first, last, timeDiff);
        int dynamicDuration = (int) Math.min(500, Math.max(50, 1000 / (speed + 1)));

        // 分段處理座標
        for (int i=0; i<coordinates.size(); i+=BATCH_SIZE) {
            int endIndex = Math.min(i+BATCH_SIZE, coordinates.size());
            List<int[]> batch = coordinates.subList(i, endIndex);
            submitGestureBatch(batch, dynamicDuration);
        }

        lastDragTime = System.currentTimeMillis();
    }

    // 新增分段提交方法
    private void submitGestureBatch(List<int[]> batch, int duration) {
        if (batch.size() < 2) return;

        Path path = new Path();
        path.moveTo(batch.get(0)[0], batch.get(0)[1]);
        for (int i=1; i<batch.size(); i++) {
            path.lineTo(batch.get(i)[0], batch.get(i)[1]);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.StrokeDescription stroke;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && currentStroke != null) {
                // API 26+ 使用連續手勢
                stroke = currentStroke.continueStroke(path, 0, duration, true);
            } else {
                // 低版本API獨立手勢
                stroke = new GestureDescription.StrokeDescription(path, 0, duration);
            }

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();

            dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        currentStroke = stroke;
                    }
                }
            }, null);
        }
    }

    // 計算移動速度（像素/毫秒）
    private double calculateSpeed(int[] start, int[] end, long timeDiff) {
        if (timeDiff == 0) return 0;
        double distance = Math.hypot(end[0]-start[0], end[1]-start[1]);
        return distance / timeDiff;
    }


    private void performSingleDrag(int startX, int startY, int endX, int endY) {
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            Log.e("GestureError", "Invalid single drag coordinates");
            return;
        }

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 使用較短的拖曳時間以更頻繁更新光標
            GestureDescription.StrokeDescription strokeDescription = new GestureDescription.StrokeDescription(path, 0, 1);
            GestureDescription gestureDescription = new GestureDescription.Builder().addStroke(strokeDescription).build();

            // 執行拖曳操作
            dispatchGesture(gestureDescription, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "Segment of drag performed successfully");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, "Segment of drag cancelled");
                }
            }, null);
        } else {
            Log.d(TAG, "API level not supported for gestures");
        }
    }



    private void performClick(int x, int y) {
        if (x < 0 || y < 0 || x >= getDisplayWidth() || y >= getDisplayHeight()) {
            Log.e(TAG, "Invalid click coordinates: (" + x + ", " + y + ")");
            return;
        }

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription strokeDescription = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            strokeDescription = new GestureDescription.StrokeDescription(path, 0, 100);
        }
        GestureDescription gestureDescription = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gestureDescription = new GestureDescription.Builder().addStroke(strokeDescription).build();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            boolean result = dispatchGesture(gestureDescription, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
//                    Toast.makeText(ClickAccessibilityService.this, "Click performed", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Click performed at (" + x + ", " + y + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, "Click cancelled at (" + x + ", " + y + ")");
                }
            }, null);
            if (!result) {
                Log.d(TAG, "Click dispatch failed");
            }
        } else {
            Log.d(TAG, "API level not supported for gestures");
        }
    }


    // 获取屏幕宽度
    private int getDisplayWidth() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels;
    }

    // 获取屏幕高度
    private int getDisplayHeight() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels;
    }
    public void performGlobalBack(int input) {
        performGlobalAction(input);
        Log.d(TAG, "performGlobalBack: " + input);
    }
}
