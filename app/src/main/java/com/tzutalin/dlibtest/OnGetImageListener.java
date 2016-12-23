/*
 * Copyright 2016 Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
* Eye Cropping
* 1. Directly detect landmarks out of the original frame, draw it on the floating window
* 2. Locate the positions of eye area
* 3. Create two more floating camera windows to display each eye individually
* */


package com.tzutalin.dlibtest;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.Trace;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.PeopleDet;
import com.tzutalin.dlib.VisionDetRet;

import junit.framework.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import Jama.*;

import static com.tzutalin.dlibtest.CalibrationActivity.mCalibrationPos;
import static com.tzutalin.dlibtest.CalibrationActivity.mLeftCalibrationGrayScale;
import static com.tzutalin.dlibtest.CalibrationActivity.mRightCalibrationGrayScale;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    private static final int NUM_CLASSES = 1001;
    private static final int INPUT_SIZE = 500;
    private static final int IMAGE_MEAN = 117;
    private static final String TAG = "OnGetImageListener";
    public static final int EYE_WIDTH = 50;
    public static final int EYE_HEIGHT = 20;
    public static final double EYE_RATIO = (double) EYE_HEIGHT/EYE_WIDTH;

    private int mScreenRotation = 90;

    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private int mScreenWidth = 0;
    private int mScreentHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;
    private Bitmap mLeftBitmap = null;
    private Bitmap mRightBitmap = null;
    private Bitmap mGazeBitmap = null;
    private Jama.Matrix mCalibrationMatrix;

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;

    private Context mContext;
    private PeopleDet mPeopleDet;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private FloatingCameraWindow mLeftEyeWindow;
    private FloatingCameraWindow mRightEyeWindow;
    private Paint mFaceLandmardkPaint;
    private Paint mEyePaint;

    public void initialize(
            final Context context,
            final AssetManager assetManager,
            final TrasparentTitleView scoreView,
            final Handler handler) {
        this.mContext = context;
        this.mTransparentTitleView = scoreView;
        this.mInferenceHandler = handler;
        mPeopleDet = new PeopleDet();
        mWindow = new FloatingCameraWindow(mContext);
        mRightEyeWindow = new FloatingCameraWindow(mContext,500,500);
        mLeftEyeWindow = new FloatingCameraWindow(mContext,500,500);

        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.WHITE);
        mFaceLandmardkPaint.setStrokeWidth(5);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);

        mEyePaint = new Paint();
        mEyePaint.setColor(Color.BLUE);
        mEyePaint.setStrokeWidth(1.5f);
        mEyePaint.setStyle(Paint.Style.STROKE);

        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point point = new Point();
        getOrient.getSize(point);
        mScreenWidth = point.x;
        mScreentHeight = point.y;
        Log.d(TAG, String.format("screen size (%d,%d)", mScreenWidth, mScreentHeight));
    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mPeopleDet != null) {
                mPeopleDet.deInit();
            }

            if (mWindow != null) {
                mWindow.release();
            }
            if (mLeftEyeWindow != null) {
                mLeftEyeWindow.release();
            }
            if (mRightEyeWindow!= null) {
                mRightEyeWindow.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {

        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point point = new Point();
        getOrient.getSize(point);
        mScreenWidth = point.x;
        mScreentHeight = point.y;
        Log.d(TAG, String.format("screen size (%d,%d)", mScreenWidth, mScreentHeight));
        if (mScreenWidth < mScreentHeight) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
            mScreenRotation = 90;
        } else {
            orientation = Configuration.ORIENTATION_LANDSCAPE;
            mScreenRotation = 0;
        }

        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null); // Matrix used to transform the Bitmap
    }

    /*
    * PreviewWidth and PreviewHeight are the image resolution for previewing.
    * image -> color planes -> YUVByte array -> RGBFrame -> Bitmap
    * */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

            // Get the array of Pixel Plane
            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWdith != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWdith = image.getWidth();
                mPreviewHeight = image.getHeight();

                Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWdith, mPreviewHeight));
                mRGBBytes = new int[mPreviewWdith * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888); // Each pixel stored in 4 bytes
                mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);   // Input_size changed from 224 to 500
                mLeftBitmap = Bitmap.createBitmap(EYE_WIDTH, EYE_HEIGHT, Config.ARGB_8888);
                mRightBitmap = Bitmap.createBitmap(EYE_WIDTH, EYE_HEIGHT, Config.ARGB_8888);
                mGazeBitmap = Bitmap.createBitmap(mScreenWidth,mScreentHeight,Config.ARGB_8888);

                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()]; // Byte buffer contains the image data of each color plane
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);                       // Transfer the byte data from ByteBuffer to the YUVByte array
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(                                // Now data bytes are in mRGBBytes
                    mYUVBytes[0],
                    mYUVBytes[1],
                    mYUVBytes[2],
                    mRGBBytes,
                    mPreviewWdith,
                    mPreviewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }
        // setPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height)
        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
        //drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(mRGBframeBitmap);
        }

        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        final String targetPath = Constants.getFaceShapeModelPath();
                        if (!new File(targetPath).exists()) {
                            //mTransparentTitleView.setText("Copying landmark model to " + targetPath);
                            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, targetPath);
                        }

                        long startTime = System.currentTimeMillis();
                        List<VisionDetRet> results;
                        synchronized (OnGetImageListener.this) {
                            results = mPeopleDet.detBitmapFace(mRGBframeBitmap, targetPath);   // Change mCroppedBitmap to mRGBframeBitmap
                        }
                        long endTime = System.currentTimeMillis();
                        //mTransparentTitleView.setText("Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
                        // Draw on bitmap
                        // results is a list of VisionDetRet objects
                        if (results != null) {
                            if(results.size() == 0)Log.d(TAG,"Not detected face");
                            for (final VisionDetRet ret : results) {
                                float resizeRatio = 1.0f;
                                Rect bounds = new Rect();
                                bounds.left = (int) (ret.getLeft() * resizeRatio);
                                bounds.top = (int) (ret.getTop() * resizeRatio);
                                bounds.right = (int) (ret.getRight() * resizeRatio);
                                bounds.bottom = (int) (ret.getBottom() * resizeRatio);
                                Log.d(TAG,"Face bounds: "+bounds.left+" "+bounds.right);
                                // Specify the Bitmap for the canvas to draw into
                                Canvas canvas = new Canvas(mRGBframeBitmap);// Change mCroppedBitmap to mRGBframeBitmap
                                Canvas gazeCanvas = new Canvas(mGazeBitmap);
                                canvas.drawRect(bounds, mFaceLandmardkPaint);

                                // Draw landmark
                                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                                /*for (Point point : landmarks) {
                                    int pointX = (int) (point.x * resizeRatio);
                                    int pointY = (int) (point.y * resizeRatio);
                                    Log.d(TAG,"Landmarks position: "+pointX+" "+pointY);
                                    canvas.drawCircle(pointX, pointY, 1, mFaceLandmardkPaint);
                                }*/
                                ArrayList<Point> eyes = new ArrayList<Point>();
                                for(int i=36;i<48;i++){
                                    eyes.add(landmarks.get(i));
                                }
                                /*for (Point point : eyes) {
                                    int pointX = (int) (point.x * resizeRatio);
                                    int pointY = (int) (point.y * resizeRatio);
                                    Log.d(TAG,"Eyes position: "+pointX+" "+pointY);
                                    canvas.drawCircle(pointX, pointY, 1, mEyePaint);
                                }*/
                                int leftx = mPreviewWdith;
                                int lefty = mPreviewHeight;
                                int lefth = 0;
                                int leftw = 0;
                                double leftratio;
                                for(int i=36; i<42; i++){
                                    if(landmarks.get(i).x < leftx && landmarks.get(i).x > 0) leftx = landmarks.get(i).x;
                                }
                                for(int i=36; i<42; i++){
                                    if(landmarks.get(i).y < lefty && landmarks.get(i).y > 0) lefty = landmarks.get(i).y;
                                }
                                for(int i=36; i<42; i++){
                                    if(landmarks.get(i).x-leftx > leftw) leftw = landmarks.get(i).x-leftx;
                                    if(landmarks.get(i).y-lefty > lefth) lefth = landmarks.get(i).y-lefty;
                                }
                                leftratio = (double) lefth/leftw;
                                if(EYE_RATIO < leftratio){leftw = (int)(lefth/EYE_RATIO);}
                                else if(EYE_RATIO > leftratio){lefth = (int)(leftw*EYE_RATIO);}
                                Bitmap LeftBitMap = Bitmap.createBitmap(mRGBframeBitmap, leftx,lefty-5,leftw, lefth);
                                mLeftBitmap = CalibrationActivity.doGreyscale(Bitmap.createScaledBitmap(LeftBitMap,EYE_WIDTH,EYE_HEIGHT,false));

                                double[] observedLeftScale = new double[mLeftBitmap.getWidth()*mLeftBitmap.getHeight()];
                                for(int i=0; i<mLeftBitmap.getHeight();i++){
                                    for (int j=0; j<mLeftBitmap.getWidth();j++){
                                        observedLeftScale[i*mLeftBitmap.getWidth()+j] = Color.red(mLeftBitmap.getPixel(j,i));
                                    }
                                }
                                double[] leftCoefs = computeCoefQR(observedLeftScale,true);
                                /*for(int i=0; i<coefficients.length; i++){
                                    Log.d(TAG,"Coefficients: "+coefficients[i]);
                                }*/
                                Point leftGaze = computeLocation(leftCoefs);
                                int adjustedXLeft = 0;
                                int adjustedYLeft = 0;
                                /*double XR = (double)mScreenWidth/2500;
                                double YR = (double)mScreentHeight/5000;
                                adjustedX = (int)((gazing.x-200)*XR);
                                adjustedY = (int)((gazing.y+100)*YR);*/
                                adjustedXLeft = leftGaze.x;
                                adjustedYLeft = leftGaze.y;
                                //Log.d(TAG,"Right Eye Staring Position: "+adjustedXLeft+", "+adjustedYLeft);

                                int rightx = mPreviewWdith;
                                int righty = mPreviewHeight;
                                int righth = 0;
                                int rightw = 0;
                                double rightratio;
                                for(int i=42; i<48; i++){
                                    if(landmarks.get(i).x < rightx) rightx = landmarks.get(i).x;
                                }
                                for(int i=42; i<48; i++){
                                    if(landmarks.get(i).y < righty) righty = landmarks.get(i).y;
                                }
                                for(int i=42; i<48; i++){
                                    if(landmarks.get(i).x-rightx > rightw) rightw = landmarks.get(i).x-rightx;
                                    if(landmarks.get(i).y-righty > righth) righth = landmarks.get(i).y-righty;
                                }
                                rightratio = (double) righth/rightw;
                                if(EYE_RATIO < rightratio){rightw = (int)(righth/EYE_RATIO);}
                                else if(EYE_RATIO > rightratio){righth = (int)(rightw*EYE_RATIO);}
                                Bitmap RightBitMap = Bitmap.createBitmap(mRGBframeBitmap, rightx,righty-5,rightw, righth);
                                mRightBitmap = CalibrationActivity.doGreyscale(Bitmap.createScaledBitmap(RightBitMap,EYE_WIDTH,EYE_HEIGHT,false));

                                double[] observedRightScale = new double[mRightBitmap.getWidth()*mRightBitmap.getHeight()];
                                for(int i=0; i<mRightBitmap.getHeight();i++){
                                    for (int j=0; j<mRightBitmap.getWidth();j++){
                                        observedRightScale[i*mRightBitmap.getWidth()+j] = Color.red(mRightBitmap.getPixel(j,i));
                                    }
                                }
                                double[] rightCoefs = computeCoefQR(observedRightScale,false);
                                /*for(int i=0; i<coefficients.length; i++){
                                    Log.d(TAG,"Coefficients: "+coefficients[i]);
                                }*/
                                Point rightGaze = computeLocation(rightCoefs);
                                int adjustedXRight = 0;
                                int adjustedYRight = 0;
                                /*double XR = (double)mScreenWidth/2500;
                                double YR = (double)mScreentHeight/5000;
                                adjustedX = (int)((gazing.x-200)*XR);
                                adjustedY = (int)((gazing.y+100)*YR);*/
                                adjustedXRight = rightGaze.x;
                                adjustedYRight = rightGaze.y;
                                //Log.d(TAG,"Left Eye Staring Position: "+adjustedXRight+", "+adjustedYRight);

                                int adjustedX = (adjustedXLeft+adjustedXRight)/2;
                                int adjustedY = (adjustedYLeft+adjustedYRight)/2;
                                Log.d(TAG,"Staring Position: "+adjustedX+", "+adjustedY);
                                gazeCanvas.drawCircle(adjustedX, adjustedY, 5, mFaceLandmardkPaint);
                            }
                        }
                        //mWindow.setRGBBitmap(mRGBframeBitmap);
                        mWindow.setRGBBitmap(mGazeBitmap);
                        mLeftEyeWindow.setRGBBitmap(mLeftBitmap);
                        mRightEyeWindow.setRGBBitmap(mRightBitmap);
                        mIsComputing = false;
                    }
                });

        Trace.endSection();
    }

    private static double[] computeCoefQR(double[] observed, boolean leftEye){
        double[] coef = new double[9];
        double[][] caliScale = new double[9][];
        if(leftEye == true){
            for(int i:mLeftCalibrationGrayScale.keySet()){
            caliScale[i-1] = mLeftCalibrationGrayScale.get(i);
        }
        }else{
            for(int i:mRightCalibrationGrayScale.keySet()){
                caliScale[i-1] = mRightCalibrationGrayScale.get(i);
        }
        }
        double[][] observedScale = new double[1][];
        observedScale[0] = observed;
        Jama.Matrix A = new Jama.Matrix(caliScale);
        A = A.transpose();
        Jama.Matrix B = new Jama.Matrix(observedScale);
        B = B.transpose();
        QRDecomposition qrDecomposition = new QRDecomposition(A);
        Jama.Matrix x = qrDecomposition.solve(B);
        double total = 0;
        for(int i=0; i<caliScale.length; i++){
            coef[i] = x.getArray()[i][0];
            total += coef[i];
        }
        //Normalize Coefficients
        double t = 0;
        for(double i:coef){
            t += i*i;
        }
        for(int i=0;i<coef.length;i++){
            coef[i] = coef[i]/Math.pow(t,1/2d);
        }
        return coef;
    }

    public double[] computeCoefOMP(double[] observed, boolean leftEye) {
        double[][] A;
        double[][] B;
        double[][] residual;
        HashSet<Integer> chosenIndex;

        double[] x = new double[9];
        A = new double[9][];
        if(leftEye == true){
            for(int i:mLeftCalibrationGrayScale.keySet()){
                A[i-1] = mLeftCalibrationGrayScale.get(i);
            }
        }else{
            for(int i:mRightCalibrationGrayScale.keySet()){
                A[i-1] = mRightCalibrationGrayScale.get(i);
            }
        }
        B = new double[1][observed.length];
        residual = new double[1][observed.length];
        chosenIndex = new HashSet<>();
        System.arraycopy(observed, 0, B[0], 0, observed.length);
        System.arraycopy(observed, 0, residual[0], 0, observed.length);
        Jama.Matrix mA = new Jama.Matrix(A);
        mA = mA.transpose();
        Jama.Matrix mB = new Jama.Matrix(B);
        mB = mB.transpose();
        Jama.Matrix mResidual = new Jama.Matrix(residual);
        mResidual = mResidual.transpose();
        int count = 0;
        while (count < 6) {
            double maxInnerProductNorm = 0;
            double maxInnerProduct = 0;
            int maxIndex = 0;
            double scalarCoef = 0;
            for (int i = 0; i < mA.getColumnDimension(); i++) {
                if (!chosenIndex.contains(i)) {
                    Jama.Matrix Ai = mA.getMatrix(0, mA.getRowDimension() - 1, i, i);
                    Jama.Matrix normAi = Ai.times(1 / Ai.norm2());
                    Jama.Matrix normResidual = mResidual.times(1 / mResidual.norm2());
                    double innerProductNorm = Math.abs(normAi.transpose().times(normResidual).get(0, 0));
					/*System.out.println("Current inner product norm with "+i+" is " + innerProductNorm);*/
                    if (innerProductNorm > maxInnerProductNorm) {
                        maxInnerProductNorm = innerProductNorm;
                        maxInnerProduct = Ai.transpose().times(mResidual).get(0, 0);
                        maxIndex = i;
                    }
                }
            }
            Jama.Matrix Amax = mA.getMatrix(0, mA.getRowDimension() - 1, maxIndex, maxIndex);
            scalarCoef = maxInnerProduct / (Math.pow(Amax.norm2(), 2));
            if (x[maxIndex] == 0)
                x[maxIndex] = scalarCoef;
            chosenIndex.add(maxIndex);
            mResidual = mResidual.minus(Amax.times(scalarCoef));
            count += 1;
			/*System.out.println("Max inner product Norm is " + maxInnerProductNorm);
			System.out.println("Max inner product is "+maxInnerProduct);
			System.out.println("Scalar Coef is "+scalarCoef);
			System.out.println("Max index is " + maxIndex);
            System.out.println("Residual is " + mResidual.norm2());*/
        }
        double t = 0;
        for(double i:x){
            t += i*i;
        }
        for(int i=0;i<x.length;i++){
            x[i] = x[i]/Math.pow(t,1/2d);
        }
        return x;
    }

    private static Point computeLocation(double[] coef){
        double x = 0;
        double y = 0;
        for(int i=0; i<coef.length;i++){
            Point point = CalibrationActivity.mCalibrationPos.get(i+1);
            x += coef[i] * point.x;
            y += coef[i] * point.y;
        }
        Point starePoint = new Point((int)x,(int)y);
        return starePoint;
    }

}
