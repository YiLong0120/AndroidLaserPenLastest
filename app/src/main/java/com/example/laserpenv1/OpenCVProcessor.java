package com.example.laserpenv1;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class OpenCVProcessor implements CameraBridgeViewBase.CvCameraViewListener2 {

    private Context context;
    private PointListener pointListener;
    private Handler mainHandler;
    private int displayWidth;
    private int displayHeight;
    private int cameraFrameWidth;
    private int cameraFrameHeight;

    private Rect screenBoundingRect = null;
    private boolean isFrameLocked = false;
    private Point lastLaserPoint = null;
    private Handler laserHandler = new Handler(Looper.getMainLooper());
    private Runnable laserRunnable;
    private boolean isLaserStationary = false;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private Mat frame; // 用來保存當前相機幀
    private static final String TAG = "OpenCVProcessor";
    private Scalar detectedHSVValue;
    private boolean isDetectingHSV = false;

    private Scalar lowerHSV;
    private Scalar upperHSV;

    private boolean isFlashing = false;
    private int flashCount = 0;
    private long lastFlashTime = 0;
    private static final int FLASH_DELAY = 500; // 定義閃爍間隔，0.5秒

    public interface PointListener {
        void onPointDetected(int x, int y);
    }

    public OpenCVProcessor(Context context, PointListener pointListener) {
        this.context = context;
        this.pointListener = pointListener;
        this.mainHandler = new Handler(Looper.getMainLooper());

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        displayWidth = displayMetrics.widthPixels;
        displayHeight = displayMetrics.heightPixels;
    }

    public void setScaleFactors(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        cameraFrameWidth = width;
        cameraFrameHeight = height;
    }

    @Override
    public void onCameraViewStopped() {
        // 清理資源
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        frame = inputFrame.rgba();

        if (!isFrameLocked) {
            // 將畫面轉換為灰階
            Mat grayMat = new Mat();
            Imgproc.cvtColor(frame, grayMat, Imgproc.COLOR_RGBA2GRAY);

            // 背景減除與二值化處理
            detectProjectionScreen(frame, grayMat);
        } else {
            detectLaserPoints(frame);
        }

        return frame;
    }

    private void detectProjectionScreen(Mat rgbaMat, Mat grayMat) {
        Mat binaryMat = new Mat();
        Imgproc.threshold(grayMat, binaryMat, 50, 255, Imgproc.THRESH_BINARY);

        List<MatOfPoint> screenContours = new ArrayList<>();
        Imgproc.findContours(binaryMat, screenContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double maxArea = 0;
        Rect maxRect = null;
        for (MatOfPoint contour : screenContours) {
            Rect rect = Imgproc.boundingRect(contour);
            double area = Imgproc.contourArea(contour);
            if (area > maxArea) {
                maxArea = area;
                maxRect = rect;
            }
        }

        if (maxRect != null) {
            screenBoundingRect = maxRect;
            if (!isFrameLocked) {
                Imgproc.rectangle(rgbaMat, maxRect.tl(), maxRect.br(), new Scalar(0, 255, 0), 2);
            }
            calculateScaleFactors();
        }
    }

    public void toggleFrameLock() {
        isFrameLocked = !isFrameLocked;
        if (isFrameLocked) {
            mainHandler.post(() -> Toast.makeText(context, "Frame locked", Toast.LENGTH_SHORT).show());
        } else {
            mainHandler.post(() -> Toast.makeText(context, "Frame unlocked", Toast.LENGTH_SHORT).show());
        }
    }

    public boolean isFrameLocked() {
        return isFrameLocked;
    }

    public Rect getScreenBoundingRect() {
        return screenBoundingRect;
    }

    private void detectLaserPoints(Mat rgbaMat) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(rgbaMat, hsv, Imgproc.COLOR_RGB2HSV);

        Scalar lowerGreen = new Scalar(30, 100, 100);
        Scalar upperGreen = new Scalar(90, 255, 255);

        Mat greenMask = new Mat();
        Core.inRange(hsv, lowerGreen, upperGreen, greenMask);

        Mat contrast = new Mat();
        Core.convertScaleAbs(hsv, contrast, 2, 0);

        Mat dilatedMask = new Mat();
        Imgproc.dilate(greenMask, dilatedMask, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(15, 15)));

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(dilatedMask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        Rect boundingRect = null;
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            if (boundingRect == null || rect.area() > boundingRect.area()) {
                boundingRect = rect;
            }
        }

        if (boundingRect != null && screenBoundingRect != null &&
                screenBoundingRect.contains(new Point(boundingRect.x + boundingRect.width / 2, boundingRect.y + boundingRect.height / 2))) {

            int laserX = boundingRect.x + boundingRect.width / 2;
            int laserY = boundingRect.y + boundingRect.height / 2;

            int mappedX = displayWidth - ((int) (((laserY - screenBoundingRect.y) / (float) screenBoundingRect.height) * displayWidth));
            int mappedY = (int) (((laserX - screenBoundingRect.x) / (float) screenBoundingRect.width) * displayHeight);

            Imgproc.circle(rgbaMat, new Point(laserX, laserY), 10, new Scalar(0, 255, 0), 3);

            // 判斷雷射點是否穩定
            if (lastLaserPoint != null && getDistance(lastLaserPoint, new Point(mappedX, mappedY)) < 50) {
                isLaserStationary = true;

                // 檢測雷射閃爍
                detectLaserFlashes();

                // 只有當閃爍數量達到2次時才會觸發點擊
                if (flashCount >= 2) {
                    triggerClick(mappedX, mappedY);
                    flashCount = 0;
                    resetFlashDetection();
                }
            } else {
                isLaserStationary = false;
                resetFlashDetection();
            }

            lastLaserPoint = new Point(mappedX, mappedY);

            // 傳送滑鼠移動指令
            Intent moveIntent = new Intent(context, MouseAccessibilityService.class);
            moveIntent.putExtra("x", mappedX);
            moveIntent.putExtra("y", mappedY);
            context.startService(moveIntent);

            if (pointListener != null) {
                pointListener.onPointDetected(mappedX, mappedY);
            }
        } else {
            resetFlashDetection(); // 找不到雷射點時重置閃爍檢測
        }
    }

    private void detectLaserFlashes() {
        long currentTime = System.currentTimeMillis();
        if (isLaserStationary && currentTime - lastFlashTime >= FLASH_DELAY) {
            flashCount++;
            lastFlashTime = currentTime;
        }
    }

    private void resetFlashDetection() {
        flashCount = 0;
        lastFlashTime = 0;
    }

    // 觸發點擊方法
    private void triggerClick(int x, int y) {
        Intent clickIntent = new Intent(context, MyAccessibilityService.class);
        clickIntent.putExtra("x", x);
        clickIntent.putExtra("y", y);
        clickIntent.putExtra("isFrameLocked", isFrameLocked);
        context.startService(clickIntent);
    }



    private void calculateScaleFactors() {
        if (screenBoundingRect != null) {
            scaleX = (float) displayWidth / screenBoundingRect.width;
            scaleY = (float) displayHeight / screenBoundingRect.height;
        }
    }

    private double getDistance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    public void startHSVDetection() {
        if (frame != null) {
            detectHSVPoints(frame);  // 开始检测HSV光点
        }
    }
    private void detectHSVPoints(Mat rgbaMat) {
        // 将图像转换为HSV色彩空间
        Mat hsv = new Mat();
        Imgproc.cvtColor(rgbaMat, hsv, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV);

        // 背景减除与二值化
        Mat mask = new Mat();
        Core.inRange(hsv, new Scalar(0, 0, 0), new Scalar(180, 255, 255), mask); // 提取整个图像

        // 腐蚀与膨胀处理
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(9, 9));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        if (!contours.isEmpty()) {
            // 提取第一个轮廓的中心
            Rect boundingRect = Imgproc.boundingRect(contours.get(0));
            int laserX = boundingRect.x + boundingRect.width / 2;
            int laserY = boundingRect.y + boundingRect.height / 2;

            // 计算并存储HSV值
            detectedHSVValue = new Scalar(
                    Core.mean(hsv.submat(boundingRect)).val[0],
                    Core.mean(hsv.submat(boundingRect)).val[1],
                    Core.mean(hsv.submat(boundingRect)).val[2]
            );

            // 显示HSV值
            mainHandler.post(() -> Toast.makeText(context, "检测到HSV值: " + detectedHSVValue.toString(), Toast.LENGTH_SHORT).show());

            // 结束HSV检测
            isDetectingHSV = false;

            // 存储动态HSV范围
            lowerHSV = new Scalar(
                    Math.max(detectedHSVValue.val[0] - 10, 0),
                    Math.max(detectedHSVValue.val[1] - 50, 0),
                    Math.max(detectedHSVValue.val[2] - 50, 0)
            );
            upperHSV = new Scalar(
                    Math.min(detectedHSVValue.val[0] + 10, 180),
                    Math.min(detectedHSVValue.val[1] + 50, 255),
                    Math.min(detectedHSVValue.val[2] + 50, 255)
            );
            isDetectingHSV = false;
        }
    }
}
