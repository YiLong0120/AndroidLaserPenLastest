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
        Mat frame = inputFrame.rgba();

        if (!isFrameLocked) {
            Mat hsvMat = new Mat();
            Imgproc.cvtColor(frame, hsvMat, Imgproc.COLOR_RGB2HSV);

            Scalar lowerThresholdForScreen = new Scalar(0, 0, 200);
            Scalar upperThresholdForScreen = new Scalar(180, 25, 255);

            Mat screenMask = new Mat();
            Core.inRange(hsvMat, lowerThresholdForScreen, upperThresholdForScreen, screenMask);

            detectProjectionScreen(frame, screenMask);
        } else {
            detectLaserPoints(frame);
        }

        return frame;
    }

    private void detectProjectionScreen(Mat rgbaMat, Mat screenMask) {
        List<MatOfPoint> screenContours = new ArrayList<>();
        Imgproc.findContours(screenMask, screenContours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

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
            if (!isFrameLocked) {  // 只有在未鎖定時顯示綠色框框
                Imgproc.rectangle(rgbaMat, maxRect.tl(), maxRect.br(), new Scalar(0, 255, 0), 2);
            }
            // 計算縮放因子
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

        Scalar lowerGreen = new Scalar(35, 100, 100);
        Scalar upperGreen = new Scalar(85, 255, 255);

        Mat greenMask = new Mat();
        Core.inRange(hsv, lowerGreen, upperGreen, greenMask);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(9, 9));
        Imgproc.morphologyEx(greenMask, greenMask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(greenMask, greenMask, Imgproc.MORPH_CLOSE, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(greenMask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

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

            if (lastLaserPoint != null && getDistance(lastLaserPoint, new Point(mappedX, mappedY)) < 50) {
                if (!isLaserStationary) {
                    isLaserStationary = true;
                    laserHandler.postDelayed(laserRunnable = () -> {
                        if (isLaserStationary) {
                            Intent clickIntent = new Intent(context, MyAccessibilityService.class);
                            clickIntent.putExtra("x", mappedX);
                            clickIntent.putExtra("y", mappedY);
                            clickIntent.putExtra("isFrameLocked", isFrameLocked);
                            context.startService(clickIntent);
                        }
                    }, 1000); // 2秒延迟
                }
            } else {
                isLaserStationary = false;
                laserHandler.removeCallbacks(laserRunnable);
            }

            lastLaserPoint = new Point(mappedX, mappedY);

            Intent moveIntent = new Intent(context, MouseAccessibilityService.class);
            moveIntent.putExtra("x", mappedX);
            moveIntent.putExtra("y", mappedY);
            context.startService(moveIntent);

            if (pointListener != null) {
                pointListener.onPointDetected(mappedX, mappedY);
            }
        } else {
            isLaserStationary = false;
            laserHandler.removeCallbacks(laserRunnable);
        }
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
}
