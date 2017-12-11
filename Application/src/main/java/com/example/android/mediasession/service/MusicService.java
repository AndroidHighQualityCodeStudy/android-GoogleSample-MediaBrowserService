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

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.example.android.mediasession.service.contentcatalogs.MusicLibrary;
import com.example.android.mediasession.service.notifications.MediaNotificationManager;
import com.example.android.mediasession.service.player.MediaPlayerManager;

import java.util.ArrayList;
import java.util.List;


/**
 * MediaBrowserServiceCompat
 */
public class MusicService extends MediaBrowserServiceCompat {

    private static final String TAG = "MusicService";


    //
    private MediaPlayerManager mMediaPlayerManager;
    private MediaNotificationManager mMediaNotificationManager;

    private boolean mServiceInStartedState;


    /**
     *
     */
    // 与MediaControl交互的MediaSessionCompat
    private MediaSessionCompat mMediaSessionCompat;

    @Override
    public void onCreate() {
        super.onCreate();

        /**
         * MediaSessionCompat
         */
        // 创建MediaSessionCompat
        mMediaSessionCompat = new MediaSessionCompat(this, "MusicService");
        // setCallBack
        mMediaSessionCompat.setCallback(new MediaSessionCallback());
        mMediaSessionCompat.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        // setSessionToken
        setSessionToken(mMediaSessionCompat.getSessionToken());

        /**
         * MediaNotificationManager
         */
        mMediaNotificationManager = new MediaNotificationManager(this);

        /**
         * MediaPlayerManager
         */
        mMediaPlayerManager = new MediaPlayerManager(this, new MediaPlayerListener());
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        mMediaNotificationManager.onDestroy();
        mMediaPlayerManager.stop();
        mMediaSessionCompat.release();
        Log.d(TAG, "onDestroy: MediaPlayerManager stopped, and MediaSession released");
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 Bundle rootHints) {
        return new BrowserRoot(MusicLibrary.getRoot(), null);
    }

    @Override
    public void onLoadChildren(
            @NonNull final String parentMediaId,
            @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(MusicLibrary.getMediaItems());
    }


    // #################################################################################

    /**
     * MediaSessionCallback
     * <p>
     * 用户对UI的操作将最终回调到这里。通过MediaSessionCallback 操作播放器
     * <p>
     * The callback class will receive all the user's actions, like play, pause, etc;
     */
    public class MediaSessionCallback extends MediaSessionCompat.Callback {
        // 播放列表
        private final List<MediaSessionCompat.QueueItem> mPlaylist = new ArrayList<>();
        private int mQueueIndex = -1;
        // 准备播放的音频数据
        private MediaMetadataCompat mPreparedMedia;

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            //
            mPlaylist.add(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            //
            mQueueIndex = (mQueueIndex == -1) ? 0 : mQueueIndex;
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            mPlaylist.remove(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mPlaylist.isEmpty()) ? -1 : mQueueIndex;
        }

        @Override
        public void onPrepare() {
            if (mQueueIndex < 0 && mPlaylist.isEmpty()) {
                // Nothing to play.
                return;
            }

            final String mediaId = mPlaylist.get(mQueueIndex).getDescription().getMediaId();
            // 根据音频 获取音频数据
            mPreparedMedia = MusicLibrary.getMetadata(MusicService.this, mediaId);
            // 设置音频数据
            // 该方法将回调到 Client 的 MediaControllerCallback.onMetadataChanged
            mMediaSessionCompat.setMetadata(mPreparedMedia);
            // 激活mediaSession
            if (!mMediaSessionCompat.isActive()) {
                mMediaSessionCompat.setActive(true);
            }
        }

        @Override
        public void onPlay() {
            //
            if (!isReadyToPlay()) {
                // Nothing to play.
                return;
            }
            // 准备数据
            if (mPreparedMedia == null) {
                onPrepare();
            }
            // 播放
            mMediaPlayerManager.playFromMedia(mPreparedMedia);
            Log.d(TAG, "onPlayFromMediaId: MediaSession active");
        }

        @Override
        public void onPause() {
            mMediaPlayerManager.pause();
        }

        @Override
        public void onStop() {
            mMediaPlayerManager.stop();
            mMediaSessionCompat.setActive(false);
        }

        @Override
        public void onSkipToNext() {
            mQueueIndex = (++mQueueIndex % mPlaylist.size());
            mPreparedMedia = null;
            onPlay();
        }

        @Override
        public void onSkipToPrevious() {
            mQueueIndex = mQueueIndex > 0 ? mQueueIndex - 1 : mPlaylist.size() - 1;
            mPreparedMedia = null;
            onPlay();
        }

        @Override
        public void onSeekTo(long pos) {
            mMediaPlayerManager.seekTo(pos);
        }

        /**
         * 判断列表数据状态
         *
         * @return
         */
        private boolean isReadyToPlay() {
            return (!mPlaylist.isEmpty());
        }
    }


    // #################################################################################


    /**
     * MediaPlayer 播放状态回调
     */
    public class MediaPlayerListener extends PlaybackInfoListener {

        private final ServiceManager mServiceManager;

        MediaPlayerListener() {
            mServiceManager = new ServiceManager();
        }

        @Override
        public void onPlaybackStateChange(PlaybackStateCompat state) {
            // 最终回调到Client 的 MediaControllerCallback.onPlaybackStateChanged
            mMediaSessionCompat.setPlaybackState(state);

            // Manage the started state of this service.
            switch (state.getState()) {
                case PlaybackStateCompat.STATE_PLAYING:
                    mServiceManager.moveServiceToStartedState(state);
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    mServiceManager.updateNotificationForPause(state);
                    break;
                case PlaybackStateCompat.STATE_STOPPED:
                    mServiceManager.moveServiceOutOfStartedState(state);
                    break;
            }
        }

        @Override
        public void onPlaybackCompleted() {

        }

        class ServiceManager {
            /**
             * @param state
             */
            private void moveServiceToStartedState(PlaybackStateCompat state) {
                //
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                mMediaPlayerManager.getCurrentMedia(), state, getSessionToken());
                //
                if (!mServiceInStartedState) {
                    ContextCompat.startForegroundService(
                            MusicService.this,
                            new Intent(MusicService.this, MusicService.class));
                    mServiceInStartedState = true;
                }
                //
                startForeground(MediaNotificationManager.NOTIFICATION_ID, notification);
            }

            /**
             * @param state
             */
            private void updateNotificationForPause(PlaybackStateCompat state) {
                stopForeground(false);
                Notification notification =
                        mMediaNotificationManager.getNotification(
                                mMediaPlayerManager.getCurrentMedia(), state, getSessionToken());
                mMediaNotificationManager.getNotificationManager()
                        .notify(MediaNotificationManager.NOTIFICATION_ID, notification);
            }

            /**
             * @param state
             */
            private void moveServiceOutOfStartedState(PlaybackStateCompat state) {
                stopForeground(true);
                stopSelf();
                mServiceInStartedState = false;
            }
        }

    }

}