package org.dync.ijkplayerlib.widget.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.FloatRange;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.dync.ijkplayerlib.R;
import org.dync.ijkplayerlib.widget.media.IRenderView;
import org.dync.ijkplayerlib.widget.media.IjkVideoView;

import java.util.Locale;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * Created by KathLine on 2017/8/22.
 */
public class PlayerController {

    /**
     * 打印日志的TAG
     */
    private static final String TAG = PlayerController.class.getSimpleName();
    /**
     * 全局上下文
     */
    private final Context mContext;
    /**
     * 依附的容器Activity
     */
    private final Activity mActivity;
    /**
     * 播放器的父布局
     */
    private View videoParentLayout;
    /**
     * 播放器的父布局的显示比例
     */
    private int aspectRatio;
    /**
     * 原生的Ijkplayer
     */
    private IjkVideoView mVideoView;

    private SeekBar videoController;

    /**
     * 当前播放位置
     */
    private int currentPosition;
    /**
     * 滑动进度条得到的新位置，和当前播放位置是有区别的,newPosition =0也会调用设置的，故初始化值为-1
     */
    private long newPosition = -1;
    /**
     * 视频旋转的角度，默认只有0,90.270分别对应向上、向左、向右三个方向
     */
    private int rotation = 0;
    /**
     * 视频显示比例,默认保持原视频的大小
     */
    private int currentShowType = IRenderView.AR_ASPECT_FIT_PARENT;
    /**
     * 播放总时长
     */
    private long duration;
    /**
     * 当前声音大小
     */
    private int volume;
    /**
     * 设备最大音量
     */
    private int mMaxVolume;
    /**
     * 当前亮度大小
     */
    private float brightness;
    /**
     * seekBar的最大值
     */
    private int seekBarMaxProgress = 100;
    /**
     * 记录进行后台时的播放状态0为播放，1为暂停
     */
    private int bgState;
    /**
     * 是否显示控制面板，默认为隐藏，true为显示false为隐藏
     */
    private boolean isShowControlPanel;
    /**
     * 禁止触摸，默认可以触摸，true为禁止false为可触摸
     */
    private boolean isForbidTouch;
    /**
     * 禁止收起控制面板，默认可以收起，true为禁止false为可触摸
     */
    private boolean isForbidHideControlPanel;
    /**
     * 是否在拖动进度条中，默认为停止拖动，true为在拖动中，false为停止拖动
     */
    private boolean isDragging;
    /**
     * 控制面板隐藏时是否更新进度，true更新，false不更新
     */
    private boolean isUpdateHide = true;
    /**
     * 播放的时候是否需要网络提示，默认显示网络提示，true为显示网络提示，false不显示网络提示
     */
    private boolean isGNetWork = true;
    protected boolean wifiReceiverSuccess;
    public static boolean WIFI_TIP_DIALOG_SHOWED = false;
    public BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                boolean isWifi = isWifiConnected(context);
                if (!isWifi && !WIFI_TIP_DIALOG_SHOWED && mVideoView.isPlaying()) {
                    if (playStateListener != null) {
                        playStateListener.playState(IjkVideoView.STATE_PLAYING);
                    }
                    if (!Utils.isWifiConnected(mActivity) && !WIFI_TIP_DIALOG_SHOWED) {
                        mVideoView.clickStart();
                        if (onNetWorkListener != null) {
                            onNetWorkListener.onChanged();
                        }
                        showWifiDialog();
                    }
                }
            }
        }
    };

    /**
     * 这里会回调{@link #setNetWorkListener(OnNetWorkListener)}
     */
    public void showWifiDialog() {
        if (!isGNetWork) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(mContext.getResources().getString(R.string.tips_not_wifi));
        builder.setPositiveButton(mContext.getResources().getString(R.string.tips_not_wifi_confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                WIFI_TIP_DIALOG_SHOWED = true;
                if (mVideoView.getCurrentState() == IjkVideoView.STATE_PAUSED) {
                    mVideoView.start();
                } else {
                    mVideoView.clickStart();
                }
                if (onNetWorkListener != null) {
                    onNetWorkListener.onChanged();
                }
            }
        });
        builder.setNegativeButton(mContext.getResources().getString(R.string.tips_not_wifi_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mVideoView.release(true);
                if (onNetWorkListener != null) {
                    onNetWorkListener.onChanged();
                }
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                dialog.dismiss();
                mVideoView.release(true);
                if (onNetWorkListener != null) {
                    onNetWorkListener.onChanged();
                }
            }
        });

        builder.create().show();
    }

    /**
     * This method requires the caller to hold the permission ACCESS_NETWORK_STATE.
     *
     * @param context context
     * @return if wifi is connected,return true
     */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public void registerWifiListener(Context context) {
        if (context == null) return;
        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(wifiReceiver, intentFilter);
        wifiReceiverSuccess = true;
    }

    public void unregisterWifiListener(Context context) {
        if (context == null) return;
        try {
            if (wifiReceiverSuccess) {
                context.unregisterReceiver(wifiReceiver);
                wifiReceiverSuccess = false;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private GestureDetector gestureDetector;
    /**
     * 设置最大播放时间，时间不做限制时设置为-1
     */
    private int maxPlaytime = -1;
    /**
     * 是否只有全屏，默认非全屏，true为全屏，false为非全屏
     */
    private boolean isOnlyFullScreen;
    /**
     * 是否禁止双击，默认不禁止，true为禁止，false为不禁止
     */
    private boolean isForbidDoulbeUp;
    /**
     * 是否是竖屏，默认为竖屏，true为竖屏，false为横屏
     */
    private boolean isPortrait = true;
    /**
     * 音频管理器
     */
    private AudioManager audioManager;
    /**
     * 同步进度
     */
    private static final int MESSAGE_SHOW_PROGRESS = 1;
    /**
     * 设置新位置
     */
    private static final int MESSAGE_SEEK_NEW_POSITION = 3;
    /**
     * 隐藏提示的box
     */
    private static final int MESSAGE_HIDE_CENTER_BOX = 4;


    /**
     * 消息处理
     */
    @SuppressWarnings("HandlerLeak")
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                /**滑动完成，隐藏滑动提示的box*/
                case MESSAGE_HIDE_CENTER_BOX:

                    break;
                /**滑动完成，设置播放进度*/
                case MESSAGE_SEEK_NEW_POSITION:
                    if (mVideoView != null && newPosition >= 0) {
                        mVideoView.seekTo((int) newPosition);
                        newPosition = -1;
                    }
                    break;
                /**滑动中，同步播放进度*/
                case MESSAGE_SHOW_PROGRESS:
                    long pos = syncProgress();
                    if (!isDragging && isUpdateHide) {
                        msg = obtainMessage(MESSAGE_SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
            }
        }
    };

    /**========================================视频的监听方法==============================================*/

    /**
     * 控制面板收起或者显示的轮询监听
     */
    private AutoControlPanelRunnable mAutoControlPanelRunnable;
    /**
     * Activity界面方向监听
     */
    private OrientationEventListener orientationEventListener;

    /**
     * 进度条滑动监听
     */
    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        private boolean isMaxTime;

        /**数值的改变*/
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) {
                /**不是用户拖动的，自动播放滑动的情况*/
                return;
            } else {
                if (getDuration() < 1) {
                    videoController.setEnabled(false);
                    return;
                } else {
                    videoController.setEnabled(true);
                }
                long duration = getDuration();
                int position = (int) ((duration * progress * 1.0) / 1000);
                newPosition = position;
                String time = generateTime(position);
                //                Log.d(TAG, "onProgressChanged: time= " + time + ", progress= " + progress);
                if (maxPlaytime != -1 && maxPlaytime + 1000 < position) {
                    //                    Log.d(TAG, "onProgressChanged: -------------");
                    isMaxTime = true;
                    long pos = seekBarMaxProgress * maxPlaytime / duration;
                    seekBar.setProgress((int) pos);
                    mHandler.sendEmptyMessage(MESSAGE_SHOW_PROGRESS);
                } else {
                    isMaxTime = false;
                }
            }

        }

        /**开始拖动*/
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (getDuration() < 1) {
                return;
            }
            isDragging = true;
            mHandler.removeMessages(MESSAGE_SHOW_PROGRESS);
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.stop();
            }
        }

        /**停止拖动*/
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (getDuration() < 1) {
                return;
            }
            if (mVideoView != null) {
                long duration = getDuration();
                newPosition = (long) ((duration * seekBar.getProgress() * 1.0) / seekBarMaxProgress);
                //                mVideoView.seekTo((int) ((duration * seekBar.getProgress() * 1.0) / 1000));
                //                mHandler.removeMessages(MESSAGE_SHOW_PROGRESS);
            }
            isDragging = false;
            if (!isMaxTime && newPosition >= 0) {
                mHandler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
                mHandler.sendEmptyMessage(MESSAGE_SEEK_NEW_POSITION);
            }
            mHandler.sendEmptyMessageDelayed(MESSAGE_SHOW_PROGRESS, 1000);
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.start(AutoControlPanelRunnable.AUTO_INTERVAL);
            }
        }
    };

    /**
     * 切换播放器
     *
     * @param playerType
     * @return
     */
    public PlayerController switchPlayer(int playerType) {
        mVideoView.switchPlayer(playerType);
        return this;
    }

    public void startRecord() {
        mVideoView.getMediaPlayer().startRecord();
    }

    public void stopRecord() {
        mVideoView.getMediaPlayer().stopRecord();
    }

    /**
     * 百分比显示切换
     */
    public PlayerController toggleAspectRatio() {
        if (mVideoView != null) {
            mVideoView.toggleAspectRatio();
        }
        return this;
    }

    /**
     * 参考{@link IRenderView#AR_ASPECT_FIT_PARENT}、{@link IRenderView#AR_ASPECT_FILL_PARENT}、{@link IRenderView#AR_ASPECT_WRAP_CONTENT}
     * {@link IRenderView#AR_16_9_FIT_PARENT}、{@link IRenderView#AR_4_3_FIT_PARENT}
     * 设置播放视频的宽高比
     *
     * @param showType 视频显示比例，如果该参数不在s_allAspectRatio数组范围内，则默认使用IRenderView.AR_ASPECT_FILL_PARENTIRenderView.AR_ASPECT_FILL_PARENT值
     */
    public PlayerController setVideoRatio(int showType) {
        currentShowType = showType;
        if (mVideoView != null) {
            mVideoView.setAspectRatio(currentShowType);
        }
        return this;
    }

    public final int ROTATION_0 = 0;
    public final int ROTATION_90 = 90;
    public final int ROTATION_180 = 180;
    public final int ROTATION_270 = 270;
    public final int[] rotations = {
            ROTATION_0,
            ROTATION_90,
            ROTATION_180,
            ROTATION_270};
    private int mCurrentRotationIndex = 0;
    private int mCurrentRotation = rotations[0];

    public PlayerController toogleVideoRotation() {
        mCurrentRotationIndex++;
        mCurrentRotationIndex %= rotations.length;

        mCurrentRotation = rotations[mCurrentRotationIndex];
        setPlayerRotation(mCurrentRotation);
        return this;
    }

    /**
     * 旋转指定角度
     *
     * @param rotation 参数可以设置 {@link #rotations}里面的值
     * @return
     */
    public PlayerController setPlayerRotation(int rotation) {

        if (mVideoView != null) {
            mVideoView.setPlayerRotation(rotation);
        }
        return this;
    }

    /**
     * 参考{@link IRenderView#AR_ASPECT_FIT_PARENT}、{@link IRenderView#AR_ASPECT_FILL_PARENT}、{@link IRenderView#AR_ASPECT_WRAP_CONTENT}
     * {@link IRenderView#AR_16_9_FIT_PARENT}、{@link IRenderView#AR_4_3_FIT_PARENT}
     * 设置播放视频父布局的宽高比
     *
     * @param aspectRatio
     * @return
     */
    public PlayerController setVideoParentRatio(int aspectRatio) {
        this.aspectRatio = aspectRatio;
        return this;
    }

    /**
     * 设置一开始显示方向，true：竖屏  false：横屏
     *
     * @param isPortrait
     * @return
     */
    public PlayerController setPortrait(boolean isPortrait) {
        if (isPortrait) {
            onConfigurationPortrait();
        } else {
            onConfigurationLandScape();
        }
        return this;
    }

    /**
     * 设置播放速率，这里仅对支持IjkMediaPlayer播放器，
     * 初始放置玩位置推荐在{@link IjkVideoView#setOnPreparedListener(IMediaPlayer.OnPreparedListener)}
     * 方法内<br/>
     * <code>
     * mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
     *
     * @param rate 0.2~2.0之间
     * @Override public void onPrepared(IMediaPlayer iMediaPlayer) {
     * mPlayerController.setSpeed(1.5f);
     * }
     * });
     * </code>
     */
    public PlayerController setSpeed(@FloatRange(from = 0.2, to = 2.0) float rate) {
        if (mVideoView != null) {
            mVideoView.setSpeed(rate);
        }
        return this;
    }

    /**
     * 设置播放位置
     */
    public PlayerController seekTo(int playtime) {
        if (mVideoView != null) {
            if (getDuration() > 1) {
                mVideoView.seekTo(playtime);
            }
        }
        return this;
    }

    /**
     * 获取当前播放位置
     */
    public int getCurrentPosition() {
        if (mVideoView != null) {
            currentPosition = mVideoView.getCurrentPosition();
        } else {
            /**直播*/
            currentPosition = -1;
        }
        return currentPosition;
    }

    /**
     * 获取视频播放总时长
     */
    public long getDuration() {
        if (mVideoView != null) {
            duration = mVideoView.getDuration();//exoplayer如果是直播流返回1
            return duration;
        } else {
            return 0;
        }
    }

    /**
     * 设置是否非WiFi网络类型提示
     *
     * @param isGNetWork true为进行2/3/4/5G网络类型提示
     *                   false 不进行网络类型提示
     */
    public PlayerController setNetWorkTypeTie(boolean isGNetWork) {
        this.isGNetWork = isGNetWork;
        registerWifiListener(mContext);
        return this;
    }

    public interface OnNetWorkListener {
        void onChanged();
    }

    private OnNetWorkListener onNetWorkListener;

    public PlayerController setNetWorkListener(OnNetWorkListener listener) {
        onNetWorkListener = listener;
        return this;
    }

    private MaxPlayListener maxPlayListener;

    public PlayerController setMaxPlayListener(MaxPlayListener listener) {
        maxPlayListener = listener;
        return this;
    }

    public interface MaxPlayListener {
        void update();
    }

    /**
     * 设置最大观看时长，时间不做限制时设置为-1
     *
     * @param maxPlaytime 最大能播放时长，单位秒
     */
    public PlayerController setMaxPlayTime(int maxPlaytime) {
        this.maxPlaytime = maxPlaytime * 1000;
        return this;
    }

    public int getMaxPlayTime() {
        return maxPlaytime;
    }

    /**==========================================Activity生命周期方法回调=============================*/
    /**
     * @Override protected void onPause() {
     * super.onPause();
     * if (player != null) {
     * player.onPause();
     * }
     * }
     */
    public PlayerController onPause() {
        if (mVideoView != null) {
            bgState = (mVideoView.isPlaying() ? 0 : 1);
            getCurrentPosition();
            mVideoView.pause();
        }
        return this;
    }

    /**
     * @Override protected void onDestroy() {
     * super.onDestroy();
     * if (player != null) {
     * player.onDestroy();
     * }
     * }
     */
    public PlayerController onDestroy() {
        unregisterWifiListener(mContext);
        orientationEventListener.disable();
        mHandler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
        if (mVideoView != null) {
            mVideoView.stopPlayback();
            //            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        }
        return this;
    }

    /**
     * @Override public void onConfigurationChanged(Configuration newConfig) {
     * super.onConfigurationChanged(newConfig);
     * if (player != null) {
     * player.onConfigurationChanged(newConfig);
     * }
     * }
     */
    public PlayerController onConfigurationChanged(final Configuration newConfig) {
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
        doOnConfigurationChanged(isPortrait);
        return this;
    }

    /**
     * @Override public void onConfigurationChanged(Configuration newConfig) {
     * super.onConfigurationChanged(newConfig);
     * if (player != null) {
     * player.onConfigurationChanged(newConfig);
     * }
     * }
     */
    public PlayerController onConfigurationChanged() {
        if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {// 横屏
            Log.e(TAG, "onConfigurationChanged: " + "横屏");
            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);//全屏
            //            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            onConfigurationLandScape();
            isPortrait = false;

        } else if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.e(TAG, "onConfigurationChanged: " + "竖屏");
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);//全屏
            //            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            onConfigurationPortrait();
            isPortrait = true;
        }
        return this;
    }

    /**
     * 是否是竖屏，默认为竖屏，true为竖屏，false为横屏
     *
     * @return
     */
    public boolean isPortrait() {
        return isPortrait;
    }

    /**
     * 调用该方法前必须调用过{@link #setVideoParentLayout(View)},
     * 参考{@link IRenderView#AR_ASPECT_FIT_PARENT}、{@link IRenderView#AR_ASPECT_FILL_PARENT}、{@link IRenderView#AR_ASPECT_WRAP_CONTENT}
     * {@link IRenderView#AR_16_9_FIT_PARENT}、{@link IRenderView#AR_4_3_FIT_PARENT}
     * 竖屏时回调
     */
    public PlayerController onConfigurationPortrait() {
        float displayAspectRatio;
        switch (aspectRatio) {
            case IRenderView.AR_16_9_FIT_PARENT:
                displayAspectRatio = 16.0f / 9.0f;
                break;
            case IRenderView.AR_4_3_FIT_PARENT:
                displayAspectRatio = 4.0f / 3.0f;
                break;
            case IRenderView.AR_ASPECT_FIT_PARENT:
            case IRenderView.AR_ASPECT_FILL_PARENT:
            case IRenderView.AR_ASPECT_WRAP_CONTENT:
            default:
                displayAspectRatio = 0;
                break;
        }

        if (displayAspectRatio != 0) {
            ViewGroup.LayoutParams params = videoParentLayout.getLayoutParams(); //取控件mRlVideoViewLayout当前的布局参数
            final int width = mActivity.getResources().getDisplayMetrics().widthPixels;
            final int heights = (int) (width / displayAspectRatio);
            //            final int heights = (int) (width * 0.5625);
            params.height = heights;// 强制设置控件的大小
            videoParentLayout.setLayoutParams(params); //使设置好的布局参数应用到控件
        } else {
            ViewGroup.LayoutParams params = videoParentLayout.getLayoutParams(); //取控件mRlVideoViewLayout当前的布局参数
            params.height = mActivity.getResources().getDisplayMetrics().heightPixels;
            videoParentLayout.setLayoutParams(params); //使设置好的布局参数应用到控
        }
        return this;
    }

    /**
     * 横屏时回调, 调用该方法前必须调用过{@link #setVideoParentLayout(View)}
     */
    public PlayerController onConfigurationLandScape() {
        ViewGroup.LayoutParams params = videoParentLayout.getLayoutParams(); //取控件mRlVideoViewLayout当前的布局参数
        params.height = mActivity.getResources().getDisplayMetrics().heightPixels;
        videoParentLayout.setLayoutParams(params); //使设置好的布局参数应用到控
        return this;
    }

    /**
     * @Override public void onBackPressed() {
     * if (player != null && player.onBackPressed()) {
     * return;
     * }
     * super.onBackPressed();
     * }
     */
    public boolean onBackPressed() {
        //        if (!isOnlyFullScreen && getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
        if (!isOnlyFullScreen && mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            if (onConfigurationChangedListener != null) {
                onConfigurationChangedListener.onChanged(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            return true;
        }
        return false;
    }

    /**
     * ==========================================对外的方法=============================
     */

    /**
     * @param activity
     */
    public PlayerController(Activity activity) {
        this.mActivity = activity;
        this.mContext = activity;
    }

    /**
     * 新的调用方法，适用非Activity中使用PlayerView，例如fragment、holder中使用
     */
    public PlayerController(Activity activity, IjkVideoView mVideoView) {
        this.mActivity = activity;
        this.mContext = activity;
        this.mVideoView = mVideoView;
    }

    /**
     * 设置IjkVideoView
     *
     * @param mVideoView
     * @return
     */
    public PlayerController setIjkVideoView(IjkVideoView mVideoView) {
        this.mVideoView = mVideoView;
        return this;
    }

    private GestureListener mGestureListener;
    private boolean progressEnable = true, volumeEnable = true, brightnessEnable = true;

    public interface GestureListener {
        /**
         * 设置快进快退
         *
         * @param newPosition 快进快退后要显示的时间（当前播放时间+快进快退了多少时间）
         * @param duration    总时间
         * @param showDelta   快进快退了多少时间
         */
        void onProgressSlide(long newPosition, long duration, int showDelta);

        /**
         * 设置当前视频的音量，范围0~100
         *
         * @param volume
         */
        void onVolumeSlide(int volume);

        /**
         * 设置当前屏幕的亮度0~100
         *
         * @param brightness
         */
        void onBrightnessSlide(float brightness);

        /**
         * 手指离开屏幕后，可以用于上面方法中控件的隐藏
         */
        void endGesture();
    }

    /**
     * 设置基于内置的GestureDetector进行控制，内置类{@link PlayerGestureListener}，回调{@link GestureListener}，
     * 该方法在{@link #setVideoParentLayout(View)}之后调用有效
     *
     * @param listener
     * @return
     */
    public PlayerController setGestureListener(GestureListener listener) {
        setGestureListener(true, true, true, listener);
        return this;
    }

    /**
     * 设置基于内置的GestureDetector进行控制，内置类{@link PlayerGestureListener}，回调{@link GestureListener}，
     * 该方法在{@link #setVideoParentLayout(View)}之后调用有效
     *
     * @param listener
     * @return
     */
    public PlayerController setGestureListener(boolean progressEnable, boolean volumeEnable, boolean brightnessEnable, GestureListener listener) {
        this.progressEnable = progressEnable;
        this.volumeEnable = volumeEnable;
        this.brightnessEnable = brightnessEnable;
        mGestureListener = listener;
        return this;
    }

    /**
     * 设置Gesture手势器是否可用
     */
    public PlayerController setGestureEnabled(boolean enabled) {
        setVideoParentTouchEvent(enabled);
        return this;
    }

    /**
     * 设置视频音量控制，要设置{@link #setGestureListener(GestureListener)}才能生效
     *
     * @return
     */
    public PlayerController setVolumeController() {
        audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return this;
    }

    /**
     * 设置屏幕亮度控制，要设置{@link #setGestureListener(GestureListener)}才能生效
     *
     * @return
     */
    public PlayerController setBrightnessController() {
        try {
            int e = android.provider.Settings.System.getInt(this.mContext.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
            float progress = 1.0F * (float) e / 255.0F;
            WindowManager.LayoutParams layout = this.mActivity.getWindow().getAttributes();
            layout.screenBrightness = progress;
            mActivity.getWindow().setAttributes(layout);
            brightness = progress;
        } catch (android.provider.Settings.SettingNotFoundException var7) {
            var7.printStackTrace();
        }
        return this;
    }

    /**
     * 设置控制视频播放进度的SeekBar
     *
     * @param videoController
     * @return
     */
    public PlayerController setVideoController(SeekBar videoController) {
        if (videoController == null) {
            return this;
        }

        this.videoController = videoController;
        videoController.setMax(seekBarMaxProgress);
        videoController.setProgress(0);
        videoController.setSecondaryProgress(0);
        videoController.setOnSeekBarChangeListener(mSeekListener);
        return this;
    }

    /**
     * 设置点击区域，基本上是播放器的父布局，建议第一个调用
     *
     * @param rootLayout
     * @return
     */
    public PlayerController setVideoParentLayout(View rootLayout) {
        videoParentLayout = rootLayout;
        gestureDetector = new GestureDetector(mContext, new PlayerGestureListener());
        //        setVideoParentTouchEvent(true);

        orientationEventListener = new OrientationEventListener(mActivity) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= 0 && orientation <= 30 || orientation >= 330 || (orientation >= 150 && orientation <= 210)) {
                    //竖屏
                    if (isPortrait) {
                        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        orientationEventListener.disable();
                    }
                } else if ((orientation >= 90 && orientation <= 120) || (orientation >= 240 && orientation <= 300)) {
                    if (!isPortrait) {
                        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        orientationEventListener.disable();
                    }
                }
            }
        };
        if (isOnlyFullScreen) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            if (onConfigurationChangedListener != null) {
                onConfigurationChangedListener.onChanged(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        }
        isPortrait = (getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        return this;
    }

    public interface PlayStateListener {
        void playState(int state);
    }

    private PlayStateListener playStateListener;

    public PlayerController setPlayStateListener(PlayStateListener listener) {
        playStateListener = listener;
        return this;
    }

    public interface SyncProgressListener {
        /**
         * 同步视频时间
         *
         * @param position 当前视频播放的时间
         * @param duration 视频的总时间
         */
        void syncTime(long position, long duration);

        void syncProgress(int progress, int secondaryProgress);
    }

    private SyncProgressListener syncProgressListener;

    /**
     * 设置视频播放进度的回调
     *
     * @param listener
     * @return
     */
    public PlayerController setSyncProgressListener(SyncProgressListener listener) {
        syncProgressListener = listener;
        return this;
    }

    /**
     * 对面板进行控制，单击{@link #setVideoParentLayout(View)}区域内生效
     *
     * @param listener
     * @return
     */
    public PlayerController setPanelControl(PanelControlListener listener) {
        panelControlListener = listener;
        return this;
    }

    private PanelControlListener panelControlListener;

    public interface PanelControlListener {
        /**
         * 控制操作面板的显示隐藏
         *
         * @param isShowControlPanel true：显示 false：隐藏
         */
        void operatorPanel(boolean isShowControlPanel);
    }

    /**
     * 设置是否自动显示隐藏操作面板，调用该方法才能自动隐藏操作面板
     *
     * @param isAuto
     * @return
     */
    public PlayerController setAutoControlPanel(boolean isAuto) {
        if (isAuto) {
            isShowControlPanel = false;
            mAutoControlPanelRunnable = new AutoControlPanelRunnable();
        } else {
            isShowControlPanel = true;
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.stop();
            }
            mAutoControlPanelRunnable = null;
        }
        mHandler.sendEmptyMessage(MESSAGE_SHOW_PROGRESS);
        if (mAutoControlPanelRunnable != null) {
            mAutoControlPanelRunnable.start(0);
        }
        return this;
    }

    /**
     * 这个方法要调用{@link #setAutoControlPanel(boolean)}，并且设置setAutoControlPanel(true)时才能生效
     * 这里最终执行的回调是{@link PanelControlListener}对应着{@link #setPanelControl(PanelControlListener)}
     *
     * @param views 设置控件按下时移除执行{@link #operatorPanel()}方法，离开控件时执行{@link #operatorPanel()}方法
     * @return
     */
    public PlayerController setAutoControlListener(View... views) {
        for (View view : views) {
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_DOWN:
                            if (mAutoControlPanelRunnable != null) {
                                mAutoControlPanelRunnable.stop();
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            endGesture();
                            break;
                    }
                    return true;
                }
            });
        }
        return this;
    }

    /**
     * 显示或隐藏操作面板
     */
    public PlayerController operatorPanel() {
        isShowControlPanel = !isShowControlPanel;
        if (isShowControlPanel) {
            mHandler.sendEmptyMessage(MESSAGE_SHOW_PROGRESS);
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.start(AutoControlPanelRunnable.AUTO_INTERVAL);
            }
        } else {
            //            mHandler.removeMessages(MESSAGE_SHOW_PROGRESS);
            if (mAutoControlPanelRunnable != null) {
                mAutoControlPanelRunnable.stop();
            }
        }
        if (panelControlListener != null) {
            panelControlListener.operatorPanel(isShowControlPanel);
        }
        return this;
    }

    /**
     * 是否仅仅为全屏
     */
    public PlayerController setOnlyFullScreen(boolean isFull) {
        this.isOnlyFullScreen = isFull;
        tryFullScreen(isOnlyFullScreen);
        if (isOnlyFullScreen) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            if (onConfigurationChangedListener != null) {
                onConfigurationChangedListener.onChanged(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        } else {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            if (onConfigurationChangedListener != null) {
                onConfigurationChangedListener.onChanged(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            }
        }
        return this;
    }

    public interface OnConfigurationChangedListener {
        void onChanged(int requestedOrientation);
    }

    private OnConfigurationChangedListener onConfigurationChangedListener;

    public PlayerController setOnConfigurationChangedListener(OnConfigurationChangedListener listener) {
        onConfigurationChangedListener = listener;
        return this;
    }

    /**
     * 设置是否禁止双击
     */
    public PlayerController setForbidDoubleUp(boolean flag) {
        this.isForbidDoulbeUp = flag;
        return this;
    }

    /**
     * 设置是否禁止隐藏bar
     */
    public PlayerController setForbidHideControlPanel(boolean flag) {
        this.isForbidHideControlPanel = flag;
        return this;
    }

    /**
     * 是否禁止触摸
     */
    public PlayerController forbidTouch(boolean forbidTouch) {
        this.isForbidTouch = forbidTouch;
        return this;
    }

    /**
     * 横竖屏切换
     */
    public PlayerController toggleScreenOrientation() {
        if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {// 横屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);// 竖屏
            if (onConfigurationChangedListener != null) {
                onConfigurationChangedListener.onChanged(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        } else if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {//竖屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            if (onConfigurationChangedListener != null) {
                onConfigurationChangedListener.onChanged(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        }
        return this;
    }

    /**
     * 设置是否开启屏幕常亮   true：保持常亮   false：清除常亮
     *
     * @param isKeep
     * @return
     */
    public PlayerController setKeepScreenOn(boolean isKeep) {
        if (isKeep) {
            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        return this;
    }

    /**
     * 判断是否为本地数据源，包括 本地文件、Asset、raw
     */
    public boolean isLocalDataSource(Uri uri) {
        //        Uri uri = mVideoView.getUri();
        if (uri != null) {
            String scheme = uri.getScheme();
            return ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                    || ContentResolver.SCHEME_FILE.equals(scheme)
                    || "rawresource".equals(scheme);
        }
        return false;
    }

    /**
     * 获取界面方向
     */
    private int getScreenOrientation() {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch (rotation) {
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_0:
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (rotation) {
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_0:
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    public void updateProgress(long position, long duration) {
        if (duration > 0) {
            long pos = seekBarMaxProgress * position / duration;
            int percent = mVideoView.getBufferPercentage();
            videoController.setProgress((int) pos);
            videoController.setSecondaryProgress(percent);
            if (syncProgressListener != null) {
                syncProgressListener.syncProgress((int) pos, percent);
            }
            //Log.d(TAG, "syncProgress: progress= " + pos + ", SecondaryProgress= " + percent);
        } else {
            videoController.setProgress(0);
            videoController.setSecondaryProgress(0);
            videoController.setEnabled(false);
            if (syncProgressListener != null) {
                syncProgressListener.syncProgress(0, 0);
            }
        }
    }

    public void sendAutoHideBarsMsg(long delayMillis) {
        if (mAutoControlPanelRunnable != null) {
            mAutoControlPanelRunnable.start(delayMillis);
        }
    }

    /**
     * 时长格式化显示
     *
     * @param time 毫秒级
     * @return
     */
    public static String generateTime(long time) {
        int totalSeconds = (int) (time / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                         : String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 下载速度格式化显示
     *
     * @param bytes 毫秒级
     * @return
     */
    public static String formatedSpeed(long bytes) {
        if (bytes <= 0) {
            return "0 B/s";
        }

        float bytes_per_sec = ((float) bytes) * 1000.f / 1000;
        if (bytes_per_sec >= 1000 * 1000) {
            return String.format(Locale.US, "%.2f MB/s", ((float) bytes_per_sec) / 1000 / 1000);
        } else if (bytes_per_sec >= 1000) {
            return String.format(Locale.US, "%.1f KB/s", ((float) bytes_per_sec) / 1000);
        } else {
            return String.format(Locale.US, "%d B/s", (long) bytes_per_sec);
        }
    }

    public static String formatedDurationMilli(long duration) {
        if (duration >= 1000) {
            return String.format(Locale.US, "%.2f sec", ((float) duration) / 1000);
        } else {
            return String.format(Locale.US, "%d msec", duration);
        }
    }

    public static String formatedSize(long bytes) {
        if (bytes >= 100 * 1000) {
            return String.format(Locale.US, "%.2f MB", ((float) bytes) / 1000 / 1000);
        } else if (bytes >= 100) {
            return String.format(Locale.US, "%.1f KB", ((float) bytes) / 1000);
        } else {
            return String.format(Locale.US, "%d B", bytes);
        }
    }

    public void setVideoInfo(TextView view, String value) {
        if (view != null) {
            view.setText(value);
        }
    }

    /**
     * ==========================================内部方法=============================
     */

    /**
     * 界面方向改变是刷新界面
     */
    private void doOnConfigurationChanged(final boolean portrait) {
        if (mVideoView != null && !isOnlyFullScreen) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    tryFullScreen(!portrait);
                }
            });
            orientationEventListener.enable();
        }
    }

    /**
     * 设置界面方向
     */
    private void setFullScreen(boolean fullScreen) {
        if (mActivity != null) {
            WindowManager.LayoutParams attrs = mActivity.getWindow().getAttributes();
            if (fullScreen) {
                attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                mActivity.getWindow().setAttributes(attrs);
                mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            } else {
                attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
                mActivity.getWindow().setAttributes(attrs);
                mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        }

    }

    /**
     * 是否显示ActionBar
     *
     * @param fullScreen
     */
    private void showActionBar(boolean fullScreen) {
        if (mActivity instanceof AppCompatActivity) {
            ActionBar supportActionBar = ((AppCompatActivity) mActivity).getSupportActionBar();
            if (supportActionBar != null) {
                if (fullScreen) {
                    supportActionBar.hide();
                } else {
                    supportActionBar.show();
                }
            }
        }
    }

    /**
     * 设置界面方向带隐藏actionbar
     */
    private void tryFullScreen(boolean fullScreen) {
        showActionBar(fullScreen);
        setFullScreen(fullScreen);
    }

    /**
     * 对播放区域设置Touch事件
     *
     * @param enabled
     */
    private void setVideoParentTouchEvent(final boolean enabled) {
        videoParentLayout.setClickable(true);
        videoParentLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        if (mAutoControlPanelRunnable != null) {
                            mAutoControlPanelRunnable.stop();
                        }
                        break;
                }
                if (enabled && gestureDetector.onTouchEvent(motionEvent)) {
                    return true;
                }
                // 处理手势结束
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_UP:
                        endGesture();
                        break;
                }
                return true;
            }
        });
    }

    /**
     * 手势结束
     */
    private void endGesture() {
        brightness = mActivity.getWindow().getAttributes().screenBrightness;
        if (audioManager != null) {
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        if (newPosition >= 0) {
            mHandler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
            mHandler.sendEmptyMessage(MESSAGE_SEEK_NEW_POSITION);
        }
        mHandler.removeMessages(MESSAGE_HIDE_CENTER_BOX);
        mHandler.sendEmptyMessageDelayed(MESSAGE_HIDE_CENTER_BOX, 500);
        if (mGestureListener != null) {
            mGestureListener.endGesture();
        }
        if (mAutoControlPanelRunnable != null) {
            mAutoControlPanelRunnable.start(AutoControlPanelRunnable.AUTO_INTERVAL);
        }
    }

    /**
     * 同步进度
     */
    private long syncProgress() {
        if (isDragging) {
            return 0;
        }
        if (mVideoView == null) {
            return 0;
        }
        long position = mVideoView.getCurrentPosition();
        long duration = mVideoView.getDuration();
        if (videoController != null) {
            updateProgress(position, duration);
        }

        if (maxPlaytime != -1 && maxPlaytime + 1000 < getCurrentPosition()) {
            if (maxPlayListener != null) {
                maxPlayListener.update();
            }
        } else {
            if (syncProgressListener != null) {
                syncProgressListener.syncTime(position, duration);
            }
        }
        return position;
    }

    /**
     * 滑动改变声音大小
     *
     * @param percent
     */
    private void onVolumeSlide(float percent) {
        if (audioManager != null) {
            int index = (int) (percent * mMaxVolume) + volume;
            if (index > mMaxVolume)
                index = mMaxVolume;
            else if (index < 0)
                index = 0;

            // 变更声音
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);

            // 变更进度条
            int i = (int) (index * 1.0 / mMaxVolume * 100);
            if (mGestureListener != null) {
                mGestureListener.onVolumeSlide(i);
            }
        }
    }

    /**
     * 亮度滑动改变亮度
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {
        if (brightness < 0.05f) {
            brightness = 0.05f;
        }
        Log.d(this.getClass().getSimpleName(), "brightness:" + brightness + ",percent:" + percent);
        WindowManager.LayoutParams lp = mActivity.getWindow().getAttributes();
        lp.screenBrightness = brightness + percent;
        if (lp.screenBrightness > 1.0f) {
            lp.screenBrightness = 1.0f;
        } else if (lp.screenBrightness < 0.05f) {
            lp.screenBrightness = 0.05f;
        }
        mActivity.getWindow().setAttributes(lp);
        if (mGestureListener != null) {
            mGestureListener.onBrightnessSlide(lp.screenBrightness);
        }
    }

    /**
     * 快进或者快退滑动改变进度
     *
     * @param percent
     */
    private void onProgressSlide(float percent) {
        int position = mVideoView.getCurrentPosition();
        int duration = mVideoView.getDuration();
        long deltaMin = Math.min(100 * 1000, duration - position);
        long delta = (long) (deltaMin * percent);
        newPosition = delta + position;
        if (maxPlaytime != -1 && maxPlaytime + 1000 < newPosition) {
            return;
        }
        if (newPosition > duration) {
            newPosition = duration;
        } else if (newPosition <= 0) {
            newPosition = 0;
            delta = -position;
        }
        int showDelta = (int) delta / 1000;
        if (mGestureListener != null) {
            mGestureListener.onProgressSlide(newPosition, duration, showDelta);
        }
    }

    /**
     * ==========================================内部类=============================
     */
    private class AutoControlPanelRunnable implements Runnable {

        public static final int AUTO_INTERVAL = 5000;

        public void start(long delayMillis) {
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, delayMillis);
        }

        public void stop() {
            mHandler.removeCallbacks(this);
        }

        @Override
        public void run() {
            operatorPanel();
        }
    }

    /**
     * 播放器的手势监听
     */
    private class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {
        /**
         * 是否是按下的标识，默认为其他动作，true为按下标识，false为其他动作
         */
        private boolean isDownTouch;
        /**
         * 是否声音控制,默认为亮度控制，true为声音控制，false为亮度控制
         */
        private boolean isVolume;
        /**
         * 是否横向滑动，默认为纵向滑动，true为横向滑动，false为纵向滑动
         */
        private boolean isLandscape;

        /**
         * 双击
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            /**视频视窗双击事件*/
            if (!isForbidTouch && !isOnlyFullScreen && !isForbidDoulbeUp) {
                toggleScreenOrientation();
            }
            return true;
        }

        /**
         * 按下
         */
        @Override
        public boolean onDown(MotionEvent e) {
            isDownTouch = true;
            return super.onDown(e);
        }


        /**
         * 滑动
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!isForbidTouch) {
                if (mVideoView == null) {
                    return false;
                }
                int width = mVideoView.getWidth();
                int top = mVideoView.getTop();
                int left = mVideoView.getLeft();
                int bottom = mVideoView.getBottom();

                if (e2.getY() <= top || e2.getY() >= bottom) {
                    return false;
                }

                float mOldX = e1.getX(), mOldY = e1.getY();
                float deltaY = mOldY - e2.getY();
                float deltaX = mOldX - e2.getX();
                if (isDownTouch) {
                    isLandscape = Math.abs(distanceX) >= Math.abs(distanceY);
                    isVolume = mOldX > left + width / 2;
                    isDownTouch = false;
                    brightness = mActivity.getWindow().getAttributes().screenBrightness;
                    Log.d(TAG, "onScroll: brightness: " + brightness);
                }

                if (isLandscape) {
                    /**进度设置*/
                    if (progressEnable && getDuration() > 1) {
                        onProgressSlide(-deltaX / mVideoView.getWidth());
                    }
                } else {
                    float percent = deltaY / mVideoView.getHeight();
                    if (isVolume) {
                        if (volumeEnable) {
                            /**声音设置*/
                            onVolumeSlide(percent);
                        }
                    } else {
                        if (brightnessEnable) {
                            /**亮度设置*/
                            onBrightnessSlide(percent);
                        }
                    }
                }
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        /**
         * 单击
         */
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            /**视频视窗单击事件*/
            if (!isForbidTouch) {
                operatorPanel();
            }
            return true;
        }
    }
}
