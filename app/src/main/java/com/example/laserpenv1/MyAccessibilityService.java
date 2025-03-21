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

    private void performDrag(ArrayList<int[]> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            Log.e("GestureError", "Coordinates list is empty or null");
            return;
        }

        // 检查所有点的坐标是否有效
        for (int[] point : coordinates) {
            if (point.length < 2 || point[0] < 0 || point[1] < 0) {
                Log.e("GestureError", "Invalid drag coordinates: (" + point[0] + ", " + point[1] + ")");
                return;
            }
        }

        Path path = new Path();
        // 根据第一个点移动到初始位置
        int[] firstPoint = coordinates.get(0);
        path.moveTo(firstPoint[0], firstPoint[1]);

        // 依次将所有点连接起来，生成完整路径
        for (int i = 1; i < coordinates.size(); i++) {
            int[] point = coordinates.get(i);
            path.lineTo(point[0], point[1]);
        }

        GestureDescription.StrokeDescription strokeDescription = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            strokeDescription = new GestureDescription.StrokeDescription(path, 0, 1000); // 根据轨迹拖移
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
                    isDraggingInProgress = false;  // 拖移完成
                    Log.d(TAG, "Drag performed successfully");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    isDraggingInProgress = false;  // 拖移取消
                    Log.d(TAG, "Drag cancelled");
                }
            }, null);

            if (!result) {
                Log.d(TAG, "Drag dispatch failed");
                isDraggingInProgress = false;
            }
        } else {
            Log.d(TAG, "API level not supported for gestures");
        }
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
            GestureDescription.StrokeDescription strokeDescription = new GestureDescription.StrokeDescription(path, 0, 5);
            GestureDescription gestureDescription = new GestureDescription.Builder().addStroke(strokeDescription).build();

            // 執行拖曳操作
            dispatchGesture(gestureDescription, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "Segment of drag performed successfully");

                    // 拖曳完成後模擬停止操作
                    simulateStopAtEndPoint(endX, endY);
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

    private void simulateStopAtEndPoint(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 創建一個模擬按下和釋放的點擊操作（停止動作）
            Path stopPath = new Path();
            stopPath.moveTo(x, y);

            GestureDescription.StrokeDescription stopStroke =
                    new GestureDescription.StrokeDescription(stopPath, 0, 100); // 停止動作時間可調整
            GestureDescription stopGesture = new GestureDescription.Builder().addStroke(stopStroke).build();

            // 執行停止動作
            dispatchGesture(stopGesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "Drag stopped at endpoint successfully");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, "Stop action cancelled");
                }
            }, null);
        } else {
            Log.d(TAG, "API level not supported for stop action");
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
