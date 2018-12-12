package club.suansuanru.camera.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import club.suansuanru.camera.R;
import club.suansuanru.camera.constant.BaseConstants;
import club.suansuanru.camera.util.Util;

/**
 * @author Yan.Liangliang
 * @date 2018/10/9
 * @email yanliang@jimi360.cn
 */
public class GalleryActivity extends Activity implements View.OnClickListener {
    private final String TAG = "GalleryActivity";
    private Context mContext;
    private ImageView lastCapture, mPlayVideo;
    private OrientationEventListener mOrEventListener;
    private int screenRotation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        mContext = this;
        initView();
    }

    private void initView() {
        lastCapture = findViewById(R.id.last_capture);
        mPlayVideo = findViewById(R.id.play_video);

        mPlayVideo.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        String path = getLastCapturePath();
        Log.d(TAG, "onResume: path =" + path);
        if (path.endsWith(BaseConstants.TYPE_VIDEO)) {
            mPlayVideo.setVisibility(View.VISIBLE);
        }
        Glide.with(mContext).load(path).into(lastCapture);

        initOrientation();
    }

    private String getLastCapturePath() {
        return Util.getStringSharePreference(mContext, BaseConstants.Key.KEY_LAST_CAPTURE_PATH);
    }

    private void initOrientation() {
        mOrEventListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int i) {
                int rotation = Util.getScreenRotation(i);
                if (rotation == screenRotation) {
                    return;
                }
                switch (rotation) {
                    case Surface.ROTATION_0:
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        lastCapture.setRotation(0);
                        mPlayVideo.setRotation(0);
                        break;
                    case Surface.ROTATION_90:
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        lastCapture.setRotation(0);
                        mPlayVideo.setRotation(0);
                        break;
                    case Surface.ROTATION_180:
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        lastCapture.setRotation(180);
                        mPlayVideo.setRotation(180);
                        break;
                    case Surface.ROTATION_270:
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        lastCapture.setRotation(180);
                        mPlayVideo.setRotation(180);
                        break;
                    default:
                        break;
                }
                Glide.with(mContext).load(getLastCapturePath()).into(lastCapture);
                screenRotation = rotation;
            }
        };
        mOrEventListener.enable();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mOrEventListener.disable();
        mOrEventListener = null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.play_video:
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                Uri uri = Uri.parse(getLastCapturePath());
                intent.setDataAndType(uri, BaseConstants.DataType.TYPE_VIDEO);
                mContext.startActivity(intent);
                break;
            default:
                break;
        }
    }
}
