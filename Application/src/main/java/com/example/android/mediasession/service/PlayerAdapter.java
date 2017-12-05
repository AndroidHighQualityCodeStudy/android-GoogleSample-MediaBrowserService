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

package com.example.android.mediasession.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

/**
 * Abstract player implementation that handles playing music with proper handling of headphones
 * and audio focus.
 */
public abstract class PlayerAdapter {

    private static final float MEDIA_VOLUME_DEFAULT = 1.0f;
    private static final float MEDIA_VOLUME_DUCK = 0.2f;


    // 播放的上下文对象
    private final Context mApplicationContext;
    // 获取AudioManager
    private final AudioManager mAudioManager;
    // OnAudioFocusChangeListener
    private final AudioFocusHelper mAudioFocusHelper;
    // 当前是否有焦点
    private boolean mPlayOnAudioFocus = false;


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
     * 构造方法
     *
     * @param context
     */
    public PlayerAdapter(@NonNull Context context) {
        // 上下文对象
        mApplicationContext = context.getApplicationContext();
        // 获取AudioManager
        mAudioManager = (AudioManager) mApplicationContext.getSystemService(Context.AUDIO_SERVICE);
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
            // 注册广播
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
        if (!mPlayOnAudioFocus) {
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
        // 取消广播注册
        unregisterAudioNoisyReceiver();
        // 结束播放
        onStop();
    }

    /**
     * Called when the media must be stopped. The player should clean up resources at this
     * point.
     */
    protected abstract void onStop();

    public abstract void seekTo(long position);

    /**
     * ？？？？？？？？？？？？？？
     *
     * @param volume
     */
    public abstract void setVolume(float volume);


    /**
     * 注册Receiver
     */
    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mApplicationContext.registerReceiver(mAudioNoisyReceiver, AUDIO_NOISY_INTENT_FILTER);
            mAudioNoisyReceiverRegistered = true;
        }
    }


    /**
     * 取消Receiver注册
     */
    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mApplicationContext.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    /**
     * Helper class for managing audio focus related tasks.
     */
    private final class AudioFocusHelper
            implements AudioManager.OnAudioFocusChangeListener {

        /**
         * 请求音频焦点
         *
         * @return
         */
        private boolean requestAudioFocus() {
            // 请求音频焦点  并判断音频焦点的获取情况
            final int result = mAudioManager.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        /**
         * 放弃音频焦点
         */
        private void abandonAudioFocus() {
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
                    // 有焦点 没有播放，则播放
                    if (mPlayOnAudioFocus && !isPlaying()) {
                        play();
                    } else if (isPlaying()) {
                        setVolume(MEDIA_VOLUME_DEFAULT);
                    }
                    mPlayOnAudioFocus = false;
                    break;
                // 播放中失去焦点，可降低播放质量，来维持播放
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    setVolume(MEDIA_VOLUME_DUCK);
                    break;
                // 失去焦点
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (isPlaying()) {
                        mPlayOnAudioFocus = true;
                        pause();
                    }
                    break;
                // 失去焦点
                case AudioManager.AUDIOFOCUS_LOSS:
                    //
                    mAudioManager.abandonAudioFocus(this);
                    mPlayOnAudioFocus = false;
                    stop();
                    break;
            }
        }
    }
}
