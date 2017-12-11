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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.android.mediasession.R;
import com.example.android.mediasession.client.MediaBrowserManager;
import com.example.android.mediasession.service.contentcatalogs.MusicLibrary;

import java.util.List;

public class MainActivity extends AppCompatActivity {


    /**
     * UI
     */
    // 歌曲标题
    private TextView mTitleTv;
    // 歌曲作者
    private TextView mArtistTv;
    // 歌曲图片
    private ImageView mAlbumArtImg;
    // 播放控制器 背景
    private View mControlBgLayout;

    // seekbar
    private MediaSeekBar mSeekBarAudio;

    /**
     * 数据
     */
    // 是否正在播放的标识
    private boolean mIsPlaying;
    //
    private MediaBrowserManager mMediaBrowserManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI
        initUI();
        // 初始化MediaBrowser
        initMediaBrowser();
    }


    @Override
    public void onStart() {
        super.onStart();
        //
        if (mMediaBrowserManager != null) {
            mMediaBrowserManager.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        //
        if (mMediaBrowserManager != null) {
            mMediaBrowserManager.onStop();
        }

    }


    /**
     * 初始化UI
     */
    private void initUI() {
        // 歌曲标题
        mTitleTv = (TextView) findViewById(R.id.song_title_tv);
        // 歌曲作者
        mArtistTv = (TextView) findViewById(R.id.song_artist_tv);
        // 歌曲图片
        mAlbumArtImg = (ImageView) findViewById(R.id.album_art_img);
        // 播放控制器背景
        mControlBgLayout = findViewById(R.id.control_bg_layout);

        // 上一首
        final Button previousBtn = (Button) findViewById(R.id.previous_btn);
        previousBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMediaBrowserManager.getTransportControls().skipToPrevious();
            }
        });
        // 播放按钮
        final Button playBtn = (Button) findViewById(R.id.play_btn);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsPlaying) {
                    mMediaBrowserManager.getTransportControls().pause();
                    //
                } else {
                    mMediaBrowserManager.getTransportControls().play();
                }
            }
        });
        // 下一首
        final Button nextBtn = (Button) findViewById(R.id.next_btn);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMediaBrowserManager.getTransportControls().skipToNext();
            }
        });
        // seekbar
        mSeekBarAudio = (MediaSeekBar) findViewById(R.id.seekbar_audio);
        mSeekBarAudio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // seek
                mMediaBrowserManager.getTransportControls().seekTo(seekBar.getProgress());
            }
        });
    }

    /**
     * 初始化 MediaBrowser
     */
    private void initMediaBrowser() {

        mMediaBrowserManager = new MediaBrowserManager(this);
        mMediaBrowserManager.addOnMediaStatusListener(new MediaBrowserManager.OnMediaStatusChangeListener() {

            /**
             * 播放状态修改
             */
            @Override
            public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
                // 播放音频 状态变化
                onMediaPlaybackStateChanged(state);
            }

            /**
             * 当前播放歌曲信息修改
             */
            @Override
            public void onMetadataChanged(MediaMetadataCompat metadata) {
                // 播放音频变化的回调
                onMediaMetadataChanged(metadata);
            }

            /**
             * 播放队列修改
             */
            @Override
            public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {

            }
        });
    }


    /**
     * 更改播放按钮背景状态
     *
     * @param isPlaying
     */
    private void setControlBg(boolean isPlaying) {
        if (isPlaying) {
            mControlBgLayout.setBackgroundResource(R.drawable.ic_media_with_pause);
        } else {
            mControlBgLayout.setBackgroundResource(R.drawable.ic_media_with_play);
        }
    }


    // ############################################################################################


    /**
     * 音频播放状态变化的回调
     *
     * @param playbackState
     */
    private void onMediaPlaybackStateChanged(PlaybackStateCompat playbackState) {
        if (playbackState == null) {
            return;
        }
        // 正在播放
        mIsPlaying =
                playbackState.getState() == PlaybackStateCompat.STATE_PLAYING;

        // 更新UI
        setControlBg(mIsPlaying);

        /**
         * 设置播放进度
         */
        final int progress = (int) playbackState.getPosition();
        mSeekBarAudio.setProgress(progress);
        switch (playbackState.getState()) {
            case PlaybackStateCompat.STATE_PLAYING:
                final int timeToEnd = (int) ((mSeekBarAudio.getMax() - progress) / playbackState.getPlaybackSpeed());
                mSeekBarAudio.startProgressAnima(progress, mSeekBarAudio.getMax(), timeToEnd);
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                mSeekBarAudio.stopProgressAnima();
                break;

        }

    }


    /**
     * 播放音频数据 发生变化的回调
     *
     * @param mediaMetadata
     */
    private void onMediaMetadataChanged(MediaMetadataCompat mediaMetadata) {
        if (mediaMetadata == null) {
            return;
        }
        // 音频的标题
        mTitleTv.setText(
                mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
        // 音频作者
        mArtistTv.setText(
                mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
        // 音频图片
        mAlbumArtImg.setImageBitmap(MusicLibrary.getAlbumBitmap(
                MainActivity.this,
                mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)));

        // 进度条
        final int max = mediaMetadata != null
                ? (int) mediaMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                : 0;
        mSeekBarAudio.setProgress(0);
        mSeekBarAudio.setMax(max);
    }

    // ############################################################################################


}
