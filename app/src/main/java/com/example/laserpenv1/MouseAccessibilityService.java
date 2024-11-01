package com.example.laserpenv1;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
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

        // 檢查並請求懸浮窗權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                stopSelf(); // 如果沒有權限，停止服務
                return;
            }
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 初始化游標視圖
        cursorView = View.inflate(getBaseContext(), R.layout.cursor_layout, null);

        // 根據不同 Android 版本設定懸浮窗參數
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
        }

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 200;
        params.y = 200;

        cursorLayout = params;

        // 获取屏幕宽度和高度
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        displayWidth = displayMetrics.widthPixels;
        displayHeight = displayMetrics.heightPixels;

        // 添加游标视图
        windowManager.addView(cursorView, cursorLayout);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int x = intent.getIntExtra("x", cursorLayout.x);
            int y = intent.getIntExtra("y", cursorLayout.y);
            moveTo(x, y);

//            // 向 MyAccessibilityService 传递坐标
//            Intent clickIntent = new Intent(this, MyAccessibilityService.class);
//            clickIntent.putExtra("x", x);
//            clickIntent.putExtra("y", y);
//            startService(clickIntent);
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
