/*
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

        switch (rotation) {
            case Surface.ROTATION_90: // 屏幕左旋90度
                Core.rotate(frame, frame, Core.ROTATE_90_COUNTERCLOCKWISE);
                break;
            case Surface.ROTATION_180: // 屏幕倒转180度
                Core.rotate(frame, frame, Core.ROTATE_180);
                break;
            case Surface.ROTATION_270: // 屏幕右旋90度
                Core.rotate(frame, frame, Core.ROTATE_90_CLOCKWISE);
                break;
        }

        // 继续图像处理流程
        if (!isFrameLocked) {
            // 转换为灰阶
            Mat grayMat = new Mat();
            Imgproc.cvtColor(frame, grayMat, Imgproc.COLOR_RGBA2GRAY);

            // 背景减除与二值化处理
            detectProjectionScreen(frame, grayMat);
        } else {
            detectLaserPoints(frame);
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

            // 在每个角落绘制数字0, 1, 2, 3
            for (int i = 0; i < corners.length; i++) {
                Imgproc.circle(rgbaMat, corners[i], 10, new Scalar(0, 0, 255), -1); // 在每个角上画红色圆点
                Imgproc.putText(rgbaMat, String.valueOf(i), new Point(corners[i].x + 10, corners[i].y - 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 0, 0), 1); // 绘制数字
            }

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

        Scalar lowerGreen = new Scalar(55, 75, 235); // 下限
        Scalar upperGreen = new Scalar(75, 95, 255); // 上限

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

        if (boundingRect != null && perspectiveTransform != null) {
            // 激光笔光点的原始坐标
            int laserX = boundingRect.x + boundingRect.width / 2;
            int laserY = boundingRect.y + boundingRect.height / 2;

            // 应用透视变换来校正光点坐标
            MatOfPoint2f originalPoint = new MatOfPoint2f(new Point(laserX, laserY));
            MatOfPoint2f transformedPoint = new MatOfPoint2f();
            Core.perspectiveTransform(originalPoint, transformedPoint, perspectiveTransform);

            // 提取校正后的光点坐标
            Point correctedPoint = transformedPoint.toArray()[0];
            int correctedX = (int) correctedPoint.x;
            int correctedY = (int) correctedPoint.y;

            // 应用比例因子进行缩放
            int scaledX = (int) (correctedX * scaleX); // 交换坐标
            int scaledY = (int) (correctedY * scaleY); // 交换坐标并取反

            // 输出调试信息
            Log.d("Movement", "Original: (" + laserX + ", " + laserY + "), Corrected: (" + correctedX + ", " + correctedY + "), Scaled: (" + scaledX + ", " + scaledY + ")");

            // 显示校正后的光点
            Imgproc.circle(rgbaMat, new Point(laserX, laserY), 10, new Scalar(0, 255, 0), 3);  // 显示原始光点
            Imgproc.circle(rgbaMat, new Point(scaledX, scaledY), 10, new Scalar(255, 0, 0), 3);  // 显示校正后的光点

            // 处理闪烁和拖移
            processLaserFlashing(scaledX, scaledY);

            // 传送鼠标移动指令
            Intent moveIntent = new Intent(context, MouseAccessibilityService.class);
            moveIntent.putExtra("x", scaledX);
            moveIntent.putExtra("y", scaledY);
            context.startService(moveIntent);

            if (pointListener != null) {
                pointListener.onPointDetected(scaledX, scaledY);
            }
        }

        // 释放资源
        hsv.release();
        greenMask.release();
        dilatedMask.release();
        hierarchy.release();
    }



    private void calculateScaleFactors() {
        // 假设手机屏幕的宽高
        int screenWidth = displayWidth;  // 替换为实际手机屏幕的宽度
        int screenHeight = displayHeight; // 替换为实际手机屏幕的高度

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




    private void processLaserFlashing(int mappedX, int mappedY) {
        // 如果正在进行拖移操作，则不再捕获新的拖移动作
        if (isDraggingInProgress) {
            return;
        }

        // 将当前的激光笔坐标添加到列表中
        if (mappedX>0 || mappedY>0) {
            laserPoints.add(new Point(mappedX, mappedY));
        }


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
}
