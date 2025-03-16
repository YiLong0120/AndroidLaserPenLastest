package com.example.laserpenv1;

import static android.Manifest.permission.CAMERA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class StartScreenActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_screen);

        Button teacherInfoButton = findViewById(R.id.teacherInfoButton);
        Button cameraPermissionButton = findViewById(R.id.cameraPermissionButton);
        Button overlayPermissionButton = findViewById(R.id.overlayPermissionButton);
        Button accessibilityPermissionButton = findViewById(R.id.accessibilityPermissionButton);
        Button startButton = findViewById(R.id.startButton);
        View fogView = findViewById(R.id.fogView);

        // 老師資訊按鈕點擊事件
        teacherInfoButton.setOnClickListener(v -> {
            // 跳轉到指定網址
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/cswang"));
            startActivity(browserIntent);
        });

        cameraPermissionButton.setOnClickListener(v -> requestCameraPermission());
        overlayPermissionButton.setOnClickListener(v -> requestOverlayPermission());
        accessibilityPermissionButton.setOnClickListener(v -> requestAccessibilityPermission());
        startButton.setOnClickListener(v -> {
            if (checkAllPermissionsGranted()) {
                Intent intent = new Intent(StartScreenActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "請先授予所有必要的權限", Toast.LENGTH_SHORT).show();
            }
        });
        fogView.setVisibility(View.VISIBLE);
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, CAMERA) != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            Toast.makeText(this, "相机权限已授予", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            Toast.makeText(this, "显示在其他应用程序上层权限已授予", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestAccessibilityPermission() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am.isEnabled()) {
            Toast.makeText(this, "无障碍服务已启用", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
    }

    private boolean checkAllPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, CAMERA) == PERMISSION_GRANTED &&
                Settings.canDrawOverlays(this) &&
                isAccessibilityEnabled();
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am.isEnabled();
    }
}
