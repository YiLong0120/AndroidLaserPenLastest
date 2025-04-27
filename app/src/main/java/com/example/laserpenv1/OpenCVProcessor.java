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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

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
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private Mat frame; // 用來保存當前相機幀
    private static final String TAG = "OpenCVProcessor";
    private int flashCount = 0;
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
    private static final int HSV_SAMPLE_SIZE = 50; // HSV采样的最大数量
    private final Queue<double[]> hsvSamples = new LinkedList<>(); // 存储最近的HSV值
    // 用于记录光点“亮”和“暗”的时间
    private long brightnessStartTime = 0;
    private long darknessStartTime = 0;

    // 用于判断光点是否持续“亮”达到指定时间
    private boolean isBrightEnough = false;
    private int[] lastClickPoint = null;
    int isKeepDrag = 0;
    private long lastLaserTime = 0; // 记录最后一次检测到雷射笔的时间戳
    private static final long LASER_TIMEOUT = 400; // 1秒超时时间（单位：毫秒）
    int temp=0;
    int firstflashcount = 0;
    private boolean hasFlashedOnce = false; // 是否在這輪中亮過
    private boolean allowDrag = false;



    public interface PointListener {
        void onPointDetected(int x, int y);
    }
    public void onButtonClicked() {
        Log.d(TAG, "isKeepDrag=: " + isKeepDrag);
        isKeepDrag ++;
        if(isKeepDrag > 1){
            isKeepDrag = 0;
        }
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
        // 对图像进行高斯模糊，减少噪声
        Imgproc.GaussianBlur(grayMat, grayMat, new Size(5, 5), 0);

        // 调整阈值，适应白色区域
        Imgproc.threshold(grayMat, binaryMat, 150, 255, Imgproc.THRESH_BINARY);

        // 使用Canny边缘检测增强边缘特征
        Mat edges = new Mat();
        Imgproc.Canny(binaryMat, edges, 100, 200);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        // 查找轮廓
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

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
        edges.release();
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
//        // 在每个角落绘制数字0, 1, 2, 3
//        for (int i = 0; i < corners.length; i++) {
//            Imgproc.circle(rgbaMat, corners[i], 10, new Scalar(0, 0, 255), -1); // 在每个角上画红色圆点
//            Imgproc.putText(rgbaMat, String.valueOf(i), new Point(corners[i].x + 10, corners[i].y - 10),
//                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 0, 0), 1); // 绘制数字
//        }
    }


    public void toggleFrameLock() {
        isFrameLocked = !isFrameLocked;
        if (isFrameLocked) {
            mainHandler.post(() -> Toast.makeText(context, "Frame locked", Toast.LENGTH_SHORT).show());
        } else {
            mainHandler.post(() -> Toast.makeText(context, "Frame unlocked", Toast.LENGTH_SHORT).show());
        }
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
        Scalar lowerGreen = new Scalar(70, 80, 240);
        Scalar upperGreen = new Scalar(90, 100, 255);
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

        // 声明全局变量

        if (laserDetected) {
            lastLaserTime = System.currentTimeMillis();  // 更新最後亮的時間

            if (!hasFlashedOnce) {
                // 第一次亮：只移動游標，不拖曳
                hasFlashedOnce = true;
                Log.d(TAG, "First laser detected - move cursor only");

                Point smoothedPoint = smoothPoint(new Point(scaledX, scaledY));
                Imgproc.circle(rgbaMat, smoothedPoint, 10, new Scalar(255, 0, 0), 3);

                showMouse((int) smoothedPoint.x, (int) smoothedPoint.y);

                temp++;
                if (temp == 1) {
                    pointHistory.clear();
                }

                lastClickPoint = new int[]{(int) smoothedPoint.x, (int) smoothedPoint.y};

            } else {

                // 第二次或之後亮起，依照是否允許拖曳
                Point smoothedPoint = smoothPoint(new Point(scaledX, scaledY));
                Imgproc.circle(rgbaMat, smoothedPoint, 10, new Scalar(255, 0, 0), 3);

                showMouse((int) smoothedPoint.x, (int) smoothedPoint.y);

                int[] currentPoint = new int[]{(int) smoothedPoint.x, (int) smoothedPoint.y};
                laserCoordinates.add(currentPoint);

                if (laserCoordinates.size() >= 2) {
                    int[] start = laserCoordinates.get(laserCoordinates.size() - 2);
                    int[] end = laserCoordinates.get(laserCoordinates.size() - 1);
                    Log.d(TAG, "drag?: " + allowDrag);
                    if (isKeepDrag == 1 && allowDrag) {

                        // 只有經過 >1秒熄滅後，再亮起來才開始拖曳
                        singleDrag(start[0], start[1], end[0], end[1]);
                        Log.d(TAG, "Dragging from (" + start[0] + "," + start[1] + ") to (" + end[0] + "," + end[1] + ")");
                    }
                }

                lastClickPoint = currentPoint;
            }

        } else {
            long timeSinceLastLaser = System.currentTimeMillis() - lastLaserTime;

            if (timeSinceLastLaser > 200 && hasFlashedOnce) {
                // 熄滅超過1秒，允許之後拖曳
                allowDrag = true;
                hasFlashedOnce = false;
                Log.d(TAG, "Laser off > 1s, ready for drag");


                if (isKeepDrag == 0 && lastClickPoint != null ) {
                    triggerClick(lastClickPoint[0], lastClickPoint[1]);
                    Log.d(TAG, "Click triggered after laser off at: (" + lastClickPoint[0] + ", " + lastClickPoint[1] + ")");
                }

            }


            if (timeSinceLastLaser > LASER_TIMEOUT) {
                temp = 0;
                laserCoordinates.clear();
                allowDrag = false; // 超時也要清除
                Log.d(TAG, "Laser timeout, data cleared");
            }
        }









        // 添加 Logcat 提示
        if (laserDetected) {
            Log.d("LaserDetection", "Laser point detected at: (" + scaledX + ", " + scaledY + ")");
        } else {
            Log.d("LaserDetection", "No laser point detected.");
        }

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
//    private void processLaserFlashing(int mappedX, int mappedY, boolean laserDetected) {
//        long currentTime = System.currentTimeMillis();
//        Log.d("LaserFlashing", "Laser flash count: " + flashCount);
//
//        // 定义参数
//        long minBrightnessDuration = 100; // 光点“亮”的最短持续时间（单位：毫秒）
//        long maxDarknessDuration = 200;  // 光点“暗”的最大持续时间（单位：毫秒）
//        long clickThresholdDistance = 50; // 点击的最大移动距离（单位：像素）
//        long dragThresholdDistance = 50; // 拖曳的距离阈值（单位：像素）
//
//        // 光点检测
//        if (laserDetected) {
//            // 如果是从暗到亮，记录亮的开始时间
//            if (!wasLaserPreviouslyVisible) {
//                brightnessStartTime = currentTime;
//                lastClickPoint = new int[]{mappedX, mappedY}; // 初始化点击坐标
//            }
//
//            Log.d("LaserFlashing", "Time since bright start: " + (currentTime - brightnessStartTime));
//
//            // 检测“亮”的时间是否超过指定时长
//            if (currentTime - brightnessStartTime >= minBrightnessDuration) {
//                // 判断光点是否稳定在一个位置
//                if (lastClickPoint != null) {
//                    double clickDistance = calculateDistance(lastClickPoint[0], lastClickPoint[1], mappedX, mappedY);
//                    if (clickDistance <= clickThresholdDistance) {
//                        // 光点稳定 -> 触发点击
//                        triggerClick(mappedX, mappedY);
//                        Log.d("LaserFlashing", "Click triggered at: (" + mappedX + ", " + mappedY + ")");
//                    } else {
//                        Log.d("LaserFlashing", "Click canceled due to movement.");
//                    }
//                }
//            }
//
//            laserCoordinates.add(new int[]{mappedX, mappedY});
//            if (laserCoordinates.size() >= 2) {
//                int[] start = laserCoordinates.get(laserCoordinates.size() - 2);
//                int[] end = laserCoordinates.get(laserCoordinates.size() - 1);
//                if(isKeepDrag == 0){
//                    triggerClick(mappedX, mappedY);
//                }
//                else if(isKeepDrag == 1){
////                    keepDrag();
//                    singleDrag(start[0], start[1], end[0], end[1]);
//                    Log.d(TAG, "isKeepDrag1: ");
//                }
//                else if (isKeepDrag == 2) {
//                    double dragDistance = calculateDistance(start[0], start[1], end[0], end[1]);
//                    Log.d("check", String.valueOf(dragDistance));
//
//                    if (dragDistance > dragThresholdDistance) {
//                        // 計算拖曳方向
//                        String dragDirection = calculateDragDirection(start, end);
//                        Log.d("dragDirection", "Detected direction: " + dragDirection);
//
//                        // 根據方向執行拖曳
//                        performDirectionalDrag(dragDirection);
//                    }
//                }
//
//            }
//        }
//    }

    private String calculateDragDirection(int[] start, int[] end) {
        int deltaX = end[0] - start[0];
        int deltaY = end[1] - start[1];

        // 判斷方向
        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            return deltaX > 0 ? "RIGHT" : "LEFT"; // 水平拖曳
        } else {
            return deltaY > 0 ? "DOWN" : "UP"; // 垂直拖曳
        }
    }

    private void performDirectionalDrag(String direction) {
        int centerX = screenWidth / 2; // 螢幕正中間的 X 座標
        int centerY = screenHeight / 2; // 螢幕正中間的 Y 座標
        int t = 500;
        switch (direction) {
            case "RIGHT":
                singleDrag(centerX - t, centerY, centerX + t, centerY); // 從左至右
                Log.d("performDirectionalDrag", "Dragging RIGHT");
                break;
            case "LEFT":
                singleDrag(centerX + t, centerY, centerX - t, centerY); // 從右至左
                Log.d("performDirectionalDrag", "Dragging LEFT");
                break;
            case "UP":
                singleDrag(centerX, centerY + t, centerX, centerY - t); // 從下至上
                Log.d("performDirectionalDrag", "Dragging UP");
                break;
            case "DOWN":
                singleDrag(centerX, centerY - t, centerX, centerY + t); // 從上至下
                Log.d("performDirectionalDrag", "Dragging DOWN");
                break;
        }
    }



    private double calculateDistance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }


    // 单次拖曳操作
    private void singleDrag(int startX, int startY, int endX, int endY) {
        Intent dragIntent = new Intent(context, MyAccessibilityService.class);
        dragIntent.putExtra("action_type", "drag_single");
        dragIntent.putExtra("startX", startX);
        dragIntent.putExtra("startY", startY);
        dragIntent.putExtra("endX", endX);
        dragIntent.putExtra("endY", endY);
        context.startService(dragIntent);
    }

    // 觸發點擊方法
    private void triggerClick(int x, int y) {
        Intent clickIntent = new Intent(context, MyAccessibilityService.class);
        clickIntent.putExtra("action_type", "click");
        clickIntent.putExtra("x", x);
        clickIntent.putExtra("y", y);
        context.startService(clickIntent);
    }

    // 重置闪烁检测逻辑
    private void resetFlashDetection() {
        flashCount = 0;
        wasLaserPreviouslyVisible = false;
        hasTriggeredClick = false;
        windowStartTime = 0;
    }






    private void calculateScaleFactors() {
        Log.d(TAG, "calculateScaleFactors: " + getRotation);
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
            if (hsvValues != null && hsvValues.length == 3) {
                Log.d(TAG, "Laser Point Detected: " + laserX + ", " + laserY + " - HSV: " + Arrays.toString(hsvValues));

                // 将当前HSV值添加到队列
                if (hsvSamples.size() >= HSV_SAMPLE_SIZE) {
                    hsvSamples.poll(); // 移除最旧的值
                }
                hsvSamples.add(hsvValues);

                // 计算每个通道的最常出现值
                int[] mostFrequentHSV = calculateMostFrequentHSV(hsvSamples);
                Log.d(TAG, "Most Frequent HSV: H=" + mostFrequentHSV[0] + ", S=" + mostFrequentHSV[1] + ", V=" + mostFrequentHSV[2]);
            }

            // 绘制光点
            Imgproc.circle(rgbaMat, new Point(laserX, laserY), 10, new Scalar(0, 255, 0), 3);

            if (pointListener != null) {
                pointListener.onPointDetected(laserX, laserY);
            }
        } else {
            Log.d(TAG, "No laser point detected");
        }

        // 释放资源
        hsvMat.release();
        mask.release();
        dilatedMask.release();
        hierarchy.release();
    }

    // 计算HSV值中最常出现的H, S, V
    private int[] calculateMostFrequentHSV(Queue<double[]> hsvSamples) {
        Map<Integer, Integer> hFrequency = new HashMap<>();
        Map<Integer, Integer> sFrequency = new HashMap<>();
        Map<Integer, Integer> vFrequency = new HashMap<>();

        for (double[] hsv : hsvSamples) {
            int h = (int) hsv[0];
            int s = (int) hsv[1];
            int v = (int) hsv[2];

            hFrequency.put(h, hFrequency.getOrDefault(h, 0) + 1);
            sFrequency.put(s, sFrequency.getOrDefault(s, 0) + 1);
            vFrequency.put(v, vFrequency.getOrDefault(v, 0) + 1);
        }

        return new int[]{
                getMostFrequentValue(hFrequency),
                getMostFrequentValue(sFrequency),
                getMostFrequentValue(vFrequency)
        };
    }

    // 获取Map中频率最高的值
    private int getMostFrequentValue(Map<Integer, Integer> frequencyMap) {
        return frequencyMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
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

//    private void processLaserFlashing(int mappedX, int mappedY, boolean laserDetected) {
//        long currentTime = System.currentTimeMillis();
//
//        // 初始化
//        if (laserDetected) {
//            if (flashCount == 0 || !wasLaserPreviouslyVisible) {
//                windowStartTime = currentTime;
//                initialCoordinate = new int[]{mappedX, mappedY}; // 記錄起點
//            }
//
//            // 如果光點從暗到亮，視為一次閃爍
//            if (!wasLaserPreviouslyVisible) {
//                flashCount++;
//                triggerClick(mappedX, mappedY);
//                Log.d("LaserFlashing", "Laser flash count: " + flashCount);
////            } else if (flashCount >= 3) {
////                // 如果闪烁次数 >= 3，持续收集光点的坐标
////                laserCoordinates.add(new int[]{mappedX, mappedY});
////
////                // 当列表中有 2 个或更多点时，将相邻的两个点进行拖曳
////                if (laserCoordinates.size() >= 2) {
////                    int[] start = laserCoordinates.get(laserCoordinates.size() - 2);
////                    int[] end = laserCoordinates.get(laserCoordinates.size() - 1);
////                    startDragWithLaser(start[0], start[1], end[0], end[1]);
////                }
//            }
//
//            // 收集最新的光點座標
//            laserCoordinates.add(new int[]{mappedX, mappedY});
//
//            // 如果超過一秒，計算距離並判斷是否進行拖曳
//            if (currentTime - windowStartTime >= 1000) {
//                if (laserCoordinates.size() > 1) {
//                    // 記錄終點
//                    int[] finalCoordinate = new int[]{mappedX, mappedY};
//
//                    // 計算移動距離
//                    double totalDistance = calculateTotalDistance(laserCoordinates);
//
//                    // 判斷距離是否超過閾值（例如 50 像素）
//                    if (totalDistance >= 50) {
//                        Log.d("LaserFlashing", "Drag initiated with distance: " + totalDistance);
//                        startDragWithLaser(
//                                initialCoordinate[0], initialCoordinate[1],
//                                finalCoordinate[0], finalCoordinate[1]
//                        );
//                    }
//                }
//
//                // 重置偵測
//                resetFlashDetection();
//                laserCoordinates.clear();
//                isDraggingInProgress = false;
//            }
//        } else {
//            // 如果光點不可見或超時，重置
//            if (currentTime - windowStartTime >= 1000) {
//                resetFlashDetection();
//                stopDragging();  // 停止拖曳操作
//                laserCoordinates.clear();
//                isDraggingInProgress = false;
//            }
//        }
//
//        // 更新上一次光點的可見狀態
//        wasLaserPreviouslyVisible = laserDetected;
//    }