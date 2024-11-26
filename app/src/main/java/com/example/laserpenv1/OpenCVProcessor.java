/*
色相、飽和度、明度
Scalar lowerHSV = new Scalar(55, 75, 235); // 下限
Scalar upperHSV = new Scalar(75, 95, 255); // 上限
 */

//Scalar lowerGreen = new Scalar(70, 80, 240);
//Scalar upperGreen = new Scalar(90, 100, 255);

package com.example.laserpenv1;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
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
import java.util.Arrays;
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
    private Mat perspectiveTransform;
    private Point[] savedCorners = null;
    int getRotation;
    // 假设手机屏幕的宽高
    int screenWidth;
    int screenHeight;
    boolean wasLaserPreviouslyVisible = false;
    long windowStartTime = 0;
    long lastLaserVisibleTime = 0;
    boolean cnadrag = false;
    boolean hasTriggeredClick = false;
    private ArrayList<int[]> laserCoordinates = new ArrayList<>();
    float H=70, S=90, V=245;



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
        Log.d(TAG, "size" + displayWidth + displayHeight);
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
        // 获取摄像头的原始图像尺寸
        frame = inputFrame.rgba();
        Size originalSize = frame.size();  // 保存原始尺寸

        // 根据当前屏幕方向旋转画面
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        Log.d(TAG, "rotation:" + rotation);
        switch (rotation) {
            case Surface.ROTATION_90: // 屏幕左旋90度
                Core.rotate(frame, frame, Core.ROTATE_90_COUNTERCLOCKWISE);
                getRotation = rotation;
                break;
            case Surface.ROTATION_180: // 屏幕倒转180度
                Core.rotate(frame, frame, Core.ROTATE_180);
                break;
            case Surface.ROTATION_270: // 屏幕右旋90度
                Core.rotate(frame, frame, Core.ROTATE_90_CLOCKWISE);
                break;
        }
        Mat grayMat = new Mat();
        Imgproc.cvtColor(frame, grayMat, Imgproc.COLOR_RGBA2GRAY);
        // 继续图像处理流程
        if (!isFrameLocked) {
            // 背景减除与二值化处理
            detectProjectionScreen(frame, grayMat);
        } else {
            detectLaserPoints(frame, grayMat);
        }

        // 在返回前调整旋转后的 `rotatedFrame` 到原始尺寸
        if (frame.size() != originalSize) {
            Imgproc.resize(frame, frame, originalSize);
        }

        return frame;
    }


    // 偵測投影邊框的函數
    private void detectProjectionScreen(Mat rgbaMat, Mat grayMat) {
        Mat binaryMat = new Mat();
        Imgproc.threshold(grayMat, binaryMat, 50, 255, Imgproc.THRESH_BINARY);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (contours.isEmpty()) {
            // 如果当前没有找到边框，继续使用保存的边框
            if (savedCorners != null) {
                drawProjectionFrame(rgbaMat, savedCorners);
            }
            return;
        }

        // 找到最大的轮廓
        MatOfPoint largestContour = contours.get(0);
        double maxArea = Imgproc.contourArea(largestContour);
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > maxArea) {
                maxArea = area;
                largestContour = contour;
            }
        }

        // 使用多边形近似来简化轮廓
        MatOfPoint2f contour2f = new MatOfPoint2f(largestContour.toArray());
        double epsilon = 0.02 * Imgproc.arcLength(contour2f, true);
        MatOfPoint2f approxCurve = new MatOfPoint2f();
        Imgproc.approxPolyDP(contour2f, approxCurve, epsilon, true);

        // 检测到四个顶点，表示找到可能的投影幕
        Point[] corners = approxCurve.toArray();
        if (corners.length == 4) {
            // 画出投影边框的四个角点
            drawProjectionFrame(rgbaMat, corners);

            // 保存当前检测到的角点
            savedCorners = corners;
            Point temp = savedCorners[0];
            savedCorners[0] = savedCorners[2];
            savedCorners[2] = temp;
            logCorners("Detected Corners", savedCorners); // 添加这行



            // 定义手机屏幕的四个角
            Point[] dstCorners = new Point[4];
            dstCorners[0] = new Point(0, 0);                        // 左上角
            dstCorners[1] = new Point(rgbaMat.cols(), 0);           // 右上角
            dstCorners[2] = new Point(rgbaMat.cols(), rgbaMat.rows()); // 右下角
            dstCorners[3] = new Point(0, rgbaMat.rows());           // 左下角

            logCorners("Destination Corners", dstCorners);

            // 透视变换矩阵
            MatOfPoint2f srcMat = new MatOfPoint2f(corners);
            MatOfPoint2f dstMat = new MatOfPoint2f(dstCorners);
            perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat);  // 保存变换矩阵

            // 计算比例缩放因子
            calculateScaleFactors();
        } else if (savedCorners != null) {
            // 如果没有找到新的四个角点，但已保存的角点存在，继续显示已保存的投影框
            drawProjectionFrame(rgbaMat, savedCorners);
        }

        // 释放资源
        binaryMat.release();
        hierarchy.release();
        contour2f.release();
        approxCurve.release();
    }





    private void logCorners(String label, Point[] corners) {
        Log.d("ProjectionCoordinates", label + ":");
        for (Point corner : corners) {
            Log.d("ProjectionCoordinates", "角坐标: (" + corner.x + ", " + corner.y + ")");
        }
    }

    // 辅助函数：绘制投影边框
    private void drawProjectionFrame(Mat rgbaMat, Point[] corners) {
        for (int i = 0; i < 4; i++) {
            Imgproc.line(rgbaMat, corners[i], corners[(i + 1) % 4], new Scalar(0, 255, 0), 2);
        }
        // 在每个角落绘制数字0, 1, 2, 3
        for (int i = 0; i < corners.length; i++) {
            Imgproc.circle(rgbaMat, corners[i], 10, new Scalar(0, 0, 255), -1); // 在每个角上画红色圆点
            Imgproc.putText(rgbaMat, String.valueOf(i), new Point(corners[i].x + 10, corners[i].y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 0, 0), 1); // 绘制数字
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

    private void detectLaserPoints(Mat rgbaMat, Mat grayMat) {
        // 高斯模糊，减少噪声
        Imgproc.GaussianBlur(grayMat, grayMat, new Size(5, 5), 0);

        // 自适应二值化
        Mat binaryMat = new Mat();
        Imgproc.adaptiveThreshold(
                grayMat, binaryMat, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11, 2
        );

        // 转换到 HSV 空间
        Mat hsvMat = new Mat();
        Imgproc.cvtColor(rgbaMat, hsvMat, Imgproc.COLOR_RGB2HSV);

        // 定义绿色的 HSV 范围
        Scalar lowerGreen = new Scalar(60, 50, 200); // 宽松的下限
        Scalar upperGreen = new Scalar(100, 255, 255); // 宽松的上限
        Mat greenMask = new Mat();
        Core.inRange(hsvMat, lowerGreen, upperGreen, greenMask);

        // 结合二值化和 HSV 掩膜
        Mat combinedMask = new Mat();
        Core.bitwise_and(binaryMat, greenMask, combinedMask);

        // 膨胀操作以突出光点
        Mat dilatedMask = new Mat();
        Imgproc.dilate(combinedMask, dilatedMask, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(15, 15)));

        // 查找轮廓
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(dilatedMask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // 获取最大轮廓的边界框
        Rect boundingRect = null;
        double minArea = 50.0; // 最小轮廓面积阈值
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            if (rect.area() >= minArea && (boundingRect == null || rect.area() > boundingRect.area())) {
                boundingRect = rect;
            }
        }

        boolean laserDetected = false; // 是否检测到激光光点的标志
        int scaledX = 0, scaledY = 0;

        if (boundingRect != null && perspectiveTransform != null) {
            // 激光光点的原始坐标
            int laserX = boundingRect.x + boundingRect.width / 2;
            int laserY = boundingRect.y + boundingRect.height / 2;

            // 应用透视变换
            MatOfPoint2f originalPoint = new MatOfPoint2f(new Point(laserX, laserY));
            MatOfPoint2f transformedPoint = new MatOfPoint2f();
            Core.perspectiveTransform(originalPoint, transformedPoint, perspectiveTransform);

            // 获取变换后的坐标
            Point correctedPoint = transformedPoint.toArray()[0];
            int correctedX = (int) correctedPoint.x;
            int correctedY = (int) correctedPoint.y;

            // 按比例缩放坐标
            scaledX = (int) (correctedX * scaleX);
            scaledY = (int) (correctedY * scaleY);

            // 限制坐标范围，避免噪声导致的异常
            if (scaledX >= 0 && scaledX < screenWidth && scaledY >= 0 && scaledY < screenHeight) {
                laserDetected = true;
            }
        }

        // 平滑坐标并绘制光点
        if (laserDetected) {
            missedFrames = 0; // 重置丢帧计数器
            lastLaserDetected = true;

            // 平滑处理后的坐标
            Point smoothedPoint = smoothPoint(new Point(scaledX, scaledY));
            Imgproc.circle(rgbaMat, smoothedPoint, 10, new Scalar(255, 0, 0), 3);
            showMouse((int) smoothedPoint.x, (int) smoothedPoint.y);
        }

        // 添加 Logcat 提示
        if (laserDetected) {
            Log.d("LaserDetection", "Laser point detected at: (" + scaledX + ", " + scaledY + ")");
        } else {
            Log.d("LaserDetection", "No laser point detected.");
        }

        // 将检测状态传递给其他方法
        processLaserFlashing(scaledX, scaledY, laserDetected);

        // 释放资源
        binaryMat.release();
        hsvMat.release();
        greenMask.release();
        combinedMask.release();
        dilatedMask.release();
        hierarchy.release();
    }

    private void showMouse(int scaledX, int scaledY){
        // 传送鼠标移动指令
        Intent moveIntent = new Intent(context, MouseAccessibilityService.class);
        moveIntent.putExtra("x", scaledX);
        moveIntent.putExtra("y", scaledY);
        context.startService(moveIntent);
    }


    // 修改后的 processLaserFlashing 方法，增加 laserDetected 参数
    private void processLaserFlashing(int mappedX, int mappedY, boolean laserDetected) {
        long currentTime = System.currentTimeMillis();
        Log.d("LaserFlashing", "Laser flash count: " + flashCount);

        if (laserDetected) {
            // 初始化计时窗口
            if (flashCount == 0 || !wasLaserPreviouslyVisible) {
                windowStartTime = currentTime;
            }

            // 如果光点从亮到暗再到亮，认为是一次闪烁
            if (!wasLaserPreviouslyVisible) {
                flashCount++;
                Log.d("LaserFlashing", "Laser flash count: " + flashCount);
            } else if (flashCount == 2 && !hasTriggeredClick) {
                Log.d("LaserFlashing", "click");
                triggerClick(mappedX, mappedY);
                hasTriggeredClick = true;
            } else if (flashCount >= 3) {
                // 如果闪烁次数 >= 3，持续收集光点的坐标
                laserCoordinates.add(new int[]{mappedX, mappedY});

                // 当列表中有 2 个或更多点时，将相邻的两个点进行拖曳
                if (laserCoordinates.size() >= 2) {
                    int[] start = laserCoordinates.get(laserCoordinates.size() - 2);
                    int[] end = laserCoordinates.get(laserCoordinates.size() - 1);
                    startDragWithLaser(start[0], start[1], end[0], end[1]);
                }
            }
        } else {
            // 如果光点不可见或超时，结束拖曳并重置
            if (currentTime - windowStartTime >= 1000) {
                resetFlashDetection();
                stopDragging();  // 停止拖曳操作
                laserCoordinates.clear();
                isDraggingInProgress = false;
            }
        }

        // 更新上一次光点的可见状态
        wasLaserPreviouslyVisible = laserDetected;
    }


    // 停止拖曳操作
    private void stopDragging() {
        if (!laserCoordinates.isEmpty()) {
            // 发送拖曳数据到 MyAccessibilityService
            Intent dragIntent = new Intent(context, MyAccessibilityService.class);
            dragIntent.putExtra("action_type", "drag");
            dragIntent.putExtra("coordinates", new ArrayList<>(laserCoordinates));
            context.startService(dragIntent);

            Log.d("LaserFlashing", "Completed drag with coordinates: " + laserCoordinates);
        }

        laserCoordinates.clear();  // 清空坐标列表
        isDraggingInProgress = false;
    }

    // 单次拖曳操作
    private void startDragWithLaser(int startX, int startY, int endX, int endY) {
        Intent dragIntent = new Intent(context, MyAccessibilityService.class);
        dragIntent.putExtra("action_type", "drag_single");
        dragIntent.putExtra("startX", startX);
        dragIntent.putExtra("startY", startY);
        dragIntent.putExtra("endX", endX);
        dragIntent.putExtra("endY", endY);
        context.startService(dragIntent);
    }

    // 重置闪烁检测逻辑
    private void resetFlashDetection() {
        flashCount = 0;
        wasLaserPreviouslyVisible = false;
        hasTriggeredClick = false;
        windowStartTime = 0;
    }






    private void calculateScaleFactors() {
        if(getRotation == 0){
            screenWidth = displayWidth;  // 替换为实际手机屏幕的宽度
            screenHeight = displayHeight; // 替换为实际手机屏幕的高度
        }else{
            screenWidth = displayHeight;  // 替换为实际手机屏幕的宽度
            screenHeight = displayWidth; // 替换为实际手机屏幕的高度
        }


        // 定义一个Mat用于存储投影边框的四个角点
        Mat srcCornersMat = new MatOfPoint2f(savedCorners); // savedCorners 为检测到的四个角点

        // 透视变换后的点（在屏幕上的投影位置）
        Mat dstCornersMat = new MatOfPoint2f();

        // 应用透视变换
        Core.perspectiveTransform(srcCornersMat, dstCornersMat, perspectiveTransform);

        // 获取透视变换后的四个角点
        Point[] transformedCorners = ((MatOfPoint2f) dstCornersMat).toArray();

        // 计算透视变换后的宽度和高度
        double projectionWidth = Math.sqrt(Math.pow(transformedCorners[0].x - transformedCorners[1].x, 2) +
                Math.pow(transformedCorners[0].y - transformedCorners[1].y, 2));  // 左上到右上
        double projectionHeight = Math.sqrt(Math.pow(transformedCorners[0].x - transformedCorners[3].x, 2) +
                Math.pow(transformedCorners[0].y - transformedCorners[3].y, 2)); // 左上到左下

        // 计算比例因子，确保宽高的比例对应正确
        scaleX = (float) (screenWidth / projectionWidth);
        scaleY = (float) (screenHeight / projectionHeight);

        Log.d("calculateScaleFactors", "screenWidth:" + screenWidth + " screenHeight:" + screenHeight
                + "\nprojectionWidth:" + projectionWidth + " projectionHeight:" + projectionHeight
                + "\n==>" + scaleX + " " + scaleY
                + "\ntransformedCorners[0]" + transformedCorners[0] + " transformedCorners[1]" + transformedCorners[1]
                + " transformedCorners[2]" + transformedCorners[2] + " transformedCorners[3]" + transformedCorners[3]);

        // 释放资源
        srcCornersMat.release();
        dstCornersMat.release();
    }






    // 重置闪烁检测逻辑

    private void startDragWithLaser() {
        // 创建 Intent 传递给 MyAccessibilityService
        Intent dragIntent = new Intent(context, MyAccessibilityService.class);
        dragIntent.putExtra("action_type", "drag");
        dragIntent.putExtra("coordinates", laserCoordinates);  // 这里的coordinates是ArrayList<int[]>类型
        context.startService(dragIntent);

        Log.d("LaserFlashing", "Starting drag with coordinates: " + laserCoordinates);

        isDraggingInProgress = true;

        // 重置拖移状态
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            laserCoordinates.clear();
            isDraggingInProgress = false;
        }, 1000);
    }





    // 觸發點擊方法
    private void triggerClick(int x, int y) {
        Intent clickIntent = new Intent(context, MyAccessibilityService.class);
        clickIntent.putExtra("action_type", "click");
        clickIntent.putExtra("x", x);
        clickIntent.putExtra("y", y);
        context.startService(clickIntent);
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

        // 定义HSV范围
        Scalar lowerHSV = new Scalar(55, 75, 235); // 下限
        Scalar upperHSV = new Scalar(90, 100, 255); // 上限

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

            // 获取HSV值
            double[] hsvValues = hsvMat.get(laserY, laserX);
            Log.d(TAG, "Laser Point Detected: " + laserX + ", " + laserY + " - HSV: " + Arrays.toString(hsvValues));

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
    public void inputHSV(float h, float s, float v) {
        // 打印 HSV 值到 Logcat
        this.H = h;
        this.S = s;
        this.V = v;
        Log.d("OpenCVProcessor", "Received HSV Values: H=" + h + ", S=" + s + ", V=" + v);

    }
    private boolean lastLaserDetected = false;
    private int missedFrames = 0;
    private final int MAX_MISSED_FRAMES = 3; // 允许的最大丢帧数

    private final List<Point> pointHistory = new ArrayList<>();
    private final int MAX_HISTORY_SIZE = 5; // 平滑历史坐标的最大数量

    // 平滑坐标的方法
    private Point smoothPoint(Point newPoint) {
        pointHistory.add(newPoint);
        if (pointHistory.size() > MAX_HISTORY_SIZE) {
            pointHistory.remove(0);
        }

        double sumX = 0, sumY = 0;
        for (Point point : pointHistory) {
            sumX += point.x;
            sumY += point.y;
        }

        return new Point(sumX / pointHistory.size(), sumY / pointHistory.size());
    }
}