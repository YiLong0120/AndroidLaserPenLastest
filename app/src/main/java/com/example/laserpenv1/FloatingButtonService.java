//package com.example.laserpenv1;
//
//import android.app.Service;
//import android.content.Intent;
//import android.graphics.PixelFormat;
//import android.os.Build;
//import android.os.IBinder;
//import android.view.Gravity;
//import android.view.LayoutInflater;
//import android.view.MotionEvent;
//import android.view.View;
//import android.view.WindowManager;
//import android.widget.Button;
//
//public class FloatingButtonService extends Service {
//
//    private WindowManager windowManager;
//    private View floatingButton;
//    private OpenCVProcessor openCVProcessor;
//    private boolean isMenuExpanded = false;
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
//
//        // Inflate the floating button layout
//        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
//        floatingButton = inflater.inflate(R.layout.floating_button_layout, null);
//
//        // Set up layout parameters for the floating button
//        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
//                WindowManager.LayoutParams.WRAP_CONTENT,
//                WindowManager.LayoutParams.WRAP_CONTENT,
//                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//                PixelFormat.TRANSLUCENT
//        );
//
//        params.gravity = Gravity.TOP | Gravity.LEFT;
//        params.x = 0;
//        params.y = 100;
//
//        // Add the floating button to the window
//        windowManager.addView(floatingButton, params);
//
//        // Set up button click events
//        Button menuButton = floatingButton.findViewById(R.id.floating_button);
//        Button whiteButton = floatingButton.findViewById(R.id.white_button);
//        Button lockFrameButton = floatingButton.findViewById(R.id.lock_frame_button);
//
//        // Set the menu button click listener
//        menuButton.setOnClickListener(v -> {
//            if (isMenuExpanded) {
//                whiteButton.setVisibility(View.GONE);
//                lockFrameButton.setVisibility(View.GONE);
//            } else {
//                whiteButton.setVisibility(View.VISIBLE);
//                lockFrameButton.setVisibility(View.VISIBLE);
//            }
//            isMenuExpanded = !isMenuExpanded;
//        });
//
//        // Set the lock frame button click listener
//        lockFrameButton.setOnClickListener(v -> {
//            if (openCVProcessor != null) {
//                openCVProcessor.toggleFrameLock();
//            }
//        });
//
//        // Set up touch listener to move the button
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
//                        xOffset = event.getRawX() - params.x;
//                        yOffset = event.getRawY() - params.y;
//                        return true;
//
//                    case MotionEvent.ACTION_MOVE:
//                        params.x = (int) (event.getRawX() - xOffset);
//                        params.y = (int) (event.getRawY() - yOffset);
//                        windowManager.updateViewLayout(floatingButton, params);
//                        return true;
//                }
//                return false;
//            }
//        });
//    }
//
//    public void setOpenCVProcessor(OpenCVProcessor processor) {
//        this.openCVProcessor = processor;
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        if (floatingButton != null) {
//            windowManager.removeView(floatingButton);
//        }
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//}