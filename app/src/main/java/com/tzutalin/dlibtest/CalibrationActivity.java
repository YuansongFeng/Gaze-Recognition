package com.tzutalin.dlibtest;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.PeopleDet;
import com.tzutalin.dlib.VisionDetRet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.tzutalin.dlibtest.OnGetImageListener.EYE_HEIGHT;
import static com.tzutalin.dlibtest.OnGetImageListener.EYE_RATIO;
import static com.tzutalin.dlibtest.OnGetImageListener.EYE_WIDTH;


public class CalibrationActivity extends AppCompatActivity {
    private Button btnCali1;
    private Button btnCali2;
    private Button btnCali3;
    private Button btnCali4;
    private Button btnCali5;
    private Button btnCali6;
    private Button btnCali7;
    private Button btnCali8;
    private Button btnCali9;
    private static final String TAG = "Gaze_Calibration";
    private static ArrayList<Button> buttonList;
    public static HashMap<Integer,Point> mCalibrationPos;
    public static HashMap<Integer,double[]> mCalibrationGrayScale;

    private static int mOrder = 0;
    private static final int REQUEST_CAMERA = 1;
    private ImageView checkPhoto;
    private CameraDevice mCameraDevice;
    private PeopleDet mPeopleDet;
    private String mCameraId;
    private Semaphore mCameraLock = new Semaphore(1);
    private ImageReader mImageReader;
    private File mFile;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private CameraCaptureSession mCaptureSession;
    private Bitmap mLeftBitmap;
    private Bitmap mRightBitmap;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraLock.release(); // Here the release means that the Camera can be CLOSED.
            mCameraDevice = cameraDevice;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraLock.release();
            cameraDevice.close();
            ;
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraLock.release();
            mCameraDevice.close();
            mCameraDevice = null;
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);
        mPeopleDet = new PeopleDet();
        buttonList = new ArrayList<Button>();
        mCalibrationPos = new HashMap<>();
        mCalibrationGrayScale = new HashMap<>();
        btnCali1 = (Button) findViewById(R.id.btnCali1);
        btnCali2 = (Button) findViewById(R.id.btnCali2);
        btnCali3 = (Button) findViewById(R.id.btnCali3);
        btnCali4 = (Button) findViewById(R.id.btnCali4);
        btnCali5 = (Button) findViewById(R.id.btnCali5);
        btnCali6 = (Button) findViewById(R.id.btnCali6);
        btnCali7 = (Button) findViewById(R.id.btnCali7);
        btnCali8 = (Button) findViewById(R.id.btnCali8);
        btnCali9 = (Button) findViewById(R.id.btnCali9);
        buttonList.add(btnCali1);
        buttonList.add(btnCali2);
        buttonList.add(btnCali3);
        buttonList.add(btnCali4);
        buttonList.add(btnCali5);
        buttonList.add(btnCali6);
        buttonList.add(btnCali7);
        buttonList.add(btnCali8);
        buttonList.add(btnCali9);
        checkPhoto = (ImageView) findViewById(R.id.photoView);


        cameraCapture();
        btnCali5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 5;
                createNewFile(mOrder);
                setBtnVisible(btnCali1);
                cameraCapture();
                int[] pos = new int[2];
                btnCali5.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(Integer.valueOf(mOrder),point);
            }
        });
        btnCali1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 1;
                createNewFile(mOrder);
                setBtnVisible(btnCali2);
                cameraCapture();
                int[] pos = new int[2];
                btnCali1.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(mOrder,point);
            }
        });
        btnCali2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 2;
                createNewFile(mOrder);
                setBtnVisible(btnCali3);
                cameraCapture();
                int[] pos = new int[2];
                btnCali2.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(mOrder,point);
            }
        });
        btnCali3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 3;
                createNewFile(mOrder);
                setBtnVisible(btnCali6);
                cameraCapture();
                int[] pos = new int[2];
                btnCali3.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(mOrder,point);
            }
        });
        btnCali6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 6;
                createNewFile(mOrder);
                setBtnVisible(btnCali9);
                cameraCapture();
                int[] pos = new int[2];
                btnCali6.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(mOrder,point);
            }
        });
        btnCali9.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 9;
                createNewFile(mOrder);
                setBtnVisible(btnCali8);
                cameraCapture();
                int[] pos = new int[2];
                btnCali9.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(mOrder,point);
            }
        });
        btnCali8.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 8;
                createNewFile(mOrder);
                setBtnVisible(btnCali7);
                cameraCapture();
                int[] pos = new int[2];
                btnCali8.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(mOrder,point);
            }
        });
        btnCali7.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 7;
                createNewFile(mOrder);
                setBtnVisible(btnCali4);
                cameraCapture();
                int[] pos = new int[2];
                btnCali7.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(mOrder,point);
            }
        });
        btnCali4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOrder = 4;
                createNewFile(mOrder);
                cameraCapture();
                int[] pos = new int[2];
                btnCali4.getLocationOnScreen(pos);
                Point point = new Point(pos[0],pos[1]);
                mCalibrationPos.put(mOrder,point);
                stopBackGroundThread();
                closeCamera();
                btnCali4.setBackgroundColor(Color.BLACK);
                btnCali4.setEnabled(false);
                Intent intent = new Intent(CalibrationActivity.this,MainActivity.class);
                startActivity(intent);
            }
        });
        for (int i = 0; i < buttonList.size(); i++) {
            if (!buttonList.get(i).equals(btnCali5)) {
                buttonList.get(i).setEnabled(false);
            }
        }
        startBackGroundThread();
        openCamera();

    }

    private void createNewFile(int order){
        mFile = new File(Environment.getExternalStorageDirectory(), order+".jpg");
        if(mFile.exists()){
            mFile.delete();
        }
        mFile.deleteOnExit();
    }
    private void setBtnVisible(Button btn) {
        for (int i = 0; i < buttonList.size(); i++) {
            if (!buttonList.get(i).equals(btn)) {
                buttonList.get(i).setBackgroundColor(Color.BLACK);
                buttonList.get(i).setEnabled(false);
            } else {
                buttonList.get(i).setBackgroundColor(Color.WHITE);
                buttonList.get(i).setEnabled(true);
            }
        }
    }
    private void startBackGroundThread(){
        mBackgroundThread = new HandlerThread("GazeCalibration");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    private void stopBackGroundThread(){
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }catch(InterruptedException e){
            e.printStackTrace();
        }
    }

    private void openCamera(){
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            String[] requests = new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, requests, REQUEST_CAMERA);
            return;
        }
        // Obtain the mCameraDevice here
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        Size largest = null;
        try {
            if (!mCameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Timeout!");
            }
            int cam_counter = 0;
            mCameraId = manager.getCameraIdList()[0];
            while(cam_counter<manager.getCameraIdList().length){
                String cameraID = manager.getCameraIdList()[cam_counter];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
                if(characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT){
                    mCameraId = cameraID;
                    break;}
                else{
                    cam_counter += 1;
                }
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (null == mCameraDevice) return;
                            mCaptureSession = session;
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {

                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera(){
        try{
            mCameraLock.acquire();
            if(mCaptureSession != null){
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if(mCameraDevice != null){
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if(mImageReader != null){
                mImageReader.close();
                mImageReader = null;
            }
        }catch(InterruptedException e){
            e.printStackTrace();
        }finally{
            mCameraLock.release();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_CAMERA) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    new AlertDialog.Builder(this).setMessage("Error Getting Permission")
                            .setPositiveButton(android.R.string.ok, null).show();
                    break;
                } else {
                    Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void cameraCapture() {
        try {
            if (null == mCameraDevice) return;
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    mBackgroundHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            CalibrationActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showPhoto();
                                }
                            });
                        }
                    });
                }
            };
            //mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void showPhoto(){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(mFile.getAbsolutePath(), options);
        List<VisionDetRet> results;
        final String targetPath = Constants.getFaceShapeModelPath();
        if (!new File(targetPath).exists()) {
            //mTransparentTitleView.setText("Copying landmark model to " + targetPath);
            FileUtils.copyFileFromRawToOthers(CalibrationActivity.this, R.raw.shape_predictor_68_face_landmarks, targetPath);
        }
        results = mPeopleDet.detBitmapFace(bitmap, targetPath);
        for(VisionDetRet face:results){
            ArrayList<Point> landmarks = face.getFaceLandmarks();
            ArrayList<Point> eyes = new ArrayList<Point>();
            for(int i=36;i<48;i++){
                eyes.add(landmarks.get(i));
            }
            int leftx = 2000;
            int lefty = 2000;
            int lefth = 0;
            int leftw = 0;
            double leftratio;
            for(int i=36; i<42; i++){
                if(landmarks.get(i).x < leftx) leftx = landmarks.get(i).x;
            }
            for(int i=36; i<42; i++){
                if(landmarks.get(i).y < lefty) lefty = landmarks.get(i).y;
            }
            for(int i=36; i<42; i++){
                if(landmarks.get(i).x-leftx > leftw) leftw = landmarks.get(i).x-leftx;
                if(landmarks.get(i).y-lefty > lefth) lefth = landmarks.get(i).y-lefty;
            }
            leftratio = (double) lefth/leftw;
            if(EYE_RATIO < leftratio){leftw = (int)(lefth/EYE_RATIO);}
            else if(EYE_RATIO > leftratio){lefth = (int)(leftw*EYE_RATIO);}
            Bitmap LeftBitMap = Bitmap.createBitmap(bitmap, leftx,lefty,leftw, lefth);
            mLeftBitmap = doGreyscale(Bitmap.createScaledBitmap(LeftBitMap,EYE_WIDTH,EYE_HEIGHT,false));

            double[] grayScale = new double[mLeftBitmap.getWidth()*mLeftBitmap.getHeight()];
            for(int i=0; i<mLeftBitmap.getHeight();i++){
                for (int j=0; j<mLeftBitmap.getWidth();j++){
                    grayScale[i*mLeftBitmap.getWidth()+j] = Color.red(mLeftBitmap.getPixel(j,i));
                }
            }
            mCalibrationGrayScale.put(mOrder,grayScale);

            /*Log.d(TAG,"mOrder: "+mOrder);
            Log.d(TAG,"grayScale Length :"+grayScale.length);*/



            int rightx = 2000;
            int righty = 2000;
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
            Bitmap RightBitMap = Bitmap.createBitmap(bitmap, rightx,righty,rightw, righth);
            mRightBitmap = doGreyscale(Bitmap.createScaledBitmap(RightBitMap,EYE_WIDTH,EYE_HEIGHT,false));
        }

        checkPhoto.setImageBitmap(mLeftBitmap);
        if(mFile.exists()) Log.d(TAG,"mFile exists.",null);
        else Log.d(TAG,"mFile not exists.",null);
        Toast.makeText(this,"File Path"+mFile.getAbsolutePath(),Toast.LENGTH_SHORT).show();
    }
    @Override
    protected void onPause(){
        super.onPause();
        if(mCameraDevice != null){
            closeCamera();
        }
    }

    private static class ImageSaver implements Runnable {
        private final Image mImage;
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
            Log.d(TAG,"Store the File at location "+mFile.getAbsolutePath(),null);
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream fo = null;
            try {
                fo = new FileOutputStream(mFile);
                fo.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
            }

        }
    }

    public static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight();
        }
    }
    public static Bitmap doGreyscale(Bitmap src) {
        // constant factors
        final double GS_RED = 0.299;
        final double GS_GREEN = 0.587;
        final double GS_BLUE = 0.114;
        // create output bitmap
        Bitmap bmOut = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
        // pixel information
        int A, R, G, B;
        int pixel;
        // get image size
        int width = src.getWidth();
        int height = src.getHeight();
        // scan through every single pixel
        for(int x = 0; x < width; ++x) {
            for(int y = 0; y < height; ++y) {
                // get one pixel color
                pixel = src.getPixel(x, y);
                // retrieve color of all channels
                A = Color.alpha(pixel);
                R = Color.red(pixel);
                G = Color.green(pixel);
                B = Color.blue(pixel);
                // take conversion up to one single value
                R = G = B = (int)(GS_RED * R + GS_GREEN * G + GS_BLUE * B);
                // set new pixel color to output bitmap
                bmOut.setPixel(x, y, Color.argb(A, R, G, B));
            }
        }

        // return final image
        return bmOut;
    }

}

