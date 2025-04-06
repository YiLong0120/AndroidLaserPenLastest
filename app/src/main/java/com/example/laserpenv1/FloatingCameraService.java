package com.example.laserpenv1;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Scalar;

public class FloatingCameraService extends Service {

    private static final String TAG = "FloatingCameraService";
    private WindowManager mWindowManager;
    private View mFloatingView;
    private JavaCameraView mOpenCvCameraView;
    private OpenCVProcessor openCVProcessor;
    private boolean isMenuExpanded = false;
    private boolean isDetectingHSV = false; // 新增状态标记
    private Scalar detectedHSVValue; // 存储检测到的HSV值
    private Context context;



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

        // 初始化 OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed!");
            stopSelf();
            return;
        }
        Log.i(TAG, "OpenCV loaded successfully");

        // 設定相機視圖的浮窗佈局參數
        WindowManager.LayoutParams cameraParams = new WindowManager.LayoutParams(
                200, 150,
//                WindowManager.LayoutParams.MATCH_PARENT, // 寬度填滿
//                WindowManager.LayoutParams.MATCH_PARENT, // 高度填滿
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // 將浮動相機視圖放置在頂部中心
        cameraParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        cameraParams.y = 10;

        // 膨脹浮動視圖佈局
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.activity_mini_camera, null);

        // 初始化 OpenCV 相機視圖
        mOpenCvCameraView = mFloatingView.findViewById(R.id.miniCameraView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        // 設定OpenCV processor
        openCVProcessor = new OpenCVProcessor(this, null);
        mOpenCvCameraView.setCvCameraViewListener(openCVProcessor);
        mOpenCvCameraView.setCameraPermissionGranted();  // <-- Important for API 23+

        // 在啟用相機之前確保 SurfaceView 已準備好
        mOpenCvCameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mOpenCvCameraView.enableView();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 如果需要，您可以在此處調整相機參數
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mOpenCvCameraView.disableView();
            }
        });

        // 將浮動視圖新增至window manager
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, cameraParams);

        // 提供訊息通知用戶
        Toast.makeText(this, "Floating Camera Started", Toast.LENGTH_SHORT).show();

        // 膨脹浮動按鈕佈局並將其新增至浮動視圖
        View floatingButton = LayoutInflater.from(this).inflate(R.layout.floating_button_layout, null);

        // 設定浮動按鈕佈局參數
        WindowManager.LayoutParams buttonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // 將浮動按鈕放置在左上角
        buttonParams.gravity = Gravity.TOP | Gravity.LEFT;

        // 將浮動按鈕新增至視窗管理器
        mWindowManager.addView(floatingButton, buttonParams);



        View backButton = LayoutInflater.from(this).inflate(R.layout.floating_button_back, null);

        // 設定浮動按鈕佈局參數
        WindowManager.LayoutParams backButtonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // 將浮動按鈕放置在左上角
        backButtonParams.gravity = Gravity.BOTTOM;

        // 將浮動按鈕新增至視窗管理器
        mWindowManager.addView(backButton, backButtonParams);


        View switchButton = LayoutInflater.from(this).inflate(R.layout.floating_button_switch, null);

        // 設定浮動按鈕佈局參數
        WindowManager.LayoutParams switchButtonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // 將浮動按鈕放置在左上角
        switchButtonParams.gravity = Gravity.TOP | Gravity.RIGHT;

        // 將浮動按鈕新增至視窗管理器
        mWindowManager.addView(switchButton, switchButtonParams);

        // 設定按鈕點擊事件
        ImageButton menuButton = floatingButton.findViewById(R.id.floating_button);
        Button lockFrameButton = floatingButton.findViewById(R.id.lock_frame_button);
//        Button hsvButton = floatingButton.findViewById(R.id.hsv_button); // 新增 HSV 按鈕
        Button exitButton = floatingButton.findViewById(R.id.exit_button);
//        Button btnSetHSV = floatingButton.findViewById(R.id.btn_set_hsv);
        Button mainButton = floatingButton.findViewById(R.id.main_btm);
        Button toggleCameraButton = floatingButton.findViewById(R.id.toggleCameraButton);

        ImageButton BackBtn = backButton.findViewById(R.id.BackBtn);

        Button SwitchDrag = switchButton.findViewById(R.id.SwitchDrag);




//        EditText editTextH = floatingButton.findViewById(R.id.H);
//        EditText editTextS = floatingButton.findViewById(R.id.S);
//        EditText editTextV = floatingButton.findViewById(R.id.V);
//        Button submitbtn = floatingButton.findViewById(R.id.Submit);
        BackBtn.setOnClickListener(v -> pressKeyButton());
        SwitchDrag.setOnClickListener(v -> {
            Button button = (Button) v; // 轉換為按鈕類型
            String currentText = button.getText().toString();

            // 根據當前文字切換到下一個狀態
            switch (currentText) {
                case "點擊":
                    button.setText("拖曳");
                    break;
                case "拖曳":
                    button.setText("暫定");
                    break;
                default:
                    button.setText("點擊");
                    break;
            }

            // 調用 openCVProcessor 的方法
            openCVProcessor.onButtonClicked();
        });





        // 設定選單按鈕點擊監聽器
        menuButton.setOnClickListener(v -> {
            if (isMenuExpanded) {
                lockFrameButton.setVisibility(View.GONE);
//                hsvButton.setVisibility(View.GONE); // 隱藏 HSV 按鈕
//                btnSetHSV.setVisibility(View.GONE);
                exitButton.setVisibility(View.GONE);
                mainButton.setVisibility(View.GONE);
                toggleCameraButton.setVisibility(View.GONE);
            } else {
                lockFrameButton.setVisibility(View.VISIBLE);
//                hsvButton.setVisibility(View.VISIBLE); // 顯示 HSV 按鈕
//                btnSetHSV.setVisibility(View.VISIBLE);
                exitButton.setVisibility(View.VISIBLE);
                mainButton.setVisibility(View.VISIBLE);
                toggleCameraButton.setVisibility(View.VISIBLE);
            }
            isMenuExpanded = !isMenuExpanded;
        });

        // 設定鎖框按鈕點擊監聽器
        lockFrameButton.setOnClickListener(v -> {
            if (openCVProcessor != null) {
                openCVProcessor.toggleFrameLock();
            }
        });

        mainButton.setOnClickListener(v -> {
            Intent intent = new Intent(FloatingCameraService.this, StartScreenActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
//            stopSelf();
        });

        // 設置按鈕點擊事件來切換相機視圖的顯示與隱藏
        toggleCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 判斷相機視圖是否可見
                if (mOpenCvCameraView.getVisibility() == SurfaceView.VISIBLE) {
                    // 隱藏相機視圖，但保持相機運行
                    mOpenCvCameraView.setVisibility(SurfaceView.GONE);  // 隱藏畫面
                    // 這裡不停止相機流，保持相機處於運行狀態
                } else {
                    // 顯示相機視圖
                    mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
                }
            }
        });

        // 设置HSV按钮点击监听器
//        hsvButton.setOnClickListener(v -> {
//            if (!isDetectingHSV) {
//                // 如果当前未开始检测，则启动检测
//                isDetectingHSV = true;
//                hsvButton.setText("Record HSV"); // 修改按钮文本以指示状态
//                Log.d(TAG, "detect HSV");
//
//                // 启动一个新的线程来进行HSV检测，避免阻塞UI线程
//                new Thread(() -> {
//                    while (isDetectingHSV) {
//                        // 调用OpenCVProcessor的detectHSVPoints方法
//                        if (openCVProcessor != null) {
//                            Mat currentFrame = openCVProcessor.getCurrentFrame();
//                            if (currentFrame != null) {
//                                openCVProcessor.detectHSVPoints(currentFrame);
//                            }
//                        }
//
//                        // 暂停一段时间，避免过于频繁地调用
//                        try {
//                            Thread.sleep(100); // 例如每100毫秒调用一次
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }).start();
//            } else {
//                // 如果当前正在检测，则停止检测
//                isDetectingHSV = false;
//                hsvButton.setText("stop detecting"); // 恢复按钮文本
//                Log.d(TAG, "stop detecting");
//            }
//        });

        // Set the exit button click listener to stop all services
        exitButton.setOnClickListener(v -> {
            stopAllServices();
            System.exit(0);  // Close the entire app
        });

        // Set up touch listener to move the button
//        floatingButton.setOnTouchListener(new View.OnTouchListener() {
//            private int x, y;
//            private float xOffset, yOffset;
//
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                switch (event.getAction()) {
//                    case MotionEvent.ACTION_DOWN:
//                        x = (int) event.getRawX();
//                        y = (int) event.getRawY();
//                        xOffset = event.getRawX() - buttonParams.x;
//                        yOffset = event.getRawY() - buttonParams.y;
//                        return true;
//
//                    case MotionEvent.ACTION_MOVE:
//                        buttonParams.x = (int) (event.getRawX() - xOffset);
//                        buttonParams.y = (int) (event.getRawY() - yOffset);
//                        mWindowManager.updateViewLayout(floatingButton, buttonParams);
//                        return true;
//                }
//                return false;
//            }
//        });

//        btnSetHSV.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent = new Intent(FloatingCameraService.this, HSVActivity.class);
//                startActivity(intent);
//                // 设置输入框为可见
////                editTextH.setVisibility(View.VISIBLE);
////                editTextS.setVisibility(View.VISIBLE);
////                editTextV.setVisibility(View.VISIBLE);
////                submitbtn.setVisibility(View.VISIBLE);
////
////
////                try {
////                    // 从输入框中获取值
////                    float h = Float.parseFloat(editTextH.getText().toString());
////                    float s = Float.parseFloat(editTextS.getText().toString());
////                    float vValue = Float.parseFloat(editTextV.getText().toString());
////
////                    // 将值传递给 openCVProcessor.inputHSV()
////                    openCVProcessor.inputHSV(h, s, vValue);
////
////                } catch (NumberFormatException e) {
////                    // 如果用户输入不是数字，显示错误提示
////                    Toast.makeText(getApplicationContext(), "Invalid HSV values", Toast.LENGTH_SHORT).show();
////                }
//            }
//        });

//        submitbtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                TextView textView = floatingButton.findViewById(R.id.TextView);
//                textView.setText(editTextH.getText());
//            }
//        });

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
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
        if (mWindowManager != null && mFloatingView != null) {
            mWindowManager.removeView(mFloatingView);
        }
        super.onDestroy();
    }


    int i = 0;
    // 返回鍵功能
    private long lastPressTime = 0; // 上次按下时间
    private int pressCount = 0;     // 1秒内的按下次数

    public void pressKeyButton() {
        long currentTime = System.currentTimeMillis(); // 获取当前时间

        // 判断是否在 1 秒内
        if (currentTime - lastPressTime <= 500) {
            pressCount++; // 增加按下次数
        } else {
            // 超过 1 秒，重置计数器
            pressCount = 1;
        }

        lastPressTime = currentTime; // 更新上次按下时间

        // 限制按键次数循环
        int i = pressCount % 3;
        if (i == 0) i = 3; // 确保 i 在 1~3 之间循环

        Intent intent = new Intent(FloatingCameraService.this, MyAccessibilityService.class);
        switch (i) {
            case 1:
                intent.putExtra("action_type", "pressBack");
                break;
            case 2:
                intent.putExtra("action_type", "pressHome");
                break;
            case 3:
                intent.putExtra("action_type", "pressRecent");
                break;
        }

        Log.d("FloatingCameraService", "Press count within 1 second: " + pressCount);
        startService(intent);
    }


}
