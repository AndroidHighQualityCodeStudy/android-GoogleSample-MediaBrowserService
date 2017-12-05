/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.mediasession.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.AppCompatSeekBar;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.SeekBar;

/**
 * SeekBar that can be used with a {@link MediaSessionCompat} to track and seek in playing
 * media.
 */

public class MediaSeekBar extends AppCompatSeekBar {


    // 音频变化控制器
    private MediaControllerCompat mMediaController;
    // 音频变化回调
    private ControllerCallback mControllerCallback;

    private boolean mIsTracking = false;


    /**
     * seekbar 变化回调
     */
    private OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mIsTracking = true;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // 音频seek
            mMediaController.getTransportControls().seekTo(getProgress());
            //
            mIsTracking = false;
        }
    };
    /**
     * 属性动画
     */
    private ValueAnimator mProgressAnimator;

    public MediaSeekBar(Context context) {
        super(context);
        // seek
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    public MediaSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    public MediaSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
    }

    @Override
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        // Prohibit adding seek listeners to this subclass.
    }


    /**
     * 设置音频变化控制器
     *
     * @param mediaController
     */
    public void setMediaController(final MediaControllerCompat mediaController) {
        //
        if (mediaController != null) {
            // 音频变化回调
            mControllerCallback = new ControllerCallback();
            // 注册回调
            mediaController.registerCallback(mControllerCallback);
        }
        // 取消回调注册
        else if (mMediaController != null) {
            mMediaController.unregisterCallback(mControllerCallback);
            mControllerCallback = null;
        }
        // mediaController赋值
        mMediaController = mediaController;
    }

    /**
     * 取消音频变化回调
     */
    public void disconnectController() {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mControllerCallback);
            mControllerCallback = null;
            mMediaController = null;
        }
    }


    /**
     *
     */
    private class ControllerCallback
            extends MediaControllerCompat.Callback
            implements ValueAnimator.AnimatorUpdateListener {

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);

            /**
             * 正在进行的动画 关闭
             */
            // If there's an ongoing animation, stop it now.
            if (mProgressAnimator != null) {
                mProgressAnimator.cancel();
                mProgressAnimator = null;
            }

            /**
             * 设置播放进度
             */
            final int progress = state != null
                    ? (int) state.getPosition()
                    : 0;
            setProgress(progress);


            /**
             *
             */
            // If the media is playing then the seekbar should follow it, and the easiest
            // way to do that is to create a ValueAnimator to update it so the bar reaches
            // the end of the media the same time as playback gets there (or close enough).
            if (state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                final int timeToEnd = (int) ((getMax() - progress) / state.getPlaybackSpeed());

                mProgressAnimator = ValueAnimator.ofInt(progress, getMax())
                        .setDuration(timeToEnd);
                mProgressAnimator.setInterpolator(new LinearInterpolator());
                mProgressAnimator.addUpdateListener(this);
                mProgressAnimator.start();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);

            final int max = metadata != null
                    ? (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                    : 0;
            setProgress(0);
            setMax(max);
        }

        @Override
        public void onAnimationUpdate(final ValueAnimator valueAnimator) {
            // If the user is changing the slider, cancel the animation.
            if (mIsTracking) {
                valueAnimator.cancel();
                return;
            }

            final int animatedIntValue = (int) valueAnimator.getAnimatedValue();
            setProgress(animatedIntValue);
        }
    }
}
