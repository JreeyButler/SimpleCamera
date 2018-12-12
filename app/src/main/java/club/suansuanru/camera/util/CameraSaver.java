package club.suansuanru.camera.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.Image;
import android.os.Handler;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import club.suansuanru.camera.constant.BaseConstants;


/**
 * @author Yan.Liangliang
 * @date 2018/9/28
 * @email yanliang@jimi360.cn
 */
public class CameraSaver implements Runnable {
    private String TAG = "CameraSaver";
    private Image image;
    private Handler mHandler;
    private boolean frontCamera, verticalCapture;
    private String mTimeLapseDirName;
    private Bitmap watermark;

    private CameraSaver(Builder builder) {
        this.image = builder.image;
        this.mHandler = builder.handler;
        this.watermark = builder.waterMark;
    }


    @Override
    public void run() {
        Log.d(TAG, "run: ");
        final byte[] picture = imageToBytes(image);
        if (picture.length == 0) {
            mHandler.obtainMessage(BaseConstants.Message.MSG_SAVE_PICTURE_FAILED)
                    .sendToTarget();
        }
        if (frontCamera) {
            Util.getSingleExecutorService()
                    .submit(new Runnable() {
                        @Override
                        public void run() {
                            mirrorPicture(picture);
                        }
                    });

            return;
        }
        if (watermark != null) {
            Util.getSingleExecutorService()
                    .submit(new Runnable() {
                        @Override
                        public void run() {
                            addWatermarkToPicture(picture);
                        }
                    });
            return;
        }
        savePicture(verticalCapture ? rotateBitmap(picture) : picture);
    }


    private void savePicture(byte[] picture) {
        Log.d(TAG, "savePicture: ");
        long now = System.currentTimeMillis();
        String path = BaseConstants.PICTURE_PATH + now + BaseConstants.TYPE_PICTURE;
        if (mTimeLapseDirName != null && !"".equals(mTimeLapseDirName)) {
            path = BaseConstants.PICTURE_PATH + mTimeLapseDirName + now + BaseConstants.TYPE_PICTURE;
        }
        createPictureFile(path);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path);
            fos.write(picture);
        } catch (IOException e) {
            e.printStackTrace();
            mHandler.obtainMessage(BaseConstants.Message.MSG_SAVE_PICTURE_ERROR)
                    .sendToTarget();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (image != null) {
                image.close();
            }
        }
        mHandler.obtainMessage(BaseConstants.Message.MSG_SAVE_PICTURE_SUCCEED, path)
                .sendToTarget();
    }

    /**
     * 图片照片添加水印
     *
     * @param picture 未添加水印的照片
     */
    private void addWatermarkToPicture(byte[] picture) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length)
                .copy(Bitmap.Config.RGB_565, true);
        if (verticalCapture) {
            bitmap = rotateBitmap(bitmap);
        }
        Canvas canvas = new Canvas(bitmap);
        int left = bitmap.getWidth() / 25;
        int top = bitmap.getHeight() - watermark.getHeight() - (bitmap.getHeight() / 15);
        canvas.drawBitmap(watermark, left, top, null);
        mCallBack.addWatermarkSuccess(bitmap);
    }

    /**
     * 图片照片添加水印
     *
     * @param bitmap 未添加水印的照片
     */
    private void addWatermarkToPicture(Bitmap bitmap) {
        if (verticalCapture) {
            bitmap = rotateBitmap(bitmap);
        }
        Canvas canvas = new Canvas(bitmap);
        int left = bitmap.getWidth() / 25;
        int top = bitmap.getHeight() - watermark.getHeight() - (bitmap.getHeight() / 15);
        canvas.drawBitmap(watermark, left, top, null);
        mCallBack.addWatermarkSuccess(bitmap);
    }

    /**
     * 将竖直拍摄的图片旋转90度
     *
     * @param path 图片路径
     */
    private void setPictureDegreeZero(String path) {
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            // 修正图片的旋转角度，设置其不旋转。
            // 例如旋转90度，传值ExifInterface.ORIENTATION_ROTATE_90，需要将这个值转换为String类型的
            exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                    String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
            exifInterface.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] rotateBitmap(byte[] picture) {
        Log.d(TAG, "rotateBitmap: ");
        Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length)
                .copy(Bitmap.Config.ARGB_8888, true);
        Matrix matrix = new Matrix();
        if (frontCamera) {
            matrix.postRotate(270);
        } else {
            matrix.postRotate(90);
        }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return bitmapToBytes(bitmap);
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {
        Log.d(TAG, "rotateBitmap: ");
        Matrix matrix = new Matrix();
        if (frontCamera) {
            matrix.postRotate(270);
        } else {
            matrix.postRotate(90);
        }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        verticalCapture = false;
        return bitmap;
    }

    private void createPictureFile(String path) {
        File pictureFile = new File(path);
        if (!pictureFile.exists()) {
            boolean isSuccess = false;
            try {
                isSuccess = pictureFile.createNewFile();
            } catch (IOException e) {
                mHandler.obtainMessage(BaseConstants.Message.MSG_SAVE_PICTURE_ERROR);
                e.printStackTrace();
            }
            if (!isSuccess) {
                mHandler.obtainMessage(BaseConstants.Message.MSG_SAVE_PICTURE_FAILED);
            }
        }
    }

    private byte[] imageToBytes(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Log.d(TAG, "imageToBytes: verticalCapture = " + verticalCapture);
        return bytes;
    }

    private byte[] bitmapToBytes(Bitmap bitmap) {
        long now = System.currentTimeMillis();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
        Log.d(TAG, "bitmapToBytes: time = " + (System.currentTimeMillis() - now));
        return outputStream.toByteArray();
    }

    /**
     * 如果是前置摄像头拍摄的图片，默认旋转180°
     */
    private void mirrorPicture(byte[] picture) {
        Log.d(TAG, "mirrorPicture: ");
        // 翻转
        Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length)
                .copy(Bitmap.Config.ARGB_8888, true);
        if (verticalCapture) {
            bitmap = rotateBitmap(bitmap);
        }
        Matrix matrix = new Matrix();
        matrix.setScale(-1, 1);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        mCallBack.mirrorPictureSuccess(bitmap);
    }

    public void setVerticalCapture(boolean verticalCapture) {
        this.verticalCapture = verticalCapture;
    }

    public void setFrontCamera(boolean frontCamera) {
        this.frontCamera = frontCamera;
    }

    public void setTimeLapseDirName(String mTimeLapseDirName) {
        this.mTimeLapseDirName = mTimeLapseDirName;
    }

    private CallBack mCallBack = new CallBack() {
        @Override
        public void mirrorPictureSuccess(Bitmap bitmap) {
            Log.d(TAG, "mirrorPictureSuccess: watermark is " +
                    (watermark == null ? "null" : "not null"));
            if (watermark != null) {
                addWatermarkToPicture(bitmap);
                return;
            }
            savePicture(bitmapToBytes(bitmap));
        }

        @Override
        public void addWatermarkSuccess(Bitmap bitmap) {
            savePicture(bitmapToBytes(bitmap));
        }
    };

    private interface CallBack {
        /**
         * 镜像图片成功
         *
         * @param bitmap 处理后的照片
         */
        void mirrorPictureSuccess(Bitmap bitmap);

        /**
         * 添加水印成功
         *
         * @param bitmap 处理后的照片
         */
        void addWatermarkSuccess(Bitmap bitmap);
    }

    public static class Builder {
        private Image image;
        private Handler handler;
        private Bitmap waterMark;

        public Builder image(Image image) {
            this.image = image;
            return this;
        }

        public Builder handler(Handler handler) {
            this.handler = handler;
            return this;
        }

        public Builder watermark(Bitmap waterMark) {
            this.waterMark = waterMark;
            return this;
        }

        public CameraSaver build() {
            return new CameraSaver(this);
        }
    }
}
