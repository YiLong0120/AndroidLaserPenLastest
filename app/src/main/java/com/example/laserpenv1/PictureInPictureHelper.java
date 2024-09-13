//package com.example.laserpenv1;
//
//import android.app.Activity;
//import android.app.PictureInPictureParams;
//import android.util.DisplayMetrics;
//import android.util.Rational;
//import android.widget.Toast;
//
//import org.opencv.android.CameraBridgeViewBase;
//
//public class PictureInPictureHelper {
//    private final Activity activity;
//    private final CameraBridgeViewBase cameraView;
//    private final OpenCVProcessor openCVProcessor;
//
//    public PictureInPictureHelper(Activity activity, CameraBridgeViewBase cameraView, OpenCVProcessor openCVProcessor) {
//        this.activity = activity;
//        this.cameraView = cameraView;
//        this.openCVProcessor = openCVProcessor;
//    }
//
//    public void enterPipMode() {
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//            Rational aspectRatio = new Rational(cameraView.getWidth(), cameraView.getHeight());
//            PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();
//            pipBuilder.setAspectRatio(aspectRatio);
//
//            DisplayMetrics displayMetrics = new DisplayMetrics();
//            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
//
//            float scaleX = (float) cameraView.getWidth() / (float) displayMetrics.widthPixels;
//            float scaleY = (float) cameraView.getHeight() / (float) displayMetrics.heightPixels;
//
//            openCVProcessor.setScaleFactors(scaleX, scaleY);
//
//            activity.enterPictureInPictureMode(pipBuilder.build());
//        } else {
//            Toast.makeText(activity, "Picture-in-Picture mode is not supported on this device", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
//        if (isInPictureInPictureMode) {
//            if (!cameraView.isEnabled()) {
//                cameraView.enableView();
//            }
//        } else {
//            if (cameraView != null && !cameraView.isEnabled()) {
//                cameraView.enableView();
//            }
//        }
//    }
//}
