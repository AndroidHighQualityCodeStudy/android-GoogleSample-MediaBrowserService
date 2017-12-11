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

package com.example.android.mediasession.client;

import android.content.ComponentName;
import android.content.Context;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.example.android.mediasession.service.MusicService;

import java.util.ArrayList;
import java.util.List;

/**
 * MediaBrowserManager for a MediaBrowser that handles connecting, disconnecting,
 * and basic browsing.
 */
public class MediaBrowserManager {

    private static final String TAG = "MediaBrowserManager";


    private final Context mContext;


    /**
     *
     */
    // MediaBrowserCompat
    private MediaBrowserCompat mMediaBrowserCompat;
    // MediaControllerCompat
    @Nullable
    private MediaControllerCompat mMediaController;
    // service 连接回调
    private final MediaBrowserConnectionCallback mMediaBrowserConnectionCallback =
            new MediaBrowserConnectionCallback();
    // 音频变化回调
    private final MediaControllerCallback mMediaControllerCallback =
            new MediaControllerCallback();
    //
    private final MediaBrowserSubscriptionCallback mMediaBrowserSubscriptionCallback =
            new MediaBrowserSubscriptionCallback();


    /**
     * 构造方法
     *
     * @param context
     */
    public MediaBrowserManager(Context context) {
        mContext = context;
    }

    /**
     * 跟随Activity的生命周期
     */
    public void onStart() {
        //
        if (mMediaBrowserCompat == null) {
            // 创建MediaBrowserCompat
            mMediaBrowserCompat = new MediaBrowserCompat(
                    mContext,
                    // 创建ComponentName 连接 MusicService
                    new ComponentName(mContext, MusicService.class),
                    // 创建callback
                    mMediaBrowserConnectionCallback,
                    //
                    null);
            // 链接service
            mMediaBrowserCompat.connect();
        }
        Log.d(TAG, "onStart: Creating MediaBrowser, and connecting");
    }

    /**
     * 跟随Activity的生命周期
     */
    public void onStop() {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mMediaControllerCallback);
            mMediaController = null;
        }
        if (mMediaBrowserCompat != null && mMediaBrowserCompat.isConnected()) {
            mMediaBrowserCompat.disconnect();
            mMediaBrowserCompat = null;
        }
        // 数据置空
        Log.d(TAG, "onStop: Releasing MediaController, Disconnecting from MediaBrowser");
    }


    /**
     * 获取播放控制器 通过该方法控制播放
     *
     * @return
     */
    public MediaControllerCompat.TransportControls getTransportControls() {
        if (mMediaController == null) {
            Log.d(TAG, "getTransportControls: MediaController is null!");
            throw new IllegalStateException();
        }
        return mMediaController.getTransportControls();
    }


    // ############################################onConnected CallBack################################################

    /**
     * mediaService的链接回调
     */
    // Receives callbacks from the MediaBrowser when it has successfully connected to the
    // MediaBrowserService (MusicService).
    public class MediaBrowserConnectionCallback extends MediaBrowserCompat.ConnectionCallback {

        // 连接成功
        // Happens as a result of onStart().
        @Override
        public void onConnected() {
            try {
                // 获取MediaControllerCompat
                // Get a MediaController for the MediaSession.
                mMediaController = new MediaControllerCompat(
                        mContext,
                        mMediaBrowserCompat.getSessionToken());
                mMediaController.registerCallback(mMediaControllerCallback);

                /**
                 * 设置当前数据
                 */
                // Sync existing MediaSession state to the UI.
                mMediaControllerCallback.onMetadataChanged(mMediaController.getMetadata());
                mMediaControllerCallback.onPlaybackStateChanged(mMediaController.getPlaybackState());


            } catch (RemoteException e) {
                Log.d(TAG, String.format("onConnected: Problem: %s", e.toString()));
                throw new RuntimeException(e);
            }

            mMediaBrowserCompat.subscribe(mMediaBrowserCompat.getRoot(), mMediaBrowserSubscriptionCallback);
        }
    }

    // ############################################onChildrenLoaded CallBack################################################


    /**
     * 加载新数据后调用
     */
    // Receives callbacks from the MediaBrowser when the MediaBrowserService has loaded new media
    // that is ready for playback.
    public class MediaBrowserSubscriptionCallback extends MediaBrowserCompat.SubscriptionCallback {

        /**
         * service 的数据发送到这里
         *
         * @param parentId
         * @param children
         */
        @Override
        public void onChildrenLoaded(@NonNull String parentId,
                                     @NonNull List<MediaBrowserCompat.MediaItem> children) {
            if (mMediaController == null) {
                return;
            }
            // Queue up all media items for this simple sample.
            for (final MediaBrowserCompat.MediaItem mediaItem : children) {
                mMediaController.addQueueItem(mediaItem.getDescription());
            }

            // Call "playFromMedia" so the UI is updated.
            mMediaController.getTransportControls().prepare();
        }
    }


    // ############################################MediaControllerCallback CallBack################################################


    /**
     * service 通过MediaControllerCallback 回调到client
     */
    public class MediaControllerCallback extends MediaControllerCompat.Callback {

        @Override
        public void onMetadataChanged(final MediaMetadataCompat metadata) {
            //
            for (OnMediaStatusChangeListener callback : mMediaStatusChangeListenerList) {
                callback.onMetadataChanged(metadata);
            }
        }

        @Override
        public void onPlaybackStateChanged(@Nullable final PlaybackStateCompat state) {
            //
            for (OnMediaStatusChangeListener callback : mMediaStatusChangeListenerList) {
                callback.onPlaybackStateChanged(state);
            }
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
            //
            for (OnMediaStatusChangeListener callback : mMediaStatusChangeListenerList) {
                callback.onQueueChanged(queue);
            }
        }

        // service被杀死时调用
        @Override
        public void onSessionDestroyed() {
            // onSessionDestroyed: MusicService is dead!!!
            onPlaybackStateChanged(null);
        }

    }

    // ########################################音频变化回调 管理列表###################################################

    /**
     * 音频变化回调 管理列表
     */
    private List<OnMediaStatusChangeListener> mMediaStatusChangeListenerList = new ArrayList<>();

    /**
     * 添加音频变化回调
     *
     * @param l
     */
    public void addOnMediaStatusListener(OnMediaStatusChangeListener l) {
        mMediaStatusChangeListenerList.add(l);
    }

    /**
     * 移除音频变化回调
     *
     * @param l
     */
    public void removeOnMediaStatusListener(OnMediaStatusChangeListener l) {
        mMediaStatusChangeListenerList.remove(l);
    }


    /**
     * 音频变化回调
     */
    public interface OnMediaStatusChangeListener {

        /**
         * 播放状态修改
         */
        void onPlaybackStateChanged(@NonNull PlaybackStateCompat state);

        /**
         * 当前播放歌曲信息修改
         */
        void onMetadataChanged(MediaMetadataCompat metadata);

        /**
         * 播放队列修改
         */
        void onQueueChanged(List<MediaSessionCompat.QueueItem> queue);
    }


}