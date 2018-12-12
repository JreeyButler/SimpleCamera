package club.suansuanru.camera.constant;

import android.os.Environment;

/**
 * @author Yan.Liangliang
 * @date 2018/9/28
 * @email yanliang@jimi360.cn
 */
public class BaseConstants {
    public static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final int DEFAULT_FOCUS_LENGTH = 100;
    /**
     * 相机开启的超时等待时间
     */
    public static final long TIME_OUT_FOR_CAMERA_OPENING = 3000L;
    public static final long TIME_AUTO_GONE_FOCUS_ICON = 3000L;

    public static final String TYPE_PICTURE = ".jpg";
    public static final String TYPE_VIDEO = ".mp4";

    private static final String ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
    private static final String APPLICATION_DIR_NAME = "JCamera/";
    private static final String PICTURE_DIR_NAME = "Picture/";
    private static final String VIDEO_DIR_NAME = "Video/";
    public static final String APPLICATION_PATH = ROOT_PATH + APPLICATION_DIR_NAME;
    public static final String PICTURE_PATH = APPLICATION_PATH + PICTURE_DIR_NAME;
    public static final String VIDEO_PATH = APPLICATION_PATH + VIDEO_DIR_NAME;
    public static final String LAST_CAPTURE_PATH = "last_capture_path";
    public static final String BASE_SOCKET_NAME = "CameraH264";

    public static final int DEFAULT_VIDEO_BIT_RATE = 2 * 1000 * 1000;
    public static final int DEFAULT_VIDEO_FRAME_RATE = 30;
    public static final int FLAG_ITEM_SWITCH_OFF = 0;
    public static final int TIMER_CAPTURE_3S = 1;
    public static final int TIMER_CAPTURE_5S = 2;
    public static final int TIMER_CAPTURE_10S = 3;
    /**
     * 一分钟：60s
     */
    public static final int ONE_MINUTE_M = 60;
    /**
     * 两位数以下的时间单位为“短”时间单位，两位数以上的为“长”时间
     * eg:8s、5m、1h（短时间）;10s、11m、20h（长时间）
     */
    public static final int LONG_TOME_LENGTH = 2;

    public static final String UNKNOWN_THUMBNAIL_PATH = "unknown_thumbnail_path";
    public static final int DEFAULT_CAMERA_MODE = -1;
    public static final String DEFAULT_STRING_VALUE = "default_string_value";
    public static final int DEFAULT_INT_VALUE = -1;
    public static final int DEFAULT_BUFFER_SIZE = 1000 * 1000;

    /**
     * 拍摄模式：拍照、摄像、停止摄像
     */
    public static class CameraMode {
        public static final int MODE_UNKNOWN = -1;
        public static final int MODE_TAKE_PICTURE = 1;
        public static final int MODE_RECORD = 2;
        public static final int MODE_STOP_RECORD = 3;
    }

    public static class CaptureMode {
        public static final int MODE_NORMAL = 1;
        public static final int MODE_TIME_LAPSE = 2;
        public static final int MODE_TIMER_CAPTURE = 3;
        public static final int MODE_DVR = 4;
        public static final int MODE_WATERMARK = 5;
    }


    /**
     * 闪光模式：自动、打开、关闭
     */
    public static class FlashMode {
        public static final int MODE_UNKNOWN = -1;
        public static final int MODE_AUTO = 1;
        public static final int MODE_ON = 2;
        public static final int MODE_OFF = 3;
    }

    public static class Message {
        public static final int MSG_SAVE_PICTURE_ERROR = -1;
        public static final int MSG_SAVE_PICTURE_FAILED = 0;
        public static final int MSG_SAVE_PICTURE_SUCCEED = 1;
        public static final int MSG_UPDATE_RECORD_TIME = 2;
        public static final int MSG_FOCUS_FINISH = 3;
    }

    public static class Key {
        /**
         * 缩略图文件路径
         */
        public static final String KEY_LAST_CAPTURE_PATH = "key_last_capture_path";
        /**
         * 拍摄模式
         */
        public static final String KEY_CAMERA_MODE = "key_capture_mode";
        /**
         * 闪光灯
         */
        public static final String KEY_FLASH_MODE = "key_flash_mode";
        /**
         * 照片位置信息
         */
        public static final String KEY_RECORD_LOCATION = "key_record_location";
        /**
         * 照片水印
         */
        public static final String KEY_WATERMARK_MODE = "key_watermark_mode";
        /**
         * 记录仪模式
         */
        public static final String KEY_DVR_MODE = "key_dvr_mode";
        /**
         * 延时拍摄模式
         */
        public static final String KEY_TIME_LAPSE_MODE = "key_timelapse_mode";
        /**
         * 定时拍摄模式
         */
        public static final String KEY_TIMER_CAPTURE = "key_timer_capture";
        /**
         *
         */
        public static final String KEY_RECORD_MODE = "key_record_mode";
    }

    public static class DataType {
        public static final String TYPE_VIDEO = "video/*";
    }

    public static class SettingsFlag {
        public static final int FLAG_LOCATION = 0;
        public static final int FLAG_TIMER_CAPTURE = 1;
        public static final int FLAG_TIME_LAPSE = 2;
        public static final int FLAG_DVR = 3;
        public static final int FLAG_WATERMARK = 4;
    }

}
