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

public class MyAccessibilityService extends AccessibilityService {

    private Handler handler = new Handler();
    private Runnable dragRunnable;
    private int startX = 0;
    private int startY = 0;
    private int endX = 0;
    private int endY = 0;
    private boolean isDragging = false;

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
        handler.postDelayed(() -> startDragging(), 2000);
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
            startX = intent.getIntExtra("x_start", startX);
            startY = intent.getIntExtra("y_start", startY);
            endX = intent.getIntExtra("x_end", endX);
            endY = intent.getIntExtra("y_end", endY);
            isDragging = true;  // 直接設置為 true

            startDragging();  // 開始拖移
        }
        return START_STICKY;
    }

    // 开始拖移操作
    private void startDragging() {
        dragRunnable = new Runnable() {
            @Override
            public void run() {
                if (isDragging) {
                    performDrag(startX, startY, endX, endY);
                }
                handler.postDelayed(this, 10000); // 每秒执行一次拖移操作
            }
        };
        handler.post(dragRunnable);
    }

    // 执行拖移手势
    private void performDrag(int startX, int startY, int endX, int endY) {
        if (startX < 0 || startY < 0 || startX >= getDisplayWidth() || startY >= getDisplayHeight()
                || endX < 0 || endY < 0 || endX >= getDisplayWidth() || endY >= getDisplayHeight()) {
            Log.e("MyAccessibilityService", "Invalid drag coordinates: from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")");
            return;
        }

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        GestureDescription.StrokeDescription strokeDescription = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            strokeDescription = new GestureDescription.StrokeDescription(path, 0, 500); // 500ms 进行滑动
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
                    Toast.makeText(MyAccessibilityService.this, "Drag performed", Toast.LENGTH_SHORT).show();
                    Log.d("MyAccessibilityService", "Drag performed from (" + startX + ", " + startY + ") to (" + endX + ", " + endY + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d("MyAccessibilityService", "Drag cancelled");
                }
            }, null);

            if (!result) {
                Log.d("MyAccessibilityService", "Drag dispatch failed");
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
