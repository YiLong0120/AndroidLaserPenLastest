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

    @Override
    public void onServiceConnected() {

        super.onServiceConnected();
        Log.d("MyAccessibilityService", "Service connected");
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
        if (intent != null && !isDraggingInProgress) {
            ArrayList<int[]> coordinates = (ArrayList<int[]>) intent.getSerializableExtra("coordinates");

            if (coordinates != null && coordinates.size() >= 2) {
                // 开始拖移
                isDraggingInProgress = true;
                performDrag(coordinates);
            } else {
                Log.e("MyAccessibilityService", "Invalid coordinates for drag");
            }
        }
        return START_STICKY;
    }


    // 开始拖移操作
//    private void startDragging() {
//        dragRunnable = new Runnable() {
//            @Override
//            public void run() {
//                if (isDragging) {
//                    performDrag(startX, startY, endX, endY);
//                }
//                handler.postDelayed(this, 10000); // 每秒执行一次拖移操作
//            }
//        };
//        handler.post(dragRunnable);
//    }

    // 执行拖移手势
    // 执行拖移操作
    private void performDrag(ArrayList<int[]> coordinates) {
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
                    Log.d("MyAccessibilityService", "Drag performed successfully");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    isDraggingInProgress = false;  // 拖移取消
                    Log.d("MyAccessibilityService", "Drag cancelled");
                }
            }, null);

            if (!result) {
                Log.d("MyAccessibilityService", "Drag dispatch failed");
                isDraggingInProgress = false;
            }
        } else {
            Log.d("MyAccessibilityService", "API level not supported for gestures");
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
}
