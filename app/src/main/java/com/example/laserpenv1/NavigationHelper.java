package com.example.laserpenv1;

// 在您的 Activity 或服务中定义以下方法

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class NavigationHelper extends AccessibilityService {

    String TAG = "NavigationHelper";


//    @Override
//    public void onBackPressed() {
//        Log.d(TAG, "onBackPressed: " + counter);
//        counter++;
//        if(counter==2){
//            super.onBackPressed();
//            counter = 0;
//        }
//
//    }
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要處理具體事件
    }

    @Override
    public void onInterrupt() {
        // 處理中斷邏輯（如無特別需求可留空）
    }

    // 模擬返回鍵
    public void performGlobalBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }


}
