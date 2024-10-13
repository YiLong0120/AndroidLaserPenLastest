package com.example.laserpenv1;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
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
    private ArrayList<Point> laserPoints = new ArrayList<>();
    private boolean isDraggingInProgress = false;


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

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (contours.isEmpty()) {
            return;
        }

        // 找到最大的輪廓
        MatOfPoint largestContour = contours.get(0);
        double maxArea = Imgproc.contourArea(largestContour);
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > maxArea) {
                maxArea = area;
                largestContour = contour;
            }
        }

        // 使用多邊形近似來簡化輪廓
        MatOfPoint2f contour2f = new MatOfPoint2f(largestContour.toArray());
        double epsilon = 0.02 * Imgproc.arcLength(contour2f, true);
        MatOfPoint2f approxCurve = new MatOfPoint2f();
        Imgproc.approxPolyDP(contour2f, approxCurve, epsilon, true);

        // 偵測到四個頂點，表示找到可能的投影幕
        Point[] corners = approxCurve.toArray();
        if (corners.length == 4) {
            // 繪製綠色邊框
            for (int i = 0; i < 4; i++) {
                Imgproc.line(rgbaMat, corners[i], corners[(i + 1) % 4], new Scalar(0, 255, 0), 2);
            }

            // 在每個角落畫一個小圓圈
            for (Point corner : corners) {
                Imgproc.circle(rgbaMat, corner, 5, new Scalar(255, 0, 0), -1);
            }

            // 如果偵測到四個角並且按下按鈕，進行透視變形校正
            if (isFrameLocked) {
                // 定義手機屏幕的四個角
                Point[] dstCorners = new Point[4];
                dstCorners[0] = new Point(0, 0);                        // 左上角
                dstCorners[1] = new Point(rgbaMat.cols(), 0);           // 右上角
                dstCorners[2] = new Point(rgbaMat.cols(), rgbaMat.rows()); // 右下角
                dstCorners[3] = new Point(0, rgbaMat.rows());           // 左下角

                // 透視變換矩陣
                MatOfPoint2f srcMat = new MatOfPoint2f(corners);
                MatOfPoint2f dstMat = new MatOfPoint2f(dstCorners);
                Mat perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat);

                // 進行透視變形校正
                Mat warpedMat = new Mat();
                Imgproc.warpPerspective(rgbaMat, warpedMat, perspectiveTransform, rgbaMat.size());

                // 將校正後的影像顯示
                warpedMat.copyTo(rgbaMat);

                // 釋放資源
                warpedMat.release();
                perspectiveTransform.release();
            }

            if (!isFrameLocked) {
                calculateScaleFactors();  // 計算比例，用於後續應用
            }
        } else {
            // 如果沒有找到精確的四邊形，退回到使用邊界矩形
            screenBoundingRect = Imgproc.boundingRect(largestContour);
            if (!isFrameLocked) {
                Imgproc.rectangle(rgbaMat, screenBoundingRect.tl(), screenBoundingRect.br(), new Scalar(0, 255, 0), 2);
                calculateScaleFactors();
            }
        }

        // 釋放資源
        binaryMat.release();
        hierarchy.release();
        contour2f.release();
        approxCurve.release();
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

    private void detectLaserPoints(Mat rgbaMat)
    {
        Mat hsv = new Mat();
        Imgproc.cvtColor(rgbaMat, hsv, Imgproc.COLOR_RGB2HSV);

        //40,170,120
        // tv 60,50,200
        Scalar lowerGreen = new Scalar(30,160,110);
        Scalar upperGreen = new Scalar(50,180,130);

        Mat greenMask = new Mat();
        Core.inRange(hsv, lowerGreen, upperGreen, greenMask);

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

            int mappedY = (int) (((laserX - screenBoundingRect.x) / (float) screenBoundingRect.width) * displayHeight);
            int mappedX = displayWidth - (int) (((laserY - screenBoundingRect.y) / (float) screenBoundingRect.height) * displayWidth);

            Imgproc.circle(rgbaMat, new Point(laserX, laserY), 10, new Scalar(0, 255, 0), 3);

            lastLaserPoint = new Point(mappedX, mappedY);

            // 進行閃爍判斷和拖移處理
            processLaserFlashing(mappedX, mappedY);

            // 傳送滑鼠移動指令
            Intent moveIntent = new Intent(context, MouseAccessibilityService.class);
            moveIntent.putExtra("x", mappedX);
            moveIntent.putExtra("y", mappedY);
            context.startService(moveIntent);

            if (pointListener != null) {
                pointListener.onPointDetected(mappedX, mappedY);
            }

        } else {
            resetFlashDetection();
        }
    }
    private void processLaserFlashing(int mappedX, int mappedY) {
        // 如果正在进行拖移操作，则不再捕获新的拖移动作
        if (isDraggingInProgress) {
            return;
        }

        // 将当前的激光笔坐标添加到列表中
        laserPoints.add(new Point(mappedX, mappedY));

        // 当我们检测到足够的点（例如 10 个），就进行拖移
        if (laserPoints.size() >= 10) {
            // 将激光笔轨迹传递给 AccessibilityService
            Intent intent = new Intent(context, MyAccessibilityService.class);
            ArrayList<int[]> coordinates = new ArrayList<>();

            for (Point point : laserPoints) {
                coordinates.add(new int[]{(int) point.x, (int) point.y});
            }

            intent.putExtra("coordinates", coordinates);
            context.startService(intent);  // 启动服务进行拖移

            Log.d("OpenCVProcessor", "Starting drag with coordinates: " + coordinates.toString());

            // 清空坐标点列表并标记为拖移进行中
            isDraggingInProgress = true;

            // 使用runOnUiThread来保证Handler在主线程中运行
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                laserPoints.clear(); // 清空已经拖移的坐标
                isDraggingInProgress = false; // 拖移完成，允许新的拖移
            }, 1000); // 拖移持续时间（可根据需要调整）
        }

        // 更新最后的光点位置
        lastLaserPoint = new Point(mappedX, mappedY);
    }



    private void handleLaserClick(int mappedX, int mappedY) {
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
    }






    private void detectLaserFlashes() {
        long currentTime = System.currentTimeMillis();
        if (isLaserStationary && currentTime - lastFlashTime >= FLASH_DELAY) {
            flashCount++;
            lastFlashTime = currentTime;
        }
    }

    private void resetFlashDetection() {
        isLaserStationary = false;
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
    public void detectHSVPoints(Mat rgbaMat) {
        Mat hsvMat = new Mat();
        Imgproc.cvtColor(rgbaMat, hsvMat, Imgproc.COLOR_RGB2HSV);

        // 這裡的範圍可以根據需要調整
        Scalar lowerHSV = new Scalar(55, 75, 235); // 下限
        Scalar upperHSV = new Scalar(75, 95, 255); // 上限

        Mat mask = new Mat();
        Core.inRange(hsvMat, lowerHSV, upperHSV, mask);

        Mat dilatedMask = new Mat();
        Imgproc.dilate(mask, dilatedMask, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(15, 15)));

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

        if (boundingRect != null) {
            int laserX = boundingRect.x + boundingRect.width / 2;
            int laserY = boundingRect.y + boundingRect.height / 2;

            Log.d(TAG, "Laser Point Detected: " + laserX + ", " + laserY);

            Imgproc.circle(rgbaMat, new Point(laserX, laserY), 10, new Scalar(0, 255, 0), 3);

            if (pointListener != null) {
                pointListener.onPointDetected(laserX, laserY);
            }
        } else {
            Log.d(TAG, "No laser point detected");
        }

        hsvMat.release();
        mask.release();
        dilatedMask.release();
        hierarchy.release();
    }
    public Mat getCurrentFrame() {
        return frame; // 返回当前帧
    }
}
