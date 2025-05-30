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
    private Mat perspectiveTransform;
    private Point[] savedCorners = null;
    int getRotation;
    // 假设手机屏幕的宽高
    int screenWidth;
    int screenHeight;
    boolean wasLaserPreviouslyVisible = false;
    long windowStartTime = 0;
    boolean hasTriggeredClick = false;
    private ArrayList<int[]> laserCoordinates = new ArrayList<>();
    float H=70, S=90, V=245;
    private static final int HSV_SAMPLE_SIZE = 50; // HSV采样的最大数量
    private final Queue<double[]> hsvSamples = new LinkedList<>(); // 存储最近的HSV值
    private int[] lastClickPoint = null;
    int isKeepDrag = 0;
    private long lastLaserTime = 0; // 记录最后一次检测到雷射笔的时间戳
    private static final long LASER_TIMEOUT = 400; // 1秒超时时间（单位：毫秒）
    int temp=0;
    private boolean hasFlashedOnce = false; // 是否在這輪中亮過
    private boolean allowDrag = false;



    public interface PointListener {
        void onPointDetected(int x, int y);
    }
    public void onButtonClicked() {
        Log.d(TAG, "isKeepDrag=: " + isKeepDrag);
        isKeepDrag ++;
        if(isKeepDrag > 2){
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
            case Surface.ROTATION_0:    // 新增正方向處理
                getRotation = rotation;
                break;
            case Surface.ROTATION_90:   // 左旋90度
                Core.rotate(frame, frame, Core.ROTATE_90_COUNTERCLOCKWISE);
                getRotation = rotation;
                break;
            case Surface.ROTATION_270:  // 右旋90度
                Core.rotate(frame, frame, Core.ROTATE_90_CLOCKWISE);
                getRotation = rotation;
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

    private Scalar lowerHSV = new Scalar(55, 75, 235); // 預設值
    private Scalar upperHSV = new Scalar(90, 100, 255); // 預設值
    public int[] getCurrentMostFrequentHSV() {
        return calculateMostFrequentHSV(hsvSamples);
    }

    // 設定新的 HSV 閾值
    public void setLaserHSV(int h, int s, int v) {
        // 只處理有效值
        if (h > 0 && s > 0 && v > 0) {
            this.H = h;
            this.S = s;
            this.V = v;

            // 計算動態範圍
            int hRange = Math.max(10, h / 10);  // 至少10或H的10%
            int sRange = Math.max(20, s / 5);   // 至少20或S的20%
            int vRange = Math.max(20, v / 5);   // 至少20或V的20%

            // 設置下限和上限，確保不超出HSV合法範圍
            this.lowerHSV = new Scalar(Math.max(0, h - hRange),
                    Math.max(0, s - sRange),
                    Math.max(0, v - vRange));

            this.upperHSV = new Scalar(Math.min(180, h + hRange),
                    Math.min(255, s + sRange),
                    Math.min(255, v + vRange));

            Log.d(TAG, "Set Laser HSV Center: H=" + h + ", S=" + s + ", V=" + v);
            Log.d(TAG, "Set Laser HSV Range: lower=" + lowerHSV + ", upper=" + upperHSV);
        }
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
        Imgproc.Canny(binaryMat, edges, 150, 250);

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
            Point[] orderedCorners = sortCorners(corners);
            drawProjectionFrame(rgbaMat, orderedCorners);
            savedCorners = orderedCorners;

            // 交換 index 0 和 1
            Point temp = savedCorners[0];
            savedCorners[0] = savedCorners[1];
            savedCorners[1] = temp;

            // 交換 index 2 和 3
            temp = savedCorners[2];
            savedCorners[2] = savedCorners[3];
            savedCorners[3] = temp;

            logCorners("Detected Corners", savedCorners);

            // 定義手機螢幕的四個角
            Point[] dstCorners = new Point[4];
            dstCorners[0] = new Point(0, 0);                        // 左上角
            dstCorners[1] = new Point(rgbaMat.cols(), 0);           // 右上角
            dstCorners[2] = new Point(rgbaMat.cols(), rgbaMat.rows()); // 右下角
            dstCorners[3] = new Point(0, rgbaMat.rows());           // 左下角

            logCorners("Destination Corners", dstCorners);

            // 透視變換矩陣
            MatOfPoint2f srcMat = new MatOfPoint2f(savedCorners); // 注意這裡要用 savedCorners
            MatOfPoint2f dstMat = new MatOfPoint2f(dstCorners);
            perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat);

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

    // 依照 x+y 最小的是左上，x-y 最小的是右上，x+y 最大的是右下，x-y 最大的是左下
    private Point[] sortCorners(Point[] corners) {
        Point[] ordered = new Point[4];

        double sum0 = corners[0].x + corners[0].y;
        double sum1 = corners[1].x + corners[1].y;
        double sum2 = corners[2].x + corners[2].y;
        double sum3 = corners[3].x + corners[3].y;

        double diff0 = corners[0].x - corners[0].y;
        double diff1 = corners[1].x - corners[1].y;
        double diff2 = corners[2].x - corners[2].y;
        double diff3 = corners[3].x - corners[3].y;

        // 左上：x+y 最小
        int idx0 = 0, idx1 = 0, idx2 = 0, idx3 = 0;
        double minSum = sum0;
        double maxSum = sum0;
        for (int i = 1; i < 4; i++) {
            double s = corners[i].x + corners[i].y;
            if (s < minSum) {
                minSum = s;
                idx0 = i;
            }
            if (s > maxSum) {
                maxSum = s;
                idx2 = i;
            }
        }
        // 右上：x-y 最小
        double minDiff = diff0;
        for (int i = 1; i < 4; i++) {
            double d = corners[i].x - corners[i].y;
            if (d < minDiff) {
                minDiff = d;
                idx1 = i;
            }
        }
        // 左下：x-y 最大
        double maxDiff = diff0;
        for (int i = 1; i < 4; i++) {
            double d = corners[i].x - corners[i].y;
            if (d > maxDiff) {
                maxDiff = d;
                idx3 = i;
            }
        }

        ordered[0] = corners[idx0]; // 左上
        ordered[1] = corners[idx1]; // 右上
        ordered[2] = corners[idx2]; // 右下
        ordered[3] = corners[idx3]; // 左下

        return ordered;
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
        // 高斯模糊降噪（保持不變）
        Imgproc.GaussianBlur(grayMat, grayMat, new Size(5, 5), 0);

        // 轉換到 HSV 色彩空間
        Mat hsvMat = new Mat();
        Imgproc.cvtColor(rgbaMat, hsvMat, Imgproc.COLOR_RGB2HSV);

        // 調整綠色雷射筆的HSV範圍（關鍵修改）
        Scalar lowerGreen = new Scalar(50, 40, 180);   // 下限（色相,飽和度,明度）
        Scalar upperGreen = new Scalar(90, 255, 255);  // 上限（放寬飽和度和明度）

        // 生成HSV遮罩
        Mat greenMask = new Mat();
        Core.inRange(hsvMat, lowerGreen, upperGreen, greenMask);

        // 加入亮度通道強化（V通道單獨處理）
        List<Mat> hsvChannels = new ArrayList<>();
        Core.split(hsvMat, hsvChannels);
        Mat vChannel = hsvChannels.get(2);
        Mat brightMask = new Mat();
        Imgproc.threshold(vChannel, brightMask, 220, 255, Imgproc.THRESH_BINARY); // 降低亮度阈值

        // 綜合遮罩（結合HSV和亮度）
        Mat combinedMask = new Mat();
        Core.bitwise_and(greenMask, brightMask, combinedMask);

        // 形態學操作（調整膨脹核大小）
        Mat dilatedMask = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(25, 25));  // 增大核尺寸
        Imgproc.dilate(combinedMask, dilatedMask, kernel);

        // 找輪廓（降低最小面積限制）
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(dilatedMask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // 找最大光點
        Rect boundingRect = null;
        double minArea = 2.0;  // 允許更小的光點
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            if (rect.area() >= minArea && (boundingRect == null || rect.area() > boundingRect.area())) {
                boundingRect = rect;
            }
        }

        boolean laserDetected = false;
        int scaledX = 0, scaledY = 0;

        if (boundingRect != null && perspectiveTransform != null) {
            // 計算光點中心（加入邊界檢查）
            int laserX = Math.max(0, Math.min(boundingRect.x + boundingRect.width/2, hsvMat.cols()-1));
            int laserY = Math.max(0, Math.min(boundingRect.y + boundingRect.height/2, hsvMat.rows()-1));

            // 透視變換
            MatOfPoint2f originalPoint = new MatOfPoint2f(new Point(laserX, laserY));
            MatOfPoint2f transformedPoint = new MatOfPoint2f();
            Core.perspectiveTransform(originalPoint, transformedPoint, perspectiveTransform);

            // 座標縮放與邊界限制
            Point correctedPoint = transformedPoint.toArray()[0];
            scaledX = (int) (correctedPoint.x * scaleX);
            scaledY = (int) (correctedPoint.y * scaleY);
            scaledX = Math.max(0, Math.min(scaledX, screenWidth-1));
            scaledY = Math.max(0, Math.min(scaledY, screenHeight-1));

            laserDetected = true;
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
                    }else if (isKeepDrag == 2 && allowDrag) {  // 移除 laserCoordinates.size()>20 判断
                        // 新增距离检查 (避免微小移动误触)
                        if (laserCoordinates.size() >= 2) {
                            int[] first = laserCoordinates.get(0);
                            int[] last = laserCoordinates.get(laserCoordinates.size() - 1);
                            double distance = calculateDistance(first[0], first[1], last[0], last[1]);

                            if (laserCoordinates.size() % 10 == 0) { // 每10個點發送一次
                                ArrayList<int[]> batch = new ArrayList<>(laserCoordinates.subList(
                                        Math.max(0, laserCoordinates.size()-10),
                                        laserCoordinates.size()
                                ));

                                Intent partialDrag = new Intent(context, MyAccessibilityService.class);
                                partialDrag.putExtra("action_type", "drag");
                                partialDrag.putExtra("coordinates", batch);
                                context.startService(partialDrag);
                            }
                        }
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
//        binaryMat.release();
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
        Log.d(TAG, "detectHSVPoints lowerHSV, upperHSV: "+ lowerHSV + upperHSV);

        Mat mask = new Mat();
        Core.inRange(hsvMat, lowerHSV, upperHSV, mask);

        Mat dilatedMask = new Mat();
        Imgproc.dilate(mask, dilatedMask, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(21, 21)));

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(dilatedMask, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        Rect boundingRect = null;
        double minArea = 5.0; // 降低閾值
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            if (rect.area() >= minArea && (boundingRect == null || rect.area() > boundingRect.area())) {
                boundingRect = rect;
            }
        }

        if (boundingRect != null) {
            // 計算範圍內所有點的平均HSV (而不是只取中心點)
            int count = 0;
            double sumH = 0, sumS = 0, sumV = 0;

            // 遍歷整個邊界矩形區域的所有像素
            for (int y = boundingRect.y; y < boundingRect.y + boundingRect.height; y++) {
                for (int x = boundingRect.x; x < boundingRect.x + boundingRect.width; x++) {
                    if (y >= 0 && y < hsvMat.rows() && x >= 0 && x < hsvMat.cols()) {
                        double[] hsv = hsvMat.get(y, x);
                        if (hsv != null && hsv.length == 3 && hsv[0] > 0 && hsv[1] > 0 && hsv[2] > 0) {
                            sumH += hsv[0];
                            sumS += hsv[1];
                            sumV += hsv[2];
                            count++;
                        }
                    }
                }
            }

            if (count > 0) {
                // 計算平均值
                double avgH = sumH / count;
                double avgS = sumS / count;
                double avgV = sumV / count;

                // 只有當值有效時才添加到樣本中
                if (avgH > 0 && avgS > 0 && avgV > 0) {
                    double[] hsvValues = new double[]{avgH, avgS, avgV};
                    Log.d(TAG, "Laser Point Detected - Avg HSV: " + Arrays.toString(hsvValues));

                    if (hsvSamples.size() >= HSV_SAMPLE_SIZE) {
                        hsvSamples.poll(); // 移除最舊的值
                    }
                    hsvSamples.add(hsvValues);

                    // 計算最常見的HSV值 (這裡已經自動調用 setLaserHSV)
                    int[] mostFrequentHSV = calculateMostFrequentHSV(hsvSamples);
                    setLaserHSV(mostFrequentHSV[0], mostFrequentHSV[1], mostFrequentHSV[2]);
                    Log.d(TAG, "Updated HSV: H=" + mostFrequentHSV[0] + ", S=" + mostFrequentHSV[1] + ", V=" + mostFrequentHSV[2]);
                }
            }

            // 繪製光點
            Imgproc.circle(rgbaMat, new Point(boundingRect.x + boundingRect.width/2, boundingRect.y + boundingRect.height/2),
                    10, new Scalar(0, 255, 0), 3);

            if (pointListener != null) {
                pointListener.onPointDetected(boundingRect.x + boundingRect.width/2, boundingRect.y + boundingRect.height/2);
            }
        } else {
            Log.d(TAG, "No laser point detected");
        }

        // 釋放資源
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

        // 用於計算平均值的變數
        double sumH = 0, sumS = 0, sumV = 0;
        int countH = 0, countS = 0, countV = 0;

        for (double[] hsv : hsvSamples) {
            int h = (int) hsv[0];
            int s = (int) hsv[1];
            int v = (int) hsv[2];

            // 排除無效值 (值為0)
            if (h > 0) {
                hFrequency.put(h, hFrequency.getOrDefault(h, 0) + 1);
                sumH += h;
                countH++;
            }
            if (s > 0) {
                sFrequency.put(s, sFrequency.getOrDefault(s, 0) + 1);
                sumS += s;
                countS++;
            }
            if (v > 0) {
                vFrequency.put(v, vFrequency.getOrDefault(v, 0) + 1);
                sumV += v;
                countV++;
            }
        }

        // 計算平均值 (當無法獲取眾數時使用)
        int avgH = countH > 0 ? (int)(sumH / countH) : 70; // 默認值
        int avgS = countS > 0 ? (int)(sumS / countS) : 90; // 默認值
        int avgV = countV > 0 ? (int)(sumV / countV) : 245; // 默認值

        // 返回每個通道的眾數，如果沒有有效頻率資料，則使用平均值
        return new int[]{
                getMostFrequentValue(hFrequency, avgH),
                getMostFrequentValue(sFrequency, avgS),
                getMostFrequentValue(vFrequency, avgV)
        };
    }

    // 獲取頻率最高的值，如果沒有頻率資料則返回默認值
    private int getMostFrequentValue(Map<Integer, Integer> frequencyMap, int defaultValue) {
        if (frequencyMap.isEmpty()) {
            return defaultValue;
        }
        return frequencyMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(defaultValue);
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
    // 修改 smoothPoint 方法，給近期座標更高權重
    private Point smoothPoint(Point newPoint) {
        pointHistory.add(newPoint.clone());
        if (pointHistory.size() > MAX_HISTORY_SIZE) {
            pointHistory.remove(0);
        }

        double sumX = 0, sumY = 0;
        double weightSum = 0;
        for (int i = 0; i < pointHistory.size(); i++) {
            double weight = (i + 1) * 0.5; // 越新的點權重越高
            sumX += pointHistory.get(i).x * weight;
            sumY += pointHistory.get(i).y * weight;
            weightSum += weight;
        }
        return new Point(sumX / weightSum, sumY / weightSum);
    }

}