package club.suansuanru.camera.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.Surface;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import club.suansuanru.camera.R;
import club.suansuanru.camera.constant.BaseConstants;

/**
 * @author Yan.Liangliang
 * @date 2018/9/28
 * @email yanliang@jimi360.cn
 */
public class Util {
    static int[] itemIconsBlack = {
            R.drawable.ic_location_black,
            R.drawable.ic_capture_timer_black,
            R.drawable.ic_timelapse_black,
            R.drawable.ic_driving_recorder_black,
            R.drawable.ic_watermark_black,
    };
    static int[] itemIconsGray = {
            R.drawable.ic_location_gray,
            R.drawable.ic_capture_timer_gray,
            R.drawable.ic_timelapse_gray,
            R.drawable.ic_driving_recorder_gray,
            R.drawable.ic_watermark_gray,
    };

    /**
     * 检查权限
     *
     * @param mContext    上下文
     * @param permissions 权限列表
     * @return
     */
    public static boolean hasPermissionGrated(Context mContext, String[] permissions) {
        for (String permission : permissions) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                int hasPermission = mContext.checkSelfPermission(permission);
                if (hasPermission != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 生成线程池为2的线程池，核心线程为1的线程池
     *
     * @return 单线程池
     */
    public static ExecutorService getSingleExecutorService() {
        return getExecutorService(1, 2);
    }

    public static ExecutorService getExecutorService(int corePoolSize, int maximumPoolSize) {
        ThreadFactory factory = new ThreadFactoryBuilder()
                .setNameFormat("single-pool-%d")
                .build();
        return new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<Runnable>(1024),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 格式化录像时间（单位：秒s）
     *
     * @param time 已经录像的时间
     * @return 格式化之后的时间 eg: 00:10
     */
    public static String formatTime(long time) {
        String minute, second;
        if (time < BaseConstants.ONE_MINUTE_M) {
            minute = "00";
            second = String.valueOf(time);
        } else {
            minute = String.valueOf(time / BaseConstants.ONE_MINUTE_M);
            second = String.valueOf(time % BaseConstants.ONE_MINUTE_M);
        }
        if (minute.length() < BaseConstants.LONG_TOME_LENGTH) {
            minute = "0" + minute;
        }
        if (second.length() < BaseConstants.LONG_TOME_LENGTH) {
            second = "0" + second;
        }
        return minute + ":" + second;
    }

    /**
     * 获取设备显示方位(逆时针)
     *
     * @param orientation 旋转角度 （1~359）
     * @return 方位
     */
    public static int getScreenRotation(int orientation) {
        final boolean rotation0 = 315 < orientation && orientation <= 359
                || (orientation > 0) && (orientation <= 45);
        final boolean orientation90 = 225 < orientation && orientation <= 315;
        final boolean orientation180 = 135 < orientation && orientation <= 225;
        final boolean orientation270 = 45 < orientation && orientation <= 135;

        if (rotation0) {
            return Surface.ROTATION_0;
        }
        if (orientation90) {
            return Surface.ROTATION_90;
        }
        if (orientation180) {
            return Surface.ROTATION_180;
        }
        if (orientation270) {
            return Surface.ROTATION_270;
        }
        return Surface.ROTATION_0;
    }


    public static void setStringSharePreference(Context mContext, String key, String value) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public static String getStringSharePreference(Context mContext, String key) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE);
        return sharedPreferences.getString(key, BaseConstants.DEFAULT_STRING_VALUE);
    }

    public static void setIntSharePreference(Context mContext, String key, int value) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public static int getIntSharePreference(Context mContext, String key) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE);
        return sharedPreferences.getInt(key, BaseConstants.DEFAULT_INT_VALUE);
    }
}
