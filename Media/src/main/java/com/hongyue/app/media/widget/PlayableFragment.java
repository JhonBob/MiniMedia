package com.hongyue.app.media.widget;

import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.hongyue.app.media.Config;
import com.hongyue.app.media.PlayerProviderImpl;
import com.hongyue.app.media.model.PlaybackInfo;
import com.hongyue.app.media.model.VolumeInfo;
import com.hongyue.app.media.player.Player;
import com.hongyue.app.media.util.cache.PlaybackInfoCache;
import com.hongyue.app.media.util.misc.ExoPlayerUtils;
import com.hongyue.app.media.util.misc.TimeUtils;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import app.hongyue.com.media.R;


/**
 *  Description:  仿抖音播放单元
 *  Author: Charlie
 *  Data: 2019/7/4  8:59
 *  Declare: None
 */

public abstract class PlayableFragment extends Fragment implements Playable, Player.AttachmentStateDelegate, Player.EventListener{


    public static final String TAG = "PlayableFragment";

    private static final float  DEFAULT_TRIGGER_OFFSET = 0.5f;
    private static final int UPDATE_TIME = 007;

    public  ViewGroup mParentViewGroup;
    public PlayerView mPlayerView;
    public View itemView;
    public PlayerTimerHandler mHandler;
    private Timer timer;
    private TimerTask timerTask;


    public PlayableFragment() {

    }


    /**
     *  Description: 此函数由子类调用
     *  Author: Charlie
     *  Data: 2020/1/17  8:51
     *  Declare: None
     */
    public void onView(ViewGroup parentViewGroup, View mView) {
        mParentViewGroup = parentViewGroup;
        mPlayerView = mView.findViewById(R.id.player_view);
        itemView = mView;
        mHandler = new PlayerTimerHandler(this);
    }

    @Override
    public final void start() {
        if(!isTrulyPlayable()) {
            return;
        }

        if(startPlayer()) {
            onStateChanged((getPlaybackState() == Player.PlaybackState.READY) ? PlaybackState.READY : PlaybackState.STARTED);
        }
    }




    @Override
    public final void restart() {
        if(!isTrulyPlayable()) {
            return;
        }

        restartPlayer();
        onStateChanged(PlaybackState.RESTARTED);
    }




    @Override
    public final void pause() {
        if(!isTrulyPlayable()) {
            return;
        }

        pausePlayer();
        onStateChanged(PlaybackState.PAUSED);
    }




    @Override
    public final void stop() {
        if(!isTrulyPlayable()) {
            return;
        }

        stopPlayer();
        onStateChanged(PlaybackState.STOPPED);
    }




    @Override
    public final void release() {
        if(!isTrulyPlayable()) {
            return;
        }

        releasePlayer();
        onStateChanged(PlaybackState.STOPPED);
    }




    private boolean startPlayer() {
        // creating/updating the PlaybackInfo for this particular Playable
        final PlaybackInfo playbackInfo = getPlaybackInfo();
        final VolumeInfo volumeInfo = playbackInfo.getVolumeInfo();

        setPlaybackInfo(playbackInfo);

        // determining whether the current Playable should play this time
        final boolean shouldPlay = (isLooping() || !playbackInfo.isEnded() || canStartPlaying());

        // preparing the Player
        final Player player = getOrInitPlayer();
        player.init();
        player.attach(mPlayerView);
        player.getVolumeController().setVolume(volumeInfo.getVolume());
        player.getVolumeController().setMuted(volumeInfo.isMuted());
        player.setMediaSource(createMediaSource());
        player.setAttachmentStateDelegate(this);
        player.addEventListener(this);

        // performing the playing related operations (if necessary)
        if(shouldPlay) {
            player.seek(playbackInfo.getPlaybackPosition());
            player.prepare(false);
            player.play();
            startPlayerTimer();
        }

        return shouldPlay;
    }




    private void restartPlayer() {
        // updating the PlaybackInfo
        final PlaybackInfo playbackInfo = getPlaybackInfo();
        playbackInfo.setPlaybackPosition(0);

        final VolumeInfo volumeInfo = playbackInfo.getVolumeInfo();

        setPlaybackInfo(playbackInfo);

        // preparing the Player
        final Player player = getOrInitPlayer();
        player.init();
        player.attach(mPlayerView);
        player.getVolumeController().setVolume(volumeInfo.getVolume());
        player.getVolumeController().setMuted(volumeInfo.isMuted());
        player.setMediaSource(createMediaSource());
        player.setAttachmentStateDelegate(this);
        player.removeEventListener(this);
        player.addEventListener(this);
        player.seek(playbackInfo.getPlaybackPosition());
        player.prepare(false);
        player.play();
        resumePalyerTimer();
    }




    private void pausePlayer() {
        final Player player = getPlayer();
        final PlaybackInfo playbackInfo = getPlaybackInfo();

        if(player != null) {
            player.pause();
            player.removeEventListener(this);

            playbackInfo.setPlaybackPosition(player.getPlaybackPosition());
            setPlaybackInfo(playbackInfo);
            pausePalyerTimer();
        }
    }




    private void stopPlayer() {
        final PlaybackInfo playbackInfo = getPlaybackInfo();
        final Player player = getPlayer();

        if(player != null) {
            player.pause();
            player.detach(mPlayerView);
            player.stop(true);
            player.setAttachmentStateDelegate(null);
            player.removeEventListener(this);

            playbackInfo.setPlaybackPosition(0L);
            setPlaybackInfo(playbackInfo);
            stopPlayerTimer();
        }
    }




    private void releasePlayer() {
        final Player player = getPlayer();
        unregisterPlayer();
        removePlaybackInfo();
        destroyPalyerTimer();

        if(player != null) {
            player.pause();
            player.stop(true);
            player.detach(mPlayerView);
            player.setAttachmentStateDelegate(null);
            player.removeEventListener(this);
        }
    }




    @Override
    public final void seekTo(long positionInMillis) {
        final PlaybackInfo playbackInfo = getPlaybackInfo();
        final Player player = getPlayer();

        if(player != null) {
            player.seek(positionInMillis);
            playbackInfo.setPlaybackPosition(positionInMillis);
            setPlaybackInfo(playbackInfo);
        }
    }




    @Override
    public final long getPlaybackPosition() {
        final Player player = getPlayer();
        return ((player != null) ? player.getPlaybackPosition() : 0);
    }




    @Override
    public long getDuration() {
        final Player player = getPlayer();
        return ((player != null) ? player.getDuration() : 0);
    }




    @Override
    public final View getPlayerView() {
        return mPlayerView;
    }




    @Override
    public final ViewParent getParent() {
        return (ViewParent) itemView;
    }




    private Player getPlayer() {
        return PlayerProviderImpl.getInstance(itemView.getContext()).getPlayer(getConfig(), getKey());
    }




    private Player getOrInitPlayer() {
        return PlayerProviderImpl.getInstance(itemView.getContext()).getOrInitPlayer(getConfig(), getKey());
    }




    private void unregisterPlayer() {
        PlayerProviderImpl.getInstance(itemView.getContext()).unregister(getConfig(), getKey());
    }




    private MediaSource createMediaSource() {
        return PlayerProviderImpl.getInstance(itemView.getContext()).createMediaSource(
                getConfig(),
                Uri.parse(getUrl()),
                isLooping()
        );
    }




    private void setPlaybackInfo(PlaybackInfo playbackInfo) {
        PlaybackInfoCache.getInstance().put(getKey(), playbackInfo);
    }




    @Override
    public final PlaybackInfo getPlaybackInfo() {
        return PlaybackInfoCache.getInstance().get(getKey(), new PlaybackInfo());
    }




    private void removePlaybackInfo() {
        PlaybackInfoCache.getInstance().remove(getKey());
    }




    private int getPlaybackState() {
        final Player player = getPlayer();
        return ((player != null) ? player.getPlaybackState() : Player.PlaybackState.IDLE);
    }




    @NonNull
    @Override
    public Config getConfig() {
        return PlayerProviderImpl.DEFAULT_CONFIG;
    }




    @NonNull
    @Override
    public final String getKey() {
        return (getUrl() + getTag());
    }




    /**
     * <br>
     *      Used to determine the current {@link PlayableItemViewHolder}'s Item {@link View} area visibility ratio that's
     *      sufficient enough to start/stop the video playback.
     * <br>
     * <br>
     *     This method is used internally by the {@link #wantsToPlay()} method to properly determine whether it's
     *     the time to start or stop the video playback.
     * <br>
     * <br>
     *     You can override this method and specify your own area visibility ratio to be used for the handling of the aforementioned events,
     *     just keep in mind the fact that the ratio should be between <strong>0.0</strong> and <strong>1.0</strong>, where
     *     <strong>0.0</strong> and <strong>1.0</strong> are 0% and 100% Item {@link View} visibility correspondingly.
     *
     * @return a value between <strong>0.0</strong> and <strong>1.0</strong>.
     */
    @FloatRange(from = 0.0, to = 1.0)
    protected float getTriggerOffset() {
        return DEFAULT_TRIGGER_OFFSET;
    }




    /**
     * <br>
     *      Sets the audio volume to be used during the playback of the video associated with this {@link PlayableItemViewHolder}.
     * <br>
     *      If the playback is the active state, then the volume will be adjusted directly on the corresponding {@link Player} instance.
     *
     * @param audioVolume the exact audio volume (a value between <strong>0.0</strong> and <strong>1.0</strong>).
     */
    protected final void setVolume(@FloatRange(from = 0.0, to = 1.0) float audioVolume) {
        // creating/updating the corresponding Playback Info
        final PlaybackInfo playbackInfo = getPlaybackInfo();
        playbackInfo.getVolumeInfo().setVolume(audioVolume);

        setPlaybackInfo(playbackInfo);

        // updating the Player-related state (if necessary)
        final Player player = getPlayer();

        if(player != null) {
            player.getVolumeController().setVolume(audioVolume);
        }
    }




    /**
     * Retrieves the audio volume that's associated with the current instance of the {@link PlayableItemViewHolder}.
     *
     * @return an audio volume ratio (a value between <strong>0.0</strong> and <strong>1.0</strong>).
     */
    @FloatRange(from = 0.0, to = 1.0)
    protected final float getVolume() {
        final PlaybackInfo playbackInfo = getPlaybackInfo();
        final Player player = getPlayer();

        return ((player != null) ? player.getVolumeController().getVolume() : playbackInfo.getVolumeInfo().getVolume());
    }




    /**
     * <br>
     *      Sets the audio muted state to be used during the playback of the video associated with this {@link PlayableItemViewHolder}.
     * <br>
     *      If the playback is the active state, then the audio muted state will be adjusted directly on the corresponding {@link Player} instance.
     *
     * @param isMuted the exact audio muted state.
     */
    public final void setMuted(boolean isMuted) {
        // creating/updating the corresponding Playback Info
        final PlaybackInfo playbackInfo = getPlaybackInfo();
        playbackInfo.getVolumeInfo().setMuted(isMuted);

        setPlaybackInfo(playbackInfo);

        // updating the Player-related state (if necessary)
        final Player player = getPlayer();

        if(player != null) {
            player.getVolumeController().setMuted(isMuted);
        }
    }




    /**
     * Retrieves the muted state of the audio that's associated with the current instance of the {@link PlayableItemViewHolder}.
     *
     * @return the muted state of the audio.
     */
    public final boolean isMuted() {
        final PlaybackInfo playbackInfo = getPlaybackInfo();
        final Player player = getPlayer();

        return ((player != null) ? player.getVolumeController().isMuted() : playbackInfo.getVolumeInfo().isMuted());
    }




    @Override
    public final boolean isPlaying() {
        final Player player = getPlayer();
        return ((player != null) && player.isPlaying());
    }




    @Override
    public final boolean isTrulyPlayable() {
        return (mPlayerView != null);
    }




    @Override
    public boolean isLooping() {
        return false;
    }




    private boolean isEnded() {
        final Player player = getPlayer();
        return ((player != null) && (player.getPlaybackState() == Player.PlaybackState.ENDED));
    }




    @Override
    public final boolean isAttached(@NonNull Player player) {
        return (isTrulyPlayable() && player.isAttached(mPlayerView));
    }




    @Override
    public final boolean wantsToPlay() {
        return (ExoPlayerUtils.getVisibleAreaOffset(this) >= getTriggerOffset());
    }




    /**
     * <br>
     *      Used to determine whether the playback of a non-looping video can be started.
     *      (Used as a last means of confirmation of the initiation of the playback)
     * <br>
     *      It is to be overridden and used only in cases when you need a specific
     *      control over when the video playback starts (or can be started).
     * <br>
     *      By default, it's always <strong>true</strong>.
     *
     * @return <strong>true</strong> to allow the initiation of the video playback, <strong>false</strong> otherwise.
     */
    protected boolean canStartPlaying() {
        return true;
    }




    /**
     * Gets called when the Playback {@link PlaybackState} changes.
     *
     * @param playbackState the new Playback {@link PlaybackState}.
     */
    protected void onStateChanged(@NonNull PlaybackState playbackState) {
        // to be overridden.
    }




    @Override
    public final void onAttach(@NonNull Player player) {
        if(mPlayerView != null) {
            player.attach(mPlayerView);
        }
    }




    @Override
    public final void onDetach(@NonNull Player player) {
        if(mPlayerView != null) {
            player.detach(mPlayerView);
        }
    }




    @Override
    public void onPlayabilityStateChanged(boolean isPlayable) {
        // to be overridden if necessary.
    }




    @Override
    public final void onPlayerStateChanged(int playbackState) {
        switch(playbackState) {

            case Player.PlaybackState.IDLE:
                onPlaybackIdle();
                break;

            case Player.PlaybackState.BUFFERING:
                onPlaybackBuffering();
                break;

            case Player.PlaybackState.READY:
                onPlaybackReady();
                break;

            case Player.PlaybackState.ENDED:
                onPlaybackEnded();
                break;

        }
    }




    private void onPlaybackIdle() {
        getPlaybackInfo().setEnded(isEnded());

        onStateChanged(PlaybackState.STOPPED);
    }




    private void onPlaybackBuffering() {
        getPlaybackInfo().setEnded(isEnded());

        onStateChanged(PlaybackState.BUFFERING);
    }




    private void onPlaybackReady() {
        getPlaybackInfo().setEnded(isEnded());

        onStateChanged(PlaybackState.READY);
    }




    private void onPlaybackEnded() {
        onStateChanged(PlaybackState.STOPPED);

        final PlaybackInfo playbackInfo = getPlaybackInfo();
        playbackInfo.setPlaybackPosition(0);
        playbackInfo.setEnded(isEnded());
        setPlaybackInfo(playbackInfo);
    }




    @Override
    public final void onLoadingChanged(boolean isLoading) {
        // do nothing.
    }




    @Override
    public final void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        // do nothing.
    }




    @Override
    public final void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, ("onPlayerError: " + error.getLocalizedMessage()));

        onStateChanged(PlaybackState.ERROR);

        //TODO <--- onPlayback ended?!
    }



    /**
     *  Description:  初始化定时器
     *  Author: Charlie
     *  Data: 2020/1/9  13:38
     *  Declare: None
     */

    private void initPlayerTimer(){

        timerTask = new TimerTask() {

            @Override
            public void run() {

                Message message = new Message();
                message.what = UPDATE_TIME;
                mHandler.sendMessage(message);

            }
        };
        timer = new Timer();

    }


    /**
     *  Description:  开始定时器
     *  Author: Charlie
     *  Data: 2020/1/9  13:40
     *  Declare: None
     */

    private void startPlayerTimer(){

        destroyPalyerTimer();

        if (mHandler == null) {
            mHandler = new PlayerTimerHandler(PlayableFragment.this);
        }

        if (!enableTimer()) {
            return;
        }

        initPlayerTimer();

        timer.schedule(timerTask, 0, 1000);
    }


    /**
     *  Description:  取消定时器
     *  Author: Charlie
     *  Data: 2020/1/9  13:40
     *  Declare: None
     */

    private void stopPlayerTimer(){
        if (timer != null){
            timer.cancel();
        }
    }



    /**
     *  Description:  暂停定时器
     *  Author: Charlie
     *  Data: 2020/1/9  13:34
     *  Declare: None
     */

    private void pausePalyerTimer(){
        if (timer != null){
            timer.cancel();
        }
    }


    /**
     *  Description:  唤醒定时器
     *  Author: Charlie
     *  Data: 2020/1/9  13:40
     *  Declare: None
     */

    private void resumePalyerTimer(){
        destroyPalyerTimer();
        initPlayerTimer();
        timer.schedule(timerTask, 0, 1000);
    }



    /**
     *  Description:  释放定时器
     *  Author: Charlie
     *  Data: 2019/7/7  11:02
     *  Declare: None
     */

    private void destroyPalyerTimer(){

        if (timer != null){
            timer.cancel();
            timer = null;
        }
        if (timerTask != null){
            timerTask.cancel();
            timerTask = null;
        }
        if (mHandler != null){
            mHandler.removeCallbacksAndMessages(null); //清除所有任务
        }
    }



    /**
     *  Description:  定时任务处理
     *  Author: Charlie
     *  Data: 2019/7/7  11:03
     *  Declare: None
     */

    public class PlayerTimerHandler extends Handler {


        private final WeakReference<PlayableFragment> mHolder; //防止内存泄露

        public PlayerTimerHandler(PlayableFragment mHolder) {
            this.mHolder = new WeakReference<PlayableFragment>(mHolder);
        }

        @Override
        public void handleMessage( Message msg) {
            super.handleMessage(msg);

            PlayableFragment holder = mHolder.get();

            if (holder == null){
                return;
            }

            if (msg.what == UPDATE_TIME && getDuration() > 0) {
                int time = (int) ((getPlaybackPosition()) / 1000);
                onCountDownTime(TimeUtils.getPlayerTimeStr(time));
            }
        }

    }

}
