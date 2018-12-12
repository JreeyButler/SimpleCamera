package club.suansuanru.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import club.suansuanru.camera.constant.BaseConstants;
import club.suansuanru.camera.util.CameraSaver;
import club.suansuanru.camera.util.Util;

/**
 * @author Yan.Liangliang
 * @date 2018/9/28
 * @email yanliang@jimi360.cn
 */
public class MyCamera {
    private static final String TAG = "MyCamera";
    private Context mContext;
    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;
    private CameraCaptureSession mCameraCaptureSession;
    private CaptureRequest.Builder mCaptureBuilder;
    private MediaRecorder mMediaRecorder;
    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;
    /**
     * 在关闭相机之前阻止应用程序退出的信号量
     */
    private Semaphore mCameraOpenCloseLock;

    private ImageReader mImageReader;
    private int deviceRotation;
    private String mCameraId;
    private String mVideoPath;
    private String mTimeLapseDirName;
    private Size mPreviewSize, mCaptureSize, mVideoSize;
    private AutoFitTextureView mTextureView;
    private CaptureCallBack mCallBack;
    private LocalServerSocket mServer;
    private LocalSocket mSender, mReceiver;
    private ExecutorService mThreadPool;
    private boolean verticalCapture, timeLapse, showWatermark;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    public MyCamera(Context mContext, AutoFitTextureView mTextureView, CaptureCallBack callBack) {
        this.mContext = mContext;
        this.mTextureView = mTextureView;
        this.mCallBack = callBack;
        this.mCameraOpenCloseLock = new Semaphore(1);
        this.deviceRotation = ((Activity) mContext).getWindowManager().getDefaultDisplay().getRotation();
        this.mThreadPool = Util.getExecutorService(2, 5);
    }

    public void startCameraHandler() {
        mCameraHandlerThread = new HandlerThread("CameraHandler");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
    }

    public void stopCameraHandler() {
        mCameraHandlerThread.quitSafely();
        try {
            mCameraHandlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mCameraHandlerThread = null;
        mCameraHandler = null;
    }

    @SuppressLint("MissingPermission")
    public void openCamera(int width, int height) {
        Log.d(TAG, "openCamera: ");
        if (!Util.hasPermissionGrated(mContext, permissions)) {
            requestPermissions();
            return;
        }
        if (!initDir()) {
            mCallBack.noCapture();
        }
        try {
            if (!mCameraOpenCloseLock.tryAcquire(BaseConstants.TIME_OUT_FOR_CAMERA_OPENING, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("开启相机超时");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        setupCamera(width, height);
        try {
            mCameraManager.openCamera(mCameraId, mCameraStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initLocalSocket() {
        mThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    mServer = new LocalServerSocket(BaseConstants.BASE_SOCKET_NAME);
                    if (mReceiver == null) {
                        mReceiver = new LocalSocket();
                    }
                    mReceiver.connect(new LocalSocketAddress(BaseConstants.BASE_SOCKET_NAME));
                    mReceiver.setReceiveBufferSize(BaseConstants.DEFAULT_BUFFER_SIZE);
                    mReceiver.setSendBufferSize(BaseConstants.DEFAULT_BUFFER_SIZE);
                    mSender = mServer.accept();
                    mSender.setSendBufferSize(BaseConstants.DEFAULT_BUFFER_SIZE);
                    mSender.setReceiveBufferSize(BaseConstants.DEFAULT_BUFFER_SIZE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean initDir() {
        File applicationDir = new File(BaseConstants.APPLICATION_PATH);
        File pictureDir = new File(BaseConstants.PICTURE_PATH);
        File videoDir = new File(BaseConstants.VIDEO_PATH);
        boolean isCreated = true;
        if (!applicationDir.exists()) {
            isCreated = applicationDir.mkdir();
            Log.d(TAG, "initDir: applicationDir = " + isCreated);
        }
        if (!pictureDir.exists()) {
            isCreated = pictureDir.mkdir();
            Log.d(TAG, "initDir: pictureDir = " + isCreated);
        }
        if (!videoDir.exists()) {
            isCreated = videoDir.mkdir();
            Log.d(TAG, "initDir: videoDir = " + isCreated);
        }
        if (!isCreated) {
            Log.e(TAG, "initDir: 初始化文件夹失败");
            return false;
        }
        return true;
    }

    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        closeCameraCaptureSession();
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mCameraOpenCloseLock.release();
        mHandler.removeCallbacksAndMessages(null);
    }

    public void takePicture() {
        timeLapse = false;
        try {
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            // 自动曝光
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
            // 自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_AUTO);
            // 照片方向
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    ORIENTATIONS.get(deviceRotation));

            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.abortCaptures();
            mCameraCaptureSession.capture(captureBuilder.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void timerTakePicture() {
        timeLapse = false;
        long timer = getCaptureTimer();
        Log.d(TAG, "specTakePicture: timer = " + timer);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                takePicture();
                // 可以改进
                mCallBack.timerCaptureSucceed();
            }
        }, timer);
    }

    public void startTimeLapse() {
        timeLapse = true;
        mTimeLapseDirName = createTimeLapseDir();
        for (int i = 0; i < 10; i++) {
            takePicture();
            try {
                Thread.sleep(10000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private String createTimeLapseDir() {
        long now = System.currentTimeMillis();
        File file = new File(String.valueOf(now));
        if (!file.exists()) {
            boolean isCreate = file.mkdirs();
            Log.d(TAG, "createTimeLapseDir: isCreate = " + isCreate);
        }
        return String.valueOf(now + "/");
    }

    public void startRecord(int mode) {
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        initLocalSocket();
        mMediaRecorder = setMediaRecorderParameter(mode);

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if (texture == null) {
            return;
        }
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        try {
            mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        List<Surface> surfaces = new ArrayList<>();
        Surface previewSurface = new Surface(texture);
        Surface recorderSurface = mMediaRecorder.getSurface();

        surfaces.add(previewSurface);
        surfaces.add(recorderSurface);

        mCaptureBuilder.addTarget(previewSurface);
        mCaptureBuilder.addTarget(recorderSurface);

        try {
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCameraCaptureSession = cameraCaptureSession;
                    updatePreview();
                    mMediaRecorder.start();
                    startH264Data();
                    mCallBack.recordStarted();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "onConfigureFailed: ");
                    mCallBack.recordFailed();
                    mCameraCaptureSession = cameraCaptureSession;
                    updatePreview();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startDVRRecord() {
        mThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                if (mMediaRecorder == null) {
                    mMediaRecorder = new MediaRecorder();
                }
                initLocalSocket();
            }
        });
    }

    private void startH264Data() {
        Util.getSingleExecutorService()
                .submit(h264Task);
    }

    private Runnable h264Task = new Runnable() {
        @Override
        public void run() {
            try {
                DataInputStream dis = new DataInputStream(mReceiver.getInputStream());
                long timestamp = dis.readLong();
                Log.d(TAG, "run: timestamp = " + timestamp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    public void stopRecord() {
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
        mCallBack.recordStopped(mVideoPath);
        startPreview();
    }

    public void stopDVRRecord() {
        if (mMediaRecorder == null) {
            return;
        }
        mMediaRecorder.setOnInfoListener(null);
        mMediaRecorder.setOnErrorListener(null);
        try {
            closeLocalSocket();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaRecorder.release();
        mMediaRecorder.reset();
    }

    private void closeLocalSocket() throws IOException {
        Log.d(TAG, "closeLocalSocket: ");
        mServer.close();
        mSender.close();
        mReceiver.close();
    }

    /**
     * 触摸对焦
     *
     * @param event 触摸事件
     */
    public void triggerFocusArea(MotionEvent event) {
        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            Rect sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            orientation = orientation == null ? 0 : orientation;
            MeteringRectangle focusAreaTouch = getMeteringRectangle(event, sensorArraySize, orientation);
            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);

                }
            };
            // 停止
            mCameraCaptureSession.stopRepeating();
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            mCameraCaptureSession.capture(mCaptureBuilder.build(), captureCallback, mCameraHandler);
            // 自动
            mCaptureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // 设置AF
            if (characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1) {
                mCaptureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
            }
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // 设置AE
            if (characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) >= 1) {
                mCaptureBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{focusAreaTouch});
            }
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // 开始预览
            mCameraCaptureSession.capture(mCaptureBuilder.build(), captureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private MeteringRectangle getMeteringRectangle(MotionEvent event, Rect rect, int orientation) {
        int x, y;
        final int halfTouchWidth = 150;
        final int halfTouchHeight = 150;
        Log.d(TAG, "getMeteringRectangle: orientation  =  " + orientation);
        switch (orientation) {
            case 0:
                x = (int) ((event.getX() / (float) mPreviewSize.getWidth() * (float) rect.width()));
                y = (int) ((event.getY() / (float) mPreviewSize.getHeight() * (float) rect.height()));
                break;
            default:
                x = (int) (0.5 * (float) rect.width());
                y = (int) (0.5 * (float) rect.height());
                break;
        }

        Log.d(TAG, "getMeteringRectangle: x = " + x + ",y = " + y);
        return new MeteringRectangle(
                Math.max((x - halfTouchWidth), 0),
                Math.max((y - halfTouchHeight), 0),
                halfTouchWidth * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);
    }

    /**
     * 设置Camera参数
     *
     * @param width  宽
     * @param height 高
     */
    private void setupCamera(int width, int height) {
        Log.d(TAG, "setupCamera: ");
        if (mCameraManager == null) {
            mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        }
        if (mCameraId == null || "".equals(mCameraId)) {
            try {
                mCameraId = getCameraId();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                throw new RuntimeException("无法获取相机尺寸");
            }
            mCaptureSize = getCaptureSize(map.getOutputSizes(ImageFormat.JPEG));
            mVideoSize = getVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height, mCaptureSize);
            Log.d(TAG, "setupCamera: mPreviewSize width =  " + mPreviewSize.getWidth() + " ,height = " + mPreviewSize.getHeight());
            int orientation = mContext.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            setImageReader();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setImageReader() {
        mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Image image = imageReader.acquireNextImage();
                CameraSaver.Builder builder = new CameraSaver.Builder()
                        .handler(mHandler)
                        .image(image);
                if (showWatermark) {
                    builder.watermark(BitmapFactory.decodeResource(mContext.getResources(),
                            R.drawable.ic_watermark_logo));
                }
                CameraSaver saver = builder.build();
                if (isFrontCamera()) {
                    saver.setFrontCamera(true);
                }
                if (timeLapse) {
                    saver.setTimeLapseDirName(mTimeLapseDirName);
                }
                Log.d(TAG, "onImageAvailable: verticalCapture =" + verticalCapture);
                if (verticalCapture) {
                    saver.setVerticalCapture(true);
                }
                mHandler.post(saver);
                updatePreview();
            }
        }, mCameraHandler);
    }

    /**
     * 取设备支持的JPEG格式支持的最大的尺寸
     *
     * @param outputSizes ImageFormat.JPEG尺寸列表
     * @return 拍摄照片的尺寸
     */
    private Size getCaptureSize(Size[] outputSizes) {
        return Collections.max(Arrays.asList(outputSizes), new Comparator<Size>() {
            @Override
            public int compare(Size size, Size t1) {
                return Long.signum(size.getWidth() * size.getHeight()
                        - t1.getWidth() * t1.getHeight());
            }
        });
    }

    /**
     * 取录制视频的尺寸
     *
     * @param outputSizes MediaRecorder尺寸列表
     * @return 拍摄视频的尺寸
     */
    private Size getVideoSize(@NonNull Size[] outputSizes) {
        for (Size size : outputSizes) {
            if (size.getWidth() == size.getHeight() * 4 / 3 &&
                    size.getWidth() < 1080) {
                return size;
            }
        }
        Log.d(TAG, "getCaptureSize: Couldn't find any suitable video size");
        return outputSizes[outputSizes.length - 1];
    }

    /**
     * 选择和是的预览尺寸
     *
     * @param outputSizes TextureView 支持的尺寸
     * @param width       设备TextureView显示的宽度
     * @param height      设备TextureView显示的高度
     * @param captureSize 照片尺寸
     * @return 相机的预览尺寸
     */
    private Size chooseOptimalPreviewSize(Size[] outputSizes, int width, int height, Size captureSize) {
        List<Size> bigEnough = new ArrayList<>();
        int w = captureSize.getWidth();
        int h = captureSize.getHeight();

        for (Size option : outputSizes) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width &&
                    option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new Comparator<Size>() {
                @Override
                public int compare(Size size, Size t1) {
                    return Long.signum((long) size.getWidth() * size.getWidth() -
                            (long) t1.getWidth() * t1.getHeight());
                }
            });
        }
        Log.e(TAG, "chooseOptimalPreviewSize: 无法找到合适的预览尺寸");
        return outputSizes[0];
    }

    private CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = (Activity) mContext;
            if (activity != null) {
                activity.finish();
            }
        }
    };

    private void startPreview() {
        if (mCameraDevice == null || !mTextureView.isAvailable() || mPreviewSize == null) {
            return;
        }
        closeCameraCaptureSession();
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(texture);
        try {
            mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            mCameraCaptureSession = cameraCaptureSession;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "onConfigureFailed: 预览失败");
                        }
                    }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void updatePreview() {
        if (mCameraDevice == null) {
            return;
        }
        setupCaptureRequestBuilder(mCaptureBuilder);
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        try {
            mCameraCaptureSession.setRepeatingRequest(mCaptureBuilder.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private void closeCameraCaptureSession() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case BaseConstants.Message.MSG_SAVE_PICTURE_SUCCEED:
                    String picturePath = String.valueOf(msg.obj);
                    mCallBack.captureSucceed(picturePath);
                    break;
                case BaseConstants.Message.MSG_SAVE_PICTURE_FAILED:
                    mCallBack.captureFailed();
                    break;
                case BaseConstants.Message.MSG_SAVE_PICTURE_ERROR:
                    mCallBack.captureFailed();
                    break;
                default:
                    break;
            }
        }
    };

    private MediaRecorder setMediaRecorderParameter(int mode) {
        Log.d(TAG, "setMediaRecorderParameter: ");
        mVideoPath = getVideoFilePath();
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (mode == BaseConstants.CaptureMode.MODE_NORMAL) {
            recorder.setOutputFile(mVideoPath);
        } else if (mode == BaseConstants.CaptureMode.MODE_DVR) {
            recorder.setOutputFile(mSender.getFileDescriptor());
        }
        recorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        recorder.setVideoEncodingBitRate(BaseConstants.DEFAULT_VIDEO_BIT_RATE);
        recorder.setVideoFrameRate(BaseConstants.DEFAULT_VIDEO_FRAME_RATE);
        try {
            recorder.prepare();
        } catch (IOException e) {
            mCallBack.recordFailed();
            e.printStackTrace();
        }
        return recorder;
    }

    private String getVideoFilePath() {
        long now = System.currentTimeMillis();
        return BaseConstants.VIDEO_PATH + now + BaseConstants.TYPE_VIDEO;
    }

    private String getCameraId() throws CameraAccessException {
        for (String id : mCameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            facing = facing == null ? 0 : facing;
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                continue;
            }
            return id;
        }
        return mCameraManager.getCameraIdList()[0];
    }

    public void setCameraId(int lensFacing) throws CameraAccessException {
        for (String id : mCameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            facing = facing == null ? 0 : facing;
            if (facing == lensFacing) {
                mCameraId = id;
            }
        }
    }

    private long getCaptureTimer() {
        int timer = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_TIMER_CAPTURE);
        switch (timer) {
            case BaseConstants.TIMER_CAPTURE_3S:
                return 3000L;
            case BaseConstants.TIMER_CAPTURE_5S:
                return 5000;
            case BaseConstants.TIMER_CAPTURE_10S:
                return 10000L;
            default:
                break;
        }
        return 3000L;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ((Activity) mContext).requestPermissions(permissions, BaseConstants.REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     * 设置竖直拍摄标识
     *
     * @param isVertical 竖直拍摄
     */
    public void setVerticalCapture(boolean isVertical) {
        this.verticalCapture = isVertical;
    }

    public void showWatermark(boolean isShow) {
        this.showWatermark = isShow;
    }

    private boolean isFrontCamera() {
        try {
            for (String id : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                facing = facing == null ? 0 : facing;
                if (facing == CameraCharacteristics.LENS_FACING_FRONT && id.equals(mCameraId)) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public interface CaptureCallBack {
        /**
         * 文件夹创建失败，无法拍摄
         */
        void noCapture();

        /**
         * 照片拍摄成功
         *
         * @param picturePath 照片路径
         */
        void captureSucceed(String picturePath);

        /**
         * 照片拍摄失败
         */
        void captureFailed();

        /**
         * 摄像成功
         */
        void recordStarted();

        /**
         * 摄像失败
         */
        void recordFailed();

        /**
         * 摄像停止
         *
         * @param videoPath 视频路径
         */
        void recordStopped(String videoPath);

        /**
         * 定时拍摄成功
         */
        void timerCaptureSucceed();

        /**
         * 延时拍摄成功
         */
        void timeLapseSucceed();
    }
}
