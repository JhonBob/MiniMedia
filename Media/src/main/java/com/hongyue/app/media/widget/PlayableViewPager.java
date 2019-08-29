package com.hongyue.app.media.widget;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

import android.util.AttributeSet;

import com.hongyue.app.media.util.misc.Preconditions;
import com.hongyue.app.media.view.VerticalViewPager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hongyue.app.media.util.misc.CollectionUtils.hashSetOf;


/**
 *  Description:  仿抖音ViewPager
 *  Author: Charlie
 *  Data: 2019/7/4  9:33
 *  Declare: None
 */

public class PlayableViewPager extends VerticalViewPager implements PlayableItemsContainer, ViewPager.OnPageChangeListener{


    private static final Set<PlaybackTriggeringState> DEFAULT_PLAYBACK_TRIGGERING_STATES = hashSetOf(
            PlaybackTriggeringState.DRAGGING,
            PlaybackTriggeringState.IDLING
    );

    private final Set<PlaybackTriggeringState> mPlaybackTriggeringStates = new HashSet<>();

    private int mPreviousScrollDeltaX;
    private int mPreviousScrollDeltaY;

    private AutoplayMode mAutoplayMode;

    private boolean mIsAutoplayEnabled;
    private boolean mIsScrolling;


    public PlayableViewPager(Context context) {
        super(context);
        init();
    }

    public PlayableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }



    private void init() {
        mPreviousScrollDeltaX = 0;
        mPreviousScrollDeltaY = 0;
        mAutoplayMode = AutoplayMode.ONE_AT_A_TIME;
        mIsAutoplayEnabled = true;

        mPlaybackTriggeringStates.addAll(DEFAULT_PLAYBACK_TRIGGERING_STATES);

        setOnPageChangeListener(this);

    }

    @Override
    public void startPlayback() {
        handleItemPlayback(true);

    }

    @Override
    public void stopPlayback() {

        stopItemPlayback();

    }

    @Override
    public void pausePlayback() {

        pauseItemPlayback();

    }

    @Override
    public void onResume() {

        startPlayback();

    }

    @Override
    public void onPause() {

        pausePlayback();

    }

    @Override
    public void onStop() {
        stopPlayback();
    }

    @Override
    public void onDestroy() {

        releaseAllItems();

    }



    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseAllItems();
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startPlayback();
    }


    @Override
    public void setAutoplayMode(@NonNull AutoplayMode autoplayMode) {

        mAutoplayMode = Preconditions.checkNonNull(autoplayMode);

        if(isAutoplayEnabled()) {
            startPlayback();
        }

    }

    @NonNull
    @Override
    public AutoplayMode getAutoplayMode() {
        return mAutoplayMode;
    }

    @Override
    public void setPlaybackTriggeringStates(@NonNull PlaybackTriggeringState... states) {
        Preconditions.nonNull(states);

        mPlaybackTriggeringStates.clear();
        mPlaybackTriggeringStates.addAll((states.length == 0) ? DEFAULT_PLAYBACK_TRIGGERING_STATES : hashSetOf(states));
    }

    @NonNull
    @Override
    public Set<PlaybackTriggeringState> getPlaybackTriggeringStates() {
        return mPlaybackTriggeringStates;
    }


    @Override
    public final void setAutoplayEnabled(boolean isAutoplayEnabled) {
        mIsAutoplayEnabled = isAutoplayEnabled;

        if(isAutoplayEnabled) {
            startPlayback();
        } else {
            stopPlayback();
        }
    }




    @Override
    public final boolean isAutoplayEnabled() {
        return mIsAutoplayEnabled;
    }



    private void handleItemPlayback(boolean allowPlay) {
        final List<Playable> playableItems = new ArrayList<>();
        final int childCount = getChildCount();
        final boolean canHaveMultipleActiveItems = AutoplayMode.MULTIPLE_SIMULTANEOUSLY.equals(mAutoplayMode);

        PlayableFragment viewHolder;
        boolean isInPlayableArea;
        boolean hasActiveItem = false;

        // extracting all the playable visible items
        for(int i = 0; i < childCount; i++) {
            viewHolder = findContainingViewHolder(i);

            if((viewHolder instanceof Playable)
                    && ((Playable) viewHolder).isTrulyPlayable()) {
                playableItems.add((Playable) viewHolder);
            }
        }

        // processing the extracted Playable items
        for(Playable playable : playableItems) {
            isInPlayableArea = playable.wantsToPlay();

            // handling the playback state
            if(isInPlayableArea && (!hasActiveItem || canHaveMultipleActiveItems)) {
                if(!playable.isPlaying()
                        && mIsAutoplayEnabled
                        && allowPlay) {
                    playable.start();
                }

                hasActiveItem = true;
            } else if(playable.isPlaying()) {
                playable.pause();
            }

            playable.onPlayabilityStateChanged(isInPlayableArea);
        }
    }



    private void stopItemPlayback() {
        final int childCount = getChildCount();
        PlayableFragment viewHolder;

        for(int i = 0; i < childCount; i++) {
            viewHolder = findContainingViewHolder(i);

            if((viewHolder instanceof Playable)
                    && ((Playable) viewHolder).isTrulyPlayable()) {
                ((Playable) viewHolder).stop();
            }
        }
    }




    private void pauseItemPlayback() {
        final int childCount = getChildCount();
        PlayableFragment viewHolder;

        for(int i = 0; i < childCount; i++) {
            viewHolder = findContainingViewHolder(i);

            if((viewHolder instanceof Playable)
                    && ((Playable) viewHolder).isTrulyPlayable()) {
                ((Playable) viewHolder).pause();
            }
        }
    }




    private void releaseAllItems() {
        final int childCount = getChildCount();
        PlayableFragment viewHolder;

        for(int i = 0; i < childCount; i++) {
            viewHolder = findContainingViewHolder(i);

            if((viewHolder instanceof Playable)
                    && ((Playable) viewHolder).isTrulyPlayable()) {
                ((Playable) viewHolder).release();
            }
        }
    }



    private PlaybackTriggeringState getPlaybackStateForScrollState(int scrollState) {
        switch(scrollState) {

            case SCROLL_STATE_SETTLING:
                return PlaybackTriggeringState.SETTLING;

            case SCROLL_STATE_DRAGGING:
                return PlaybackTriggeringState.DRAGGING;

            default:
                return PlaybackTriggeringState.IDLING;

        }
    }



    private boolean canPlay() {
        final PlaybackTriggeringState state = getPlaybackStateForScrollState(getScrollState());
        final boolean containsState = mPlaybackTriggeringStates.contains(state);
        final boolean isDragging = (PlaybackTriggeringState.DRAGGING.equals(state) && !mIsScrolling);
        final boolean isSettling = PlaybackTriggeringState.SETTLING.equals(state);
        final boolean isIdling = PlaybackTriggeringState.IDLING.equals(state);

        return (containsState && (isDragging || isSettling || isIdling));
    }


    @Override
    public void onPageScrolled(int i, float v, int distance) {

        mIsScrolling = ((Math.abs(mPreviousScrollDeltaX - distance) > 0) || (Math.abs(mPreviousScrollDeltaY - v) > distance));

        handleItemPlayback(canPlay());

        mPreviousScrollDeltaX = distance;
        mPreviousScrollDeltaY = distance;

    }

    @Override
    public void onPageSelected(int i) {

    }

    @Override
    public void onPageScrollStateChanged(int i) {

        handleItemPlayback(canPlay());

    }


}
