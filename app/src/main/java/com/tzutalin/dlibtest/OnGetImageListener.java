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
* Testing step
* 1. Try to directly detect landmarks out of the original frame, draw it on the floating window
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
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.PeopleDet;
import com.tzutalin.dlib.VisionDetRet;

import junit.framework.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    private static final int NUM_CLASSES = 1001;
    private static final int INPUT_SIZE = 500;
    private static final int IMAGE_MEAN = 117;
    private static final String TAG = "OnGetImageListener";

    private int mScreenRotation = 90;

    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;

    private Context mContext;
    private PeopleDet mPeopleDet;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private Paint mFaceLandmardkPaint;

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

        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.GREEN);
        mFaceLandmardkPaint.setStrokeWidth(1);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);
    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mPeopleDet != null) {
                mPeopleDet.deInit();
            }

            if (mWindow != null) {
                mWindow.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {

        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point point = new Point();
        getOrient.getSize(point);
        int screen_width = point.x;
        int screen_height = point.y;
        Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
        if (screen_width < screen_height) {
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
        canvas.drawBitmap(src, matrix, null);
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
        drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(mCroppedBitmap);
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
                                Canvas canvas = new Canvas(mRGBframeBitmap);
                                // mFaceLandmardkPaint is a Paint() class                      // Change mCroppedBitmap to mRGBframeBitmap
                                canvas.drawRect(bounds, mFaceLandmardkPaint);

                                // Draw landmark
                                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                                for (Point point : landmarks) {
                                    int pointX = (int) (point.x * resizeRatio);
                                    int pointY = (int) (point.y * resizeRatio);
                                    Log.d(TAG,"Landmarks position: "+pointX+" "+pointY);
                                    canvas.drawCircle(pointX, pointY, 1, mFaceLandmardkPaint);
                                }
                            }
                        }

                        mWindow.setRGBBitmap(mRGBframeBitmap);
                        mIsComputing = false;
                    }
                });

        Trace.endSection();
    }
}
