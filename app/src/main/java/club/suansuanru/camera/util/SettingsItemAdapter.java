package club.suansuanru.camera.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;

import club.suansuanru.camera.R;
import club.suansuanru.camera.constant.BaseConstants;
import club.suansuanru.camera.constant.BaseConstants.CaptureMode;
import club.suansuanru.camera.constant.BaseConstants.SettingsFlag;

/**
 * @author Yan.Liangliang
 * @date 2018/10/15
 * @email yanliang@jimi360.cn
 */
public class SettingsItemAdapter extends BaseAdapter implements View.OnClickListener {
    private final String TAG = "SettingsItemAdapter";

    private Context mContext;
    private List<SettingsItem> mItems;
    private Handler mHandler;
    private ViewHolder holder;
    private SettingsCallBack mCallBack;

    public SettingsItemAdapter(Context mContext) {
        this.mContext = mContext;
        this.holder = new ViewHolder();
        initSettingsItemData();
    }

    @Override
    public int getCount() {
        if (mItems == null) {
            return 0;
        }
        return mItems.size();
    }

    @Override
    public Object getItem(int i) {
        return i;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @SuppressLint("InflateParams")
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.view_settings_item, null);
        }
        holder.mItemIcon = view.findViewById(R.id.settings_icon);
        holder.mItemName = view.findViewById(R.id.settings_title);
        holder.mItemValue = view.findViewById(R.id.settings_value);
        holder.mItemMore = view.findViewById(R.id.settings_more);
        Glide.with(mContext).load(mItems.get(i).getIcon()).into(holder.mItemIcon);
        String name = mItems.get(i).getName();
        holder.mItemName.setText(name);
        holder.mItemValue.setText(getEntryFromValue(i, getValueFromTag(i)));
        view.setTag(i);
        view.setOnClickListener(this);
        checkSettingsItem(view);
        return view;
    }

    /**
     * 检查该设置项是否可用
     *
     * @param view 设置项View
     */
    @SuppressLint("ResourceAsColor")
    private void checkSettingsItem(View view) {
        int captureMode = getCaptureMode();
        int tag = (int) view.getTag();
        // 拍照模式
        boolean pictureMode = captureMode == BaseConstants.CameraMode.MODE_TAKE_PICTURE;
        // 记录仪模式
        boolean dvrFlag = tag == SettingsFlag.FLAG_DVR;
        // 延时摄像与定时拍摄功能是会相互影响的两个功能，所以在拍照模式下，它们当中只有一个的功能是可以处于使用状态
        // 延时开、定时不可用；定时开、延时不可用
        boolean sameFunc =
                (tag == SettingsFlag.FLAG_TIME_LAPSE
                        && getValueFromTag(SettingsFlag.FLAG_TIMER_CAPTURE) != BaseConstants.FLAG_ITEM_SWITCH_OFF)
                        ||
                        (tag == SettingsFlag.FLAG_TIMER_CAPTURE
                                && getValueFromTag(SettingsFlag.FLAG_TIME_LAPSE) != BaseConstants.FLAG_ITEM_SWITCH_OFF);
        boolean banItem = (pictureMode && dvrFlag) || (!pictureMode && !dvrFlag) || sameFunc;
        Log.d(TAG, "checkSettingsItem: banItem = " + banItem + " tag = " + tag);
        if (banItem) {
            Glide.with(mContext)
                    .load(Util.itemIconsGray[tag])
                    .into((ImageView) view.findViewById(R.id.settings_icon));
            ((TextView) view.findViewById(R.id.settings_title))
                    .setTextColor(mContext.getResources().getColorStateList(R.color.un_useful));
            Glide.with(mContext)
                    .load(R.drawable.ic_settings_more_gray)
                    .into((ImageView) view.findViewById(R.id.settings_more));
            view.setEnabled(false);
        } else {
            Glide.with(mContext)
                    .load(Util.itemIconsBlack[tag])
                    .into((ImageView) view.findViewById(R.id.settings_icon));
            ((TextView) view.findViewById(R.id.settings_title))
                    .setTextColor(mContext.getResources().getColorStateList(android.R.color.black));
            Glide.with(mContext)
                    .load(R.drawable.ic_settings_more)
                    .into((ImageView) view.findViewById(R.id.settings_more));
            view.setEnabled(true);
        }
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick: ");
        int tag = (int) view.getTag();
        int value = getValueFromTag(tag);
        changeValue(tag, value);
    }

    /**
     * 切换设置项
     *
     * @param tag   设置项
     * @param value 实际值
     */
    private void changeValue(int tag, int value) {
        String[] entry = getEntry(tag);
        if (value < entry.length - 1) {
            value += 1;

        } else {
            value = BaseConstants.FLAG_ITEM_SWITCH_OFF;
        }
        setValue(tag, value);
        showModeFlag(tag, value);
    }

    private void showModeFlag(int tag, int value) {
        int captureMode = CaptureMode.MODE_NORMAL;
        if (value != BaseConstants.FLAG_ITEM_SWITCH_OFF) {
            switch (tag) {
                case SettingsFlag.FLAG_LOCATION:
                    break;
                case SettingsFlag.FLAG_TIME_LAPSE:
                    captureMode = CaptureMode.MODE_TIME_LAPSE;
                    break;
                case SettingsFlag.FLAG_TIMER_CAPTURE:
                    captureMode = CaptureMode.MODE_TIMER_CAPTURE;
                    break;
                case SettingsFlag.FLAG_DVR:
                    captureMode = CaptureMode.MODE_DVR;
                    break;
                case SettingsFlag.FLAG_WATERMARK:
                    captureMode = CaptureMode.MODE_WATERMARK;
                    break;
                default:
                    break;
            }
        }
        mCallBack.specCaptureModeCompleted(captureMode);
    }

    /**
     * 获取展示结果
     *
     * @param tag   设置项
     * @param value 实际值
     * @return 展示的值
     */
    private String getEntryFromValue(int tag, int value) {
        String[] entry = mContext.getResources().getStringArray(R.array.base_entry);
        if (tag == SettingsFlag.FLAG_TIMER_CAPTURE) {
            entry = mContext.getResources().getStringArray(R.array.timer_capture_entry);
        }
        return entry[value];
    }

    /**
     * 获取指定设置项的所有展示值
     *
     * @param tag 设置项
     * @return 所有的展示值
     */
    private String[] getEntry(int tag) {
        String[] entry = mContext.getResources().getStringArray(R.array.base_entry);
        if (tag == SettingsFlag.FLAG_TIMER_CAPTURE) {
            entry = mContext.getResources().getStringArray(R.array.timer_capture_entry);
        }
        return entry;
    }

    /**
     * 获取指定设置项的实际值
     *
     * @param tag 设置项
     * @return 实际值
     */
    private int getValueFromTag(int tag) {
        int value = BaseConstants.FLAG_ITEM_SWITCH_OFF;
        switch (tag) {
            case SettingsFlag.FLAG_LOCATION:
                value = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_RECORD_LOCATION);
                break;
            case SettingsFlag.FLAG_TIMER_CAPTURE:
                value = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_TIMER_CAPTURE);
                break;
            case SettingsFlag.FLAG_TIME_LAPSE:
                value = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_TIME_LAPSE_MODE);
                break;
            case SettingsFlag.FLAG_DVR:
                value = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_DVR_MODE);
                break;
            case SettingsFlag.FLAG_WATERMARK:
                value = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_WATERMARK_MODE);
                break;
            default:
                break;
        }
        if (value == BaseConstants.DEFAULT_INT_VALUE) {
            value = BaseConstants.FLAG_ITEM_SWITCH_OFF;
        }
        return value;
    }

    /**
     * 保存设置项的设置数据
     *
     * @param tag   设置项编号
     * @param value 设置项结果
     */
    private void setValue(int tag, int value) {
        mItems.get(tag).setValue(getEntryFromValue(tag, value));
        switch (tag) {
            case SettingsFlag.FLAG_LOCATION:
                Util.setIntSharePreference(mContext, BaseConstants.Key.KEY_RECORD_LOCATION, value);
                break;
            case SettingsFlag.FLAG_TIMER_CAPTURE:
                Util.setIntSharePreference(mContext, BaseConstants.Key.KEY_TIMER_CAPTURE, value);
                break;
            case SettingsFlag.FLAG_TIME_LAPSE:
                Util.setIntSharePreference(mContext, BaseConstants.Key.KEY_TIME_LAPSE_MODE, value);
                break;
            case SettingsFlag.FLAG_DVR:
                Util.setIntSharePreference(mContext, BaseConstants.Key.KEY_DVR_MODE, value);
                break;
            case SettingsFlag.FLAG_WATERMARK:
                Util.setIntSharePreference(mContext, BaseConstants.Key.KEY_WATERMARK_MODE, value);
                break;
            default:
                break;
        }
        notifyDataSetChanged();
    }

    /**
     * 初始化设置模块数据
     */
    private void initSettingsItemData() {
        if (mItems == null) {
            mItems = new ArrayList<>();
        }
        String[] itemNames = mContext.getResources().getStringArray(R.array.camera_settings_names);

        for (int i = 0; i < itemNames.length; i++) {
            SettingsItem item = new SettingsItem();
            String name = itemNames[i];
            int icon = Util.itemIconsBlack[i];
            item.setIcon(icon);
            item.setName(name);
            mItems.add(item);
        }
    }

    /**
     * 获取Camera拍摄模式
     *
     * @return 拍摄模式
     */
    private int getCaptureMode() {
        int mode = Util.getIntSharePreference(mContext, BaseConstants.Key.KEY_CAMERA_MODE);
        if (mode == BaseConstants.DEFAULT_INT_VALUE) {
            return BaseConstants.CameraMode.MODE_UNKNOWN;
        }
        return mode;
    }

    private class ViewHolder {
        ImageView mItemIcon;
        TextView mItemName;
        TextView mItemValue;
        ImageView mItemMore;
    }

    public void setCallBack(SettingsCallBack mCallBack) {
        this.mCallBack = mCallBack;
    }

    public interface SettingsCallBack {

        /**
         * 设置特殊拍摄模式标识
         *
         * @param mode 特殊模式
         */
        void specCaptureModeCompleted(int mode);
    }

}
