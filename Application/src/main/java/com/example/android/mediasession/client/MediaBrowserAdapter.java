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
 * Adapter for a MediaBrowser that handles connecting, disconnecting,
 * and basic browsing.
 */
public class MediaBrowserAdapter {

    private static final String TAG = "MediaBrowserAdapter";


    private final Context mContext;

    private final MediaBrowserSubscriptionCallback mMediaBrowserSubscriptionCallback =
            new MediaBrowserSubscriptionCallback();


    //
    private MediaBrowserCompat mMediaBrowser;

    @Nullable
    private MediaControllerCompat mMediaController;


    // 存储播放状态与数据
    private final InternalState mState;

    // 连接回调
    private final MediaBrowserConnectionCallback mMediaBrowserConnectionCallback =
            new MediaBrowserConnectionCallback();
    // 音频变化回调
    private final MediaControllerCallback mMediaControllerCallback =
            new MediaControllerCallback();

    /**
     * 构造方法
     *
     * @param context
     */
    public MediaBrowserAdapter(Context context) {
        mContext = context;
        // 存储播放状态与数据
        mState = new InternalState();
    }

    /**
     * 跟随Activity的生命周期
     */
    public void onStart() {
        //
        if (mMediaBrowser == null) {
            // 创建MediaBrowserCompat
            mMediaBrowser = new MediaBrowserCompat(
                    mContext,
                    // 创建ComponentName
                    new ComponentName(mContext, MusicService.class),
                    // 创建callback
                    mMediaBrowserConnectionCallback,
                    //
                    null);
            // 链接service
            mMediaBrowser.connect();
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
        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.disconnect();
            mMediaBrowser = null;
        }
        // 数据置空
        Log.d(TAG, "onStop: Releasing MediaController, Disconnecting from MediaBrowser");
    }

    /**
     * The internal state of the app needs to revert to what it looks like when it started before
     * any connections to the {@link MusicService} happens via the {@link MediaSessionCompat}.
     */
    private void resetState() {
        // service被杀死后，数据置空
        mState.reset();

        Log.d(TAG, "resetState: ");
    }


    /**
     * seekTo
     *
     * @param pos
     */
    public void seekTo(long pos) {
        // 音频seek
        if (mMediaController != null) {
            mMediaController.getTransportControls().seekTo(pos);
        }
    }


    /**
     * 获取播放控制器
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
                        mMediaBrowser.getSessionToken());
                mMediaController.registerCallback(mMediaControllerCallback);

                /**
                 * 设置当前数据
                 */
                // Sync existing MediaSession state to the UI.
                mMediaControllerCallback.onMetadataChanged(
                        mMediaController.getMetadata());
                mMediaControllerCallback
                        .onPlaybackStateChanged(mMediaController.getPlaybackState());


            } catch (RemoteException e) {
                Log.d(TAG, String.format("onConnected: Problem: %s", e.toString()));
                throw new RuntimeException(e);
            }

            mMediaBrowser.subscribe(mMediaBrowser.getRoot(), mMediaBrowserSubscriptionCallback);
        }
    }

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
            assert mMediaController != null;

            // Queue up all media items for this simple sample.
            for (final MediaBrowserCompat.MediaItem mediaItem : children) {
                mMediaController.addQueueItem(mediaItem.getDescription());
            }

            // Call "playFromMedia" so the UI is updated.
            mMediaController.getTransportControls().prepare();
        }
    }


    /**
     * service 通过MediaControllerCallback 回调到client
     */
    public class MediaControllerCallback extends MediaControllerCompat.Callback {

        @Override
        public void onMetadataChanged(final MediaMetadataCompat metadata) {


            for (OnMediaStatusChangeListener callback : mMediaStatusChangeListenerList) {
                callback.onMetadataChanged(metadata);
            }


            // Filtering out needless updates, given that the metadata has not changed.
            if (isMediaIdSame(metadata, mState.getMediaMetadata())) {
                Log.d(TAG, "onMetadataChanged: Filtering out needless onMetadataChanged() update");
                return;
            } else {
                // 设置音频数据
                mState.setMediaMetadata(metadata);
            }

        }

        @Override
        public void onPlaybackStateChanged(@Nullable final PlaybackStateCompat state) {


            for (OnMediaStatusChangeListener callback : mMediaStatusChangeListenerList) {
                callback.onPlaybackStateChanged(state);
            }

            // 设置播放状态数据
            mState.setPlaybackState(state);
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);


            for (OnMediaStatusChangeListener callback : mMediaStatusChangeListenerList) {
                callback.onQueueChanged(queue);
            }
        }

        // service被杀死时调用
        // This might happen if the MusicService is killed while the Activity is in the
        // foreground and onStart() has been called (but not onStop()).
        @Override
        public void onSessionDestroyed() {

            resetState();
            onPlaybackStateChanged(null);
            Log.d(TAG, "onSessionDestroyed: MusicService is dead!!!");
        }

        /**
         * 通过id判断是否为同一个音频
         *
         * @param currentMedia
         * @param newMedia
         * @return
         */
        private boolean isMediaIdSame(MediaMetadataCompat currentMedia,
                                      MediaMetadataCompat newMedia) {
            if (currentMedia == null || newMedia == null) {
                return false;
            }
            String newMediaId =
                    newMedia.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
            String currentMediaId =
                    currentMedia.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
            return newMediaId.equals(currentMediaId);
        }

    }


    /**
     * 播放状态与数据
     */
    // A holder class that contains the internal state.
    public class InternalState {

        // 播放状态
        private PlaybackStateCompat playbackState;
        // 播放数据
        private MediaMetadataCompat mediaMetadata;


        /**
         * service被杀死后，数据置空
         */
        public void reset() {
            playbackState = null;
            mediaMetadata = null;
        }

        public PlaybackStateCompat getPlaybackState() {
            return playbackState;
        }

        public void setPlaybackState(PlaybackStateCompat playbackState) {
            this.playbackState = playbackState;
        }

        public MediaMetadataCompat getMediaMetadata() {
            return mediaMetadata;
        }

        public void setMediaMetadata(MediaMetadataCompat mediaMetadata) {
            this.mediaMetadata = mediaMetadata;
        }
    }


    // ###########################################################################################

    // 音频变化的回调
    private List<OnMediaStatusChangeListener> mMediaStatusChangeListenerList = new ArrayList<>();

    //
    public void addOnMediaStatusListener(OnMediaStatusChangeListener l) {
        mMediaStatusChangeListenerList.add(l);
    }

    public void removeOnMediaStatusListener(OnMediaStatusChangeListener l) {
        mMediaStatusChangeListenerList.remove(l);
    }


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