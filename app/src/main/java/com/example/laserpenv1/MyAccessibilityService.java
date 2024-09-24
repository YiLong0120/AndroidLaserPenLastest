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
    private Runnable clickRunnable;
    private int clickX = 0;
    private int clickY = 0;
    private boolean isFrameLocked = false; // 增加的变量

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d("MyAccessibilityService", "Service connected");
        Toast.makeText(this, "Accessibility Service Connected", Toast.LENGTH_SHORT).show();

        // Launch the home screen
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);

        // Start clicking after a delay to ensure home screen is visible
        handler.postDelayed(() -> startClicking(), 2000);
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(clickRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(clickRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            clickX = intent.getIntExtra("x", clickX);
            clickY = intent.getIntExtra("y", clickY);
            isFrameLocked = intent.getBooleanExtra("isFrameLocked", isFrameLocked); // 更新状态
            if (isFrameLocked) { // 只有在锁定边框时才执行点击
                performClick(clickX, clickY);
            }
        }
        return START_STICKY;
    }


    private void startClicking() {
        clickRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFrameLocked) { // 只有在锁定边框时才执行点击
                    performClick(clickX, clickY);
                }
                handler.postDelayed(this, 1000); // Repeat every second
            }
        };
        handler.post(clickRunnable);
    }

    private void performClick(int x, int y) {
        if (x < 0 || y < 0 || x >= getDisplayWidth() || y >= getDisplayHeight()) {
            Log.e("MyAccessibilityService", "Invalid click coordinates: (" + x + ", " + y + ")");
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
                    // 只有点击成功时才显示提示
                    Toast.makeText(MyAccessibilityService.this, "Click performed", Toast.LENGTH_SHORT).show();
                    Log.d("MyAccessibilityService", "Click performed at (" + x + ", " + y + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d("MyAccessibilityService", "Click cancelled at (" + x + ", " + y + ")");
                }
            }, null);

            if (!result) {
                Log.d("MyAccessibilityService", "Click dispatch failed");
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
