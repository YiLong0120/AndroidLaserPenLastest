package com.example.laserpenv1;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;

public class FloatingCameraService extends Service {

    private static final String TAG = "FloatingCameraService";
    private WindowManager mWindowManager;
    private View mFloatingView;
    private JavaCameraView mOpenCvCameraView;
    private OpenCVProcessor openCVProcessor;
    private boolean isMenuExpanded = false;
    private boolean isWhiteScreen = false;
    private View whiteScreenOverlay;

    @Override
    public void onCreate() {
        super.onCreate();

        // 檢查並請求懸浮窗權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Overlay permission not granted, requesting...");
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                stopSelf(); // 如果沒有權限，停止服務，等待權限開啟後重新啟動
                return;
            } else {
                Log.d(TAG, "Overlay permission granted.");
            }
        }

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!");
            stopSelf();
            return;
        }
        Log.i(TAG, "OpenCV loaded successfully");

        // Set up the floating window layout parameters for camera view
        WindowManager.LayoutParams cameraParams = new WindowManager.LayoutParams(
                200, 150,  // Fixed size for camera view
                // 嘗試使用不同的類型來處理懸浮視窗問題
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE, // 舊設備使用 TYPE_PHONE
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // Position the floating camera view at the top center
        cameraParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        cameraParams.y = 100;

        // Inflate the floating view layout
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.activity_mini_camera, null);

        // Initialize the OpenCV camera view
        mOpenCvCameraView = mFloatingView.findViewById(R.id.miniCameraView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        // Set up the OpenCV processor
        openCVProcessor = new OpenCVProcessor(this, null);
        mOpenCvCameraView.setCvCameraViewListener(openCVProcessor);
        mOpenCvCameraView.setCameraPermissionGranted();  // <-- Important for API 23+

        // Ensure the SurfaceView is ready before enabling the camera
        mOpenCvCameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mOpenCvCameraView.enableView();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // You can adjust camera parameters here if needed
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mOpenCvCameraView.disableView();
            }
        });

        // Add the floating view to the window manager
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, cameraParams);

        // Provide a message to inform the user
        Toast.makeText(this, "Floating Camera Started", Toast.LENGTH_SHORT).show();

        // Inflate the floating button layout and add it to the floating view
        View floatingButton = LayoutInflater.from(this).inflate(R.layout.floating_button_layout, null);

        // Set up the floating button layout parameters
        WindowManager.LayoutParams buttonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE, // 舊設備使用 TYPE_PHONE
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // Position the floating button at the top left
        buttonParams.gravity = Gravity.TOP | Gravity.START;
        buttonParams.x = 10; // Offset from the left
        buttonParams.y = 10; // Offset from the top

        // Add the floating button to the window manager
        mWindowManager.addView(floatingButton, buttonParams);

        // Set up button click events
        Button menuButton = floatingButton.findViewById(R.id.floating_button);
        Button whiteScreenButton = floatingButton.findViewById(R.id.white_screen_button);
        Button lockFrameButton = floatingButton.findViewById(R.id.lock_frame_button);
        Button exitButton = floatingButton.findViewById(R.id.exit_button);

        // Set the menu button click listener
        menuButton.setOnClickListener(v -> {
            if (isMenuExpanded) {
                whiteScreenButton.setVisibility(View.GONE);
                lockFrameButton.setVisibility(View.GONE);
                exitButton.setVisibility(View.GONE);
            } else {
                whiteScreenButton.setVisibility(View.VISIBLE);
                lockFrameButton.setVisibility(View.VISIBLE);
                exitButton.setVisibility(View.VISIBLE);  // Show the exit button
            }
            isMenuExpanded = !isMenuExpanded;
        });

        // Set the lock frame button click listener
        lockFrameButton.setOnClickListener(v -> {
            if (openCVProcessor != null) {
                openCVProcessor.toggleFrameLock();
            }
        });

        // Set the white screen button click listener
        whiteScreenButton.setOnClickListener(v -> toggleWhiteScreen());

        // Set the exit button click listener to stop all services
        exitButton.setOnClickListener(v -> {
            stopAllServices();
            System.exit(0);  // Close the entire app
        });

        // Set up touch listener to move the button
        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int x, y;
            private float xOffset, yOffset;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        x = (int) event.getRawX();
                        y = (int) event.getRawY();
                        xOffset = event.getRawX() - buttonParams.x;
                        yOffset = event.getRawY() - buttonParams.y;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        buttonParams.x = (int) (event.getRawX() - xOffset);
                        buttonParams.y = (int) (event.getRawY() - yOffset);
                        mWindowManager.updateViewLayout(floatingButton, buttonParams);
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleWhiteScreen() {
        if (isWhiteScreen) {
            if (whiteScreenOverlay != null) {
                mWindowManager.removeView(whiteScreenOverlay);
                whiteScreenOverlay = null;
            }
        } else {
            whiteScreenOverlay = new View(this);
            whiteScreenOverlay.setBackgroundColor(0xFFFFFFFF); // 设置为白色
            WindowManager.LayoutParams whiteScreenParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE, // 舊設備使用 TYPE_PHONE
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            // 确保白色覆盖层位于按钮下方
            whiteScreenParams.gravity = Gravity.TOP | Gravity.START;
            mWindowManager.addView(whiteScreenOverlay, whiteScreenParams);
        }
        isWhiteScreen = !isWhiteScreen;
    }

    // Stop all running services
    private void stopAllServices() {
        stopSelf();  // Stop this service

        // Stop MyAccessibilityService
        Intent myAccessibilityService = new Intent(this, MyAccessibilityService.class);
        stopService(myAccessibilityService);

        // Stop MouseAccessibilityService
        Intent mouseAccessibilityService = new Intent(this, MouseAccessibilityService.class);
        stopService(mouseAccessibilityService);

        Toast.makeText(this, "All Services Stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
        if (mFloatingView != null) {
            mWindowManager.removeView(mFloatingView);
        }
        if (whiteScreenOverlay != null) {
            mWindowManager.removeView(whiteScreenOverlay);
        }
    }
}
