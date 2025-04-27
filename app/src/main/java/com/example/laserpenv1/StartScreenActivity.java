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

//        Button teacherInfoButton = findViewById(R.id.teacherInfoButton);
        Button cameraPermissionButton = findViewById(R.id.cameraPermissionButton);
        Button overlayPermissionButton = findViewById(R.id.overlayPermissionButton);
        Button accessibilityPermissionButton = findViewById(R.id.accessibilityPermissionButton);
        Button startButton = findViewById(R.id.startButton);
        View fogView = findViewById(R.id.fogView);

//        // 老師資訊按鈕
//        teacherInfoButton.setOnClickListener(v -> {
//            Intent intent = new Intent(StartScreenActivity.this, StudentAndSchoolInfoActivity.class);
//            startActivity(intent);
//        });

        // 權限按鈕
        cameraPermissionButton.setOnClickListener(v -> requestCameraPermission());
        overlayPermissionButton.setOnClickListener(v -> requestOverlayPermission());
        accessibilityPermissionButton.setOnClickListener(v -> requestAccessibilityPermission());

        // 開始按鈕
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

    // 申請相機權限（直接彈出系統對話框）
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, CAMERA) != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            Toast.makeText(this, "相機權限已授予", Toast.LENGTH_SHORT).show();
        }
    }

    // 申請懸浮視窗權限（必須跳轉設定，無法自動授權）
    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            Toast.makeText(this, "已允許懸浮視窗權限", Toast.LENGTH_SHORT).show();
        }
    }

    // 申請無障礙權限（Android 不允許自動啟動，仍需手動開啟）
    private void requestAccessibilityPermission() {
        if (isAccessibilityEnabled()) {
            Toast.makeText(this, "無障礙服務已啟用", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "請手動啟用無障礙服務", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }
    }

    // 確認所有權限是否已授權
    private boolean checkAllPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, CAMERA) == PERMISSION_GRANTED &&
                Settings.canDrawOverlays(this) &&
                isAccessibilityEnabled();
    }

    // 檢查無障礙服務是否啟用
    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am.isEnabled();
    }
}
