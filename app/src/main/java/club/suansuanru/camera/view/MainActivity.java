package club.suansuanru.camera.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import club.suansuanru.camera.AutoFitTextureView;
import club.suansuanru.camera.MyCamera;
import club.suansuanru.camera.R;
import club.suansuanru.camera.constant.BaseConstants;
import club.suansuanru.camera.constant.BaseConstants.CameraMode;
import club.suansuanru.camera.constant.BaseConstants.CaptureMode;
import club.suansuanru.camera.constant.BaseConstants.FlashMode;
import club.suansuanru.camera.util.SettingsItemAdapter;
import club.suansuanru.camera.util.Util;

/**
 * @author Yan.Liangliang
 * @date 2018/9/28
 * @email yanliang@jimi360.cn
 */
public class MainActivity extends Activity implements View.OnClickListener,
        TextureView.SurfaceTextureListener {
    private final String TAG = "MainActivity";
    private Context mContext;
    private AutoFitTextureView mTextureView;
    private ImageView mCapture, mFocus, mSettings, mFlash, mSpecMode,
            mCameraMode, mSwitchCamera, mRecordStatus, mThumbnail;
    private TextView mRecordTime;
    private LinearLayout mRecordInfo, mCameraSettings;
    private MyCamera mCamera;
    private ExecutorService recordInfoService, focusService;
    private Future recordFuture;
    private OrientationEventListener mOrEventListener;
    private SettingsItemAdapter mAdapter;

    private int screenRotation;
    private int lensFacing;

    private boolean isShowSettingsItem;
    private long startTime;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lensFacing = CameraCharacteristics.LENS_FACING_BACK;
        mContext = this;
        initView();
        initListener();
    }

    private void initView() {
        mTextureView = findViewById(R.id.camera_preview);
        mFocus = findViewById(R.id.camera_focus);
        mCapture = findViewById(R.id.capture);
        mCameraMode = findViewById(R.id.camera_mode);
        mSettings = findViewById(R.id.settings);
        mFlash = findViewById(R.id.flash);
        mSwitchCamera = findViewById(R.id.switch_camera);
        mRecordInfo = findViewById(R.id.record_info);
        mRecordStatus = findViewById(R.id.record_status);
        mRecordTime = findViewById(R.id.record_time);
        mThumbnail = findViewById(R.id.thumbnail);
        mCameraSettings = findViewById(R.id.camera_settings);
        mSpecMode = findViewById(R.id.camera_spec_mode);
        initSettingView();
    }

    private void initSettingView() {
        ListView mSettingsList = findViewById(R.id.settings_list);
        // 设置ListView Item 分割线颜色
        mSettingsList.setDivider(new ColorDrawable(Color.GRAY));
        mSettingsList.setDividerHeight(1);

        mAdapter = new SettingsItemAdapter(mContext);
        mAdapter.setCallBack(mSettingCallBack);
        mSettingsList.setAdapter(mAdapter);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initListener() {
        mTextureView.setOnTouchListener(onTouchListener);
        mCapture.setOnClickListener(this);
        mFlash.setOnClickListener(this);
        mCameraMode.setOnClickListener(this);
        mSwitchCamera.setOnClickListener(this);
        mSettings.setOnClickListener(this);
        mThumbnail.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openCamera();
        initCameraSettings();
        showThumbnail();
        initOrientation();
    }

    private void openCamera() {
        if (mCamera == null) {
            mCamera = new MyCamera(mContext, mTextureView, mCaptureCallBack);
        }
        mCamera.startCameraHandler();
        if (mTextureView.isAvailable()) {
            mCamera.openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(this);
        }
    }

    private void initCameraSettings() {
        int captureMode = getCameraMode();
        int flashMode = getFlashMode();

        if (captureMode == CameraMode.MODE_UNKNOWN) {
            setCameraMode(CameraMode.MODE_TAKE_PICTURE);
        } else {
            setCameraMode(captureMode);
        }

        if (flashMode == FlashMode.MODE_UNKNOWN) {
            setFlashMode(FlashMode.MODE_AUTO);
        } else {
            setFlashMode(flashMode);
        }


    }

    private void initOrientation() {
        mOrEventListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int i) {
                setViewRotation(Util.getScreenRotation(i));
            }
        };
        mOrEventListener.enable();
    }

    private void setViewRotation(int rotation) {
        if (rotation == screenRotation) {
            return;
        }
        mCameraMode.setPivotX(mCameraMode.getWidth() / 2);
        mCameraMode.setPivotY(mCameraMode.getHeight() / 2);
        mThumbnail.setPivotX(mThumbnail.getWidth() / 2);
        mThumbnail.setPivotY(mThumbnail.getHeight() / 2);
        mSwitchCamera.setPivotX(mSwitchCamera.getWidth() / 2);
        mSwitchCamera.setPivotY(mSwitchCamera.getHeight() / 2);
        switch (rotation) {
            case Surface.ROTATION_0:
                setIconRotation(0);
                mCamera.setVerticalCapture(true);
                break;
            case Surface.ROTATION_90:
                setIconRotation(90);
                mCamera.setVerticalCapture(false);
                break;
            case Surface.ROTATION_180:
                setIconRotation(180);
                mCamera.setVerticalCapture(true);
                break;
            case Surface.ROTATION_270:
                setIconRotation(270);
                mCamera.setVerticalCapture(false);
                break;
            default:
                setIconRotation(0);
                mCamera.setVerticalCapture(false);
                break;
        }
        screenRotation = rotation;
    }


    @Override
    protected void onPause() {
        super.onPause();
        mCamera.closeCamera();
        mCamera.stopCameraHandler();
        if (mCameraSettings.isShown()) {
            mCameraSettings.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.closeCamera();
        if (mOrEventListener != null) {
            mOrEventListener.disable();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        Log.d(TAG, "onSurfaceTextureAvailable: ");
        mCamera.openCamera(i, i1);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    @Override
    public void onClick(View view) {
        int cameraMode = getCameraMode();
        int flashMode = getFlashMode();

        switch (view.getId()) {
            case R.id.capture:
                capture(cameraMode);
                break;
            case R.id.camera_mode:
                if (cameraMode == CameraMode.MODE_TAKE_PICTURE) {
                    setCameraMode(CameraMode.MODE_RECORD);
                } else if (cameraMode == CameraMode.MODE_RECORD) {
                    setCameraMode(CameraMode.MODE_TAKE_PICTURE);
                } else {
                    Log.d(TAG, "onClick: do nothing");
                }
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
                break;
            case R.id.switch_camera:
                try {
                    if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                        mCamera.setCameraId(CameraCharacteristics.LENS_FACING_BACK);
                        lensFacing = CameraCharacteristics.LENS_FACING_BACK;
                        Glide.with(mContext).load(R.drawable.ic_camera_front).into(mSwitchCamera);
                    } else if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        mCamera.setCameraId(CameraCharacteristics.LENS_FACING_FRONT);
                        lensFacing = CameraCharacteristics.LENS_FACING_FRONT;
                        Glide.with(mContext).load(R.drawable.ic_camera_rear).into(mSwitchCamera);
                    } else {
                        mCamera.setCameraId(CameraCharacteristics.LENS_FACING_BACK);
                        lensFacing = CameraCharacteristics.LENS_FACING_BACK;
                        Glide.with(mContext).load(R.drawable.ic_camera_front).into(mSwitchCamera);
                    }
                } catch (CameraAccessException e) {
                    Log.e(TAG, "onClick: ");
                }
                mCamera.closeCamera();
                mCamera.openCamera(mTextureView.getWidth(), mTextureView.getHeight());
                break;
            case R.id.settings:
                showCameraSettings(isShowSettingsItem);
                break;
            case R.id.flash:
                if (flashMode == FlashMode.MODE_AUTO) {
                    setFlashMode(FlashMode.MODE_ON);
                } else if (flashMode == FlashMode.MODE_ON) {
                    setFlashMode(FlashMode.MODE_OFF);
                } else if (flashMode == FlashMode.MODE_OFF) {
                    setFlashMode(FlashMode.MODE_AUTO);
                } else {
                    setFlashMode(FlashMode.MODE_UNKNOWN);
                }
                break;
            case R.id.thumbnail:
                startActivity(new Intent(mContext, GalleryActivity.class));
                break;
            default:
                break;
        }
    }

    private void capture(int cameraMode) {
        switch (cameraMode) {
            case CameraMode.MODE_TAKE_PICTURE:
                if (isSpecMode()) {
                    specTakePicture();
                } else {
                    mCamera.takePicture();
                }
                break;
            case CameraMode.MODE_RECORD:
                if (isSpecMode()) {
                    mCamera.startRecord(CaptureMode.MODE_DVR);
                } else {
                    mCamera.startRecord(CaptureMode.MODE_NORMAL);
                }
                break;
            case CameraMode.MODE_STOP_RECORD:
                Log.d(TAG, "capture: isSpecMode = " + isSpecMode());
                if (isSpecMode()) {
                    mCamera.stopDVRRecord();
                } else {
                    mCamera.stopRecord();
                }
                break;
            case CameraMode.MODE_UNKNOWN:
                break;
            default:
                Log.d(TAG, "onClick: unknown mode,do nothing!");
                break;
        }
    }

    private boolean isSpecMode() {
        int cameraMode = getCameraMode();
        switch (cameraMode) {
            case CameraMode.MODE_TAKE_PICTURE:
                boolean isTimerCapture =
                        getValueFromKey(BaseConstants.Key.KEY_TIMER_CAPTURE) != BaseConstants.FLAG_ITEM_SWITCH_OFF;
                boolean isTimeLapse =
                        getValueFromKey(BaseConstants.Key.KEY_TIME_LAPSE_MODE) != BaseConstants.FLAG_ITEM_SWITCH_OFF;

                return isTimerCapture | isTimeLapse;
            case CameraMode.MODE_RECORD:
                boolean isDVR = getValueFromKey(BaseConstants.Key.KEY_DVR_MODE) != BaseConstants.FLAG_ITEM_SWITCH_OFF;
                Log.d(TAG, "isSpecMode: isDVR = " + isDVR);
                return isDVR;
            default:
                break;
        }
        return false;
    }


    private int getValueFromKey(String key) {
        int value = Util.getIntSharePreference(mContext, key);
        return value == BaseConstants.DEFAULT_INT_VALUE ? BaseConstants.FLAG_ITEM_SWITCH_OFF : value;
    }

    private void showCameraSettings(boolean isShowing) {
        if (!isShowing) {
            mCameraSettings.setVisibility(View.VISIBLE);
        } else {
            mCameraSettings.setVisibility(View.GONE);
        }
        isShowSettingsItem = !isShowSettingsItem;
    }

    private void setFlashMode(int mode) {
        switch (mode) {
            case FlashMode.MODE_AUTO:
                Glide.with(mContext).load(R.drawable.ic_flash_auto).into(mFlash);
                break;
            case FlashMode.MODE_ON:
                Glide.with(mContext).load(R.drawable.ic_flash_on).into(mFlash);
                break;
            case FlashMode.MODE_OFF:
                Glide.with(mContext).load(R.drawable.ic_flash_off).into(mFlash);
                break;
            case FlashMode.MODE_UNKNOWN:
                mFlash.setVisibility(View.GONE);
                break;
            default:
                break;
        }
        Util.setIntSharePreference(mContext, BaseConstants.Key.KEY_FLASH_MODE, mode);
    }


    /**
     * 设置图标转向角度
     *
     * @param rotation 旋转角度
     */
    private void setIconRotation(int rotation) {
        mCameraMode.setRotation(rotation);
        mThumbnail.setRotation(rotation);
        mSwitchCamera.setRotation(rotation);
        mFlash.setRotation(rotation);
        mSettings.setRotation(rotation);
        mCameraSettings.setRotation(rotation);
        mSpecMode.setRotation(rotation);
    }

    private void setCameraMode(int mode) {
        switch (mode) {
            case CameraMode.MODE_TAKE_PICTURE:
                setView(mCameraMode, R.drawable.ic_switch_record);
                setView(mCapture, R.drawable.ic_take_picture);
                break;
            case CameraMode.MODE_RECORD:
                setView(mCapture, R.drawable.ic_start_record);
                setView(mCameraMode, R.drawable.ic_switch_capture);
                break;
            case CameraMode.MODE_STOP_RECORD:
                setView(mCapture, R.drawable.ic_stop_record);
                break;
            case CameraMode.MODE_UNKNOWN:
                setCameraMode(CameraMode.MODE_TAKE_PICTURE);
                break;
            default:
                break;
        }
        Util.setIntSharePreference(mContext, BaseConstants.Key.KEY_CAMERA_MODE, mode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
    }

    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            checkCameraSettings();
            int action = motionEvent.getAction();
            Log.d(TAG, "onTouch: action = " + action);
            int focusLength = (mFocus.getWidth() + mFocus.getHeight()) / 2;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    float x = motionEvent.getRawX();
                    float y = motionEvent.getRawY();
                    if (focusLength == 0) {
                        mFocus.setX(x - (BaseConstants.DEFAULT_FOCUS_LENGTH / 2));
                        mFocus.setY(y - (BaseConstants.DEFAULT_FOCUS_LENGTH / 2));
                    } else {
                        mFocus.setX(x - (focusLength / 2));
                        mFocus.setY(y - (focusLength / 2));
                    }
                    mFocus.setVisibility(View.VISIBLE);
                    startTime = System.currentTimeMillis();
                    if (focusService == null) {
                        focusService = Util.getSingleExecutorService();
                    }
                    focusService.submit(focusTask);
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    /**
     * 隐藏正在显示的设置菜单
     */
    private void checkCameraSettings() {
        if (mCameraSettings.isShown()) {
            mCameraSettings.setVisibility(View.GONE);
        }
    }

    /**
     * 3s自动隐藏对焦图标
     */
    private Runnable focusTask = new Runnable() {

        @Override
        public synchronized void run() {
            long endTime = System.currentTimeMillis();
            while (true) {
                if ((endTime - startTime) >= BaseConstants.TIME_AUTO_GONE_FOCUS_ICON) {
                    mHandler.sendEmptyMessage(BaseConstants.Message.MSG_FOCUS_FINISH);
                    break;
                }
                endTime = System.currentTimeMillis();
            }
        }
    };


    private MyCamera.CaptureCallBack mCaptureCallBack = new MyCamera.CaptureCallBack() {
        @Override
        public void noCapture() {
            Toast.makeText(mContext, mContext.getResources().getString(R.string.take_picture_error), Toast.LENGTH_SHORT)
                    .show();
            setView(mCapture, R.drawable.ic_take_picture_gray);
            mCapture.setOnClickListener(null);
        }

        @Override
        public void captureSucceed(String picturePath) {
            Log.d(TAG, "captureSucceed: 拍照成功");
            setThumbnail(picturePath);
//            Toast.makeText(mContext, mContext.getResources().getText(R.string.take_picture_succeed), Toast.LENGTH_SHORT)
//                    .show();
        }

        @Override
        public void captureFailed() {
            Log.e(TAG, "captureFailed: 拍照失败");
            Toast.makeText(mContext, mContext.getResources().getString(R.string.take_picture_failed), Toast.LENGTH_SHORT)
                    .show();
        }

        @Override
        public void recordStarted() {
            Log.d(TAG, "recordStarted: 摄像成功");
            if (recordInfoService == null || recordInfoService.isShutdown()) {
                recordInfoService = Util.getSingleExecutorService();
            }
            recordFuture = recordInfoService.submit(new RecordTime());
            setCameraMode(CameraMode.MODE_STOP_RECORD);
        }

        @Override
        public void recordFailed() {
            Log.e(TAG, "recordFailed: 摄像失败");
        }

        @Override
        public void recordStopped(String videoPath) {
            Log.d(TAG, "recordStopped: 录像完成");
            mRecordStatus.clearAnimation();
            mRecordInfo.setVisibility(View.GONE);
            recordFuture.cancel(true);
            mHandler.removeCallbacksAndMessages(null);
            setView(mCapture, R.drawable.ic_start_record);
            setThumbnail(videoPath);
            setCameraMode(CameraMode.MODE_RECORD);
        }

        @Override
        public void timerCaptureSucceed() {
            setView(mCapture, R.drawable.ic_take_picture);
            mCapture.setOnClickListener(MainActivity.this);
        }

        @Override
        public void timeLapseSucceed() {
            Log.d(TAG, "timeLapseSucceed: do nothing");
        }
    };

    private SettingsItemAdapter.SettingsCallBack mSettingCallBack = new SettingsItemAdapter.SettingsCallBack() {
        @Override
        public void specCaptureModeCompleted(int mode) {
            if (mode == CaptureMode.MODE_NORMAL) {
                mSpecMode.setVisibility(View.GONE);
                mCamera.showWatermark(false);
                return;
            }
            switch (mode) {
                case CaptureMode.MODE_TIMER_CAPTURE:
                    mSpecMode.setVisibility(View.VISIBLE);
                    setView(mSpecMode, R.drawable.ic_capture_timer);
                    break;
                case CaptureMode.MODE_TIME_LAPSE:
                    mSpecMode.setVisibility(View.VISIBLE);
                    setView(mSpecMode, R.drawable.ic_timelapse);
                    break;
                case CaptureMode.MODE_DVR:
                    mSpecMode.setVisibility(View.VISIBLE);
                    setView(mSpecMode, R.drawable.ic_driving_recorder);
                    break;
                case CaptureMode.MODE_WATERMARK:
                    mCamera.showWatermark(true);
                    break;
                default:
                    break;
            }
        }
    };

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                // 更新时间
                case BaseConstants.Message.MSG_UPDATE_RECORD_TIME:
                    long time = (long) msg.obj;
                    if (mRecordInfo.getVisibility() == View.GONE) {
                        mRecordInfo.setVisibility(View.VISIBLE);
                    }
                    if (mRecordStatus.getAnimation() == null) {
                        AlphaAnimation animation = (AlphaAnimation) AnimationUtils.loadAnimation(mContext,
                                R.anim.alpha_record_status);
                        mRecordStatus.startAnimation(animation);
                    }
                    Log.d(TAG, "handleMessage: record time = " + time);
                    mRecordTime.setText(Util.formatTime(time));
                    break;
                case BaseConstants.Message.MSG_FOCUS_FINISH:
                    Log.d(TAG, "handleMessage: MSG_FOCUS_FINISH");
                    mFocus.setVisibility(View.GONE);
                    if (focusService != null) {
                        focusService.shutdownNow();
                        focusService = null;
                    }
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * 显示照片缩略图
     */
    private void showThumbnail() {
        String path = getThumbnailPath();
        if (path.equals(BaseConstants.UNKNOWN_THUMBNAIL_PATH)) {
            return;
        }
        Glide.with(mContext).load(path).into(mThumbnail);
    }

    /**
     * 将最后一张拍摄的照片保存为缩略图
     *
     * @param path 照片Path
     */
    private void setThumbnail(String path) {
        Glide.with(mContext).load(new File(path)).into(mThumbnail);
        Util.setStringSharePreference(mContext, BaseConstants.Key.KEY_LAST_CAPTURE_PATH, path);
    }

    /**
     * 获取缩略图位置信息
     *
     * @return path
     */
    private String getThumbnailPath() {
        String path = Util.getStringSharePreference(mContext, BaseConstants.Key.KEY_LAST_CAPTURE_PATH);
        if (path != null && path.equals(BaseConstants.DEFAULT_STRING_VALUE)) {
            return BaseConstants.UNKNOWN_THUMBNAIL_PATH;
        }
        return path;
    }

    private int getCameraMode() {
        int mode = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_CAMERA_MODE);
        if (mode == BaseConstants.DEFAULT_INT_VALUE) {
            return CameraMode.MODE_UNKNOWN;
        }
        return mode;
    }

    private int getFlashMode() {
        int mode = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_FLASH_MODE);
        if (mode == BaseConstants.DEFAULT_INT_VALUE) {
            return FlashMode.MODE_UNKNOWN;
        }
        return mode;
    }

    private void specTakePicture() {
        boolean timeLapse =
                getValueFromKey(BaseConstants.Key.KEY_TIME_LAPSE_MODE) != BaseConstants.FLAG_ITEM_SWITCH_OFF;
        if (timeLapse) {
            mCamera.startTimeLapse();
            Glide.with(mContext).load(R.drawable.ic_timelapse).into(mSpecMode);
        } else {
            mCamera.timerTakePicture();
            setView(mCapture, R.drawable.ic_take_picture_gray);
            Glide.with(mContext).load(R.drawable.ic_capture_timer).into(mSpecMode);
            mCapture.setOnClickListener(null);
        }
        mSpecMode.setVisibility(View.VISIBLE);
    }


    /**
     * 通过UI线程设置View
     *
     * @param view  需要设置的View
     * @param resId 图片ID
     */
    private void setView(final ImageView view, final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.setImageResource(resId);
            }
        });
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK) {
            if (isShowSettingsItem) {
                showCameraSettings(true);
                return true;
            }
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            System.exit(0);
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 计时线程
     */
    private class RecordTime implements Runnable {
        private long time;

        @Override
        public synchronized void run() {
            Log.d(TAG, "run: time = " + time);
            time++;
            mHandler.obtainMessage(BaseConstants.Message.MSG_UPDATE_RECORD_TIME, time).sendToTarget();
            mHandler.postDelayed(this, 1000);
        }
    }

}
