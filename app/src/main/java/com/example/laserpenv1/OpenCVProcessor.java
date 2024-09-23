package com.example.laserpenv1;

import android.content.Context;
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
        if (detectedHSVValue == null) return;

        // 将图像转换为HSV色彩空间
        Mat hsv = new Mat();
        Imgproc.cvtColor(rgbaMat, hsv, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV);

        // 使用动态HSV值创建掩模
        Mat laserMask = new Mat();
        Scalar lowerHSV = new Scalar(1, 35, 195);
        Scalar upperHSV = new Scalar(179, 145, 255);
        Core.inRange(hsv, lowerHSV, upperHSV, laserMask);

        // 腐蚀与膨胀处理
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(9, 9));
        Imgproc.morphologyEx(laserMask, laserMask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(laserMask, laserMask, Imgproc.MORPH_CLOSE, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(laserMask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // 处理找到的轮廓
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

            // 处理光点的逻辑...
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
