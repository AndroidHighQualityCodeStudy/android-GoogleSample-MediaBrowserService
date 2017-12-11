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

package com.example.android.mediasession.service.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;

/**
 * Abstract player implementation that handles playing music with proper handling of headphones
 * and audio focus.
 */
public abstract class PlayerAdapter {


    // 默认的音量 0~1之间
    private static final float MEDIA_VOLUME_DEFAULT = 1.0f;
    // 失去焦点时，降低音量后的音量
    private static final float MEDIA_VOLUME_DUCK = 0.2f;


    /**
     *
     */
    // 播放的上下文对象
    private final Context mContext;
    // 获取AudioManager
    private final AudioManager mAudioManager;
    // OnAudioFocusChangeListener
    private final AudioFocusHelper mAudioFocusHelper;


    /**
     * 构造方法
     *
     * @param context
     */
    public PlayerAdapter(@NonNull Context context) {
        // 上下文对象
        mContext = context.getApplicationContext();
        // 获取AudioManager
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        // OnAudioFocusChangeListener
        mAudioFocusHelper = new AudioFocusHelper();
    }

    public abstract void playFromMedia(MediaMetadataCompat metadata);

    public abstract MediaMetadataCompat getCurrentMedia();

    public abstract boolean isPlaying();


    /**
     * 播放音频
     */
    public final void play() {
        // 是否获取到焦点的判断
        if (mAudioFocusHelper.requestAudioFocus()) {
            // 注册插拔耳机广播
            registerAudioNoisyReceiver();
            // 播放
            onPlay();
        }
    }

    /**
     * Called when media is ready to be played and indicates the app has audio focus.
     */
    protected abstract void onPlay();


    /**
     * 停止播放
     */
    public final void pause() {
        // 丢弃音频焦点
        if (!mPlayingOnAudioFocusLoss) {
            mAudioFocusHelper.abandonAudioFocus();
        }
        // 取消广播注册
        unregisterAudioNoisyReceiver();
        // 暂停播放
        onPause();
    }

    /**
     * Called when media must be paused.
     */
    protected abstract void onPause();


    /**
     * 停止播放
     */
    public final void stop() {
        // 放弃焦点
        mAudioFocusHelper.abandonAudioFocus();
        // 取消耳机插拔广播注册
        unregisterAudioNoisyReceiver();
        // 结束播放
        onStop();
    }

    /**
     * Called when the media must be stopped. The player should clean up resources at this
     * point.
     */
    protected abstract void onStop();

    /**
     * seek to
     *
     * @param position
     */
    public abstract void seekTo(long position);

    /**
     * 设置音频播放音量
     *
     * @param volume
     */
    public abstract void setVolume(float volume);


    // ##########################################获取焦点帮助类###############################################


    // 是否失去焦点时，停止了音频播放
    private boolean mPlayingOnAudioFocusLoss = false;


    /**
     * Helper class for managing audio focus related tasks.
     * <p>
     * 音频焦点
     */
    private final class AudioFocusHelper
            implements AudioManager.OnAudioFocusChangeListener {

        /**
         * 请求音频焦点
         *
         * @return
         */
        public boolean requestAudioFocus() {
            // 请求音频焦点  并判断音频焦点的获取情况
            final int result = mAudioManager.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        /**
         * 放弃音频焦点
         */
        public void abandonAudioFocus() {
            mAudioManager.abandonAudioFocus(this);
        }


        /**
         * 音频焦点变化回调
         *
         * @param focusChange
         */
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                // 获取到音频焦点
                case AudioManager.AUDIOFOCUS_GAIN:
                    // 没有播放&&焦点失去时停止过播放 则播放
                    if (mPlayingOnAudioFocusLoss && !isPlaying()) {
                        play();
                    }
                    // 正在播放
                    else if (isPlaying()) {
                        setVolume(MEDIA_VOLUME_DEFAULT);
                    }
                    mPlayingOnAudioFocusLoss = false;
                    break;
                // 播放中失去焦点，可降低播放质量，来维持播放
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    setVolume(MEDIA_VOLUME_DUCK);
                    break;
                // 失去焦点
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // 失去焦点，暂停播放
                    if (isPlaying()) {
                        mPlayingOnAudioFocusLoss = true;
                        pause();
                    }
                    break;
                // 失去焦点
                case AudioManager.AUDIOFOCUS_LOSS:
                    // 停止播放
                    mAudioManager.abandonAudioFocus(this);
                    mPlayingOnAudioFocusLoss = false;
                    stop();
                    break;
            }
        }
    }


    // ##########################################耳机状态变化的广播接收者###############################################


    /**
     * 耳机插拔等状态变化的监听
     */
    // 监听广播的注册状态
    private boolean mAudioNoisyReceiverRegistered = false;
    // filter
    private static final IntentFilter AUDIO_NOISY_INTENT_FILTER =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    // receiver
    private final BroadcastReceiver mAudioNoisyReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // 耳机插拔变化等的监听
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                        // 正在播放，则停止播放
                        if (isPlaying()) {
                            pause();
                        }
                    }
                }
            };


    /**
     * 注册Receiver
     */
    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mContext.registerReceiver(mAudioNoisyReceiver, AUDIO_NOISY_INTENT_FILTER);
            mAudioNoisyReceiverRegistered = true;
        }
    }


    /**
     * 取消Receiver注册
     */
    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mContext.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }
}
