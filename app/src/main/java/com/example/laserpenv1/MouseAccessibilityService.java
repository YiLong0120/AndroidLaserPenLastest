package com.example.laserpenv1;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import androidx.annotation.Nullable;

public class MouseAccessibilityService extends Service {
    private WindowManager windowManager;
    private View cursorView;
    private WindowManager.LayoutParams cursorLayout;
    private int displayWidth;
    private int displayHeight;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        cursorView = View.inflate(getBaseContext(), R.layout.cursor_layout, null);
        cursorLayout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        cursorLayout.gravity = Gravity.TOP | Gravity.LEFT;
        cursorLayout.x = 200;
        cursorLayout.y = 200;

        // 获取屏幕宽度和高度
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        displayWidth = displayMetrics.widthPixels;
        displayHeight = displayMetrics.heightPixels;

        // 添加 View
        windowManager.addView(cursorView, cursorLayout);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int x = intent.getIntExtra("x", cursorLayout.x);
            int y = intent.getIntExtra("y", cursorLayout.y);
            moveTo(x, y);

            // 向 MyAccessibilityService 传递坐标
            Intent clickIntent = new Intent(this, MyAccessibilityService.class);
            clickIntent.putExtra("x", x);
            clickIntent.putExtra("y", y);
            startService(clickIntent);
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cursorView != null) {
            windowManager.removeView(cursorView);
        }
    }

    private void moveTo(int x, int y) {
        // 更新游标位置
        cursorLayout.x = x;
        cursorLayout.y = y;
        windowManager.updateViewLayout(cursorView, cursorLayout);
    }
}
