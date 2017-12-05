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
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.android.mediasession.R;
import com.example.android.mediasession.client.MediaBrowserAdapter;
import com.example.android.mediasession.service.contentcatalogs.MusicLibrary;

public class MainActivity extends AppCompatActivity {


    // 歌曲标题
    private TextView mTitleTextView;
    // 歌曲作者
    private TextView mArtistTextView;
    // 歌曲图片
    private ImageView mAlbumArt;
    // 播放器控制器背景
    private ImageView mMediaControlsImage;
    // seekbar
    private MediaSeekBar mSeekBarAudio;

    //
    private MediaBrowserAdapter mMediaBrowserAdapter;

    /**
     *
     */
    private boolean mIsPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //
        initializeUI();
        //
        mMediaBrowserAdapter = new MediaBrowserAdapter(this);
        mMediaBrowserAdapter.addListener(new MediaBrowserListener());
    }

    /**
     *
     */
    private void initializeUI() {
        // 歌曲标题
        mTitleTextView = (TextView) findViewById(R.id.song_title);
        // 歌曲作者
        mArtistTextView = (TextView) findViewById(R.id.song_artist);
        // 歌曲图片
        mAlbumArt = (ImageView) findViewById(R.id.album_art);
        // 播放器控制器背景
        mMediaControlsImage = (ImageView) findViewById(R.id.media_controls);
        // seekbar
        mSeekBarAudio = (MediaSeekBar) findViewById(R.id.seekbar_audio);
        // 上一首
        final Button buttonPrevious = (Button) findViewById(R.id.button_previous);
        buttonPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMediaBrowserAdapter.getTransportControls().skipToPrevious();
            }
        });
        // 播放按钮
        final Button buttonPlay = (Button) findViewById(R.id.button_play);
        buttonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsPlaying) {
                    mMediaBrowserAdapter.getTransportControls().pause();
                } else {
                    mMediaBrowserAdapter.getTransportControls().play();
                }
            }
        });
        // 下一首
        final Button buttonNext = (Button) findViewById(R.id.button_next);
        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMediaBrowserAdapter.getTransportControls().skipToNext();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowserAdapter.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mSeekBarAudio.disconnectController();
        mMediaBrowserAdapter.onStop();
    }

    private class MediaBrowserListener extends MediaBrowserAdapter.MediaBrowserChangeListener {

        @Override
        public void onConnected(@Nullable MediaControllerCompat mediaController) {
            super.onConnected(mediaController);
            mSeekBarAudio.setMediaController(mediaController);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
            // 正在播放
            mIsPlaying = playbackState != null &&
                    playbackState.getState() == PlaybackStateCompat.STATE_PLAYING;
            // 这是要干什么??????????
            mMediaControlsImage.setPressed(mIsPlaying);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat mediaMetadata) {
            if (mediaMetadata == null) {
                return;
            }
            // 音频的标题
            mTitleTextView.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            // 音频作者
            mArtistTextView.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            // 音频图片
            mAlbumArt.setImageBitmap(MusicLibrary.getAlbumBitmap(
                    MainActivity.this,
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)));
        }
    }
}
