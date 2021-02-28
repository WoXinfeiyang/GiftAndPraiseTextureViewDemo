package com.fxj.giftandpraisetextureviewdemo;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG="fxj1203";

    private ViewGroup mRootView;
    private VideoView mVideoView;

    private Button giftLBJN;
    private Button giftWHD;
    ArrayList<String> list;

    private TextureView mTextureView;

    AnimationTextureViewManager lbjnGift;
    AnimationTextureViewManager whdGift;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        list=new ArrayList<String>();

        this.mRootView =(RelativeLayout)findViewById(R.id.root_view);

        this.mVideoView= (VideoView) findViewById(R.id.video_view);

        this.mTextureView = (TextureView) findViewById(R.id.texture_view);

        String filePath ="android.resource://"+getPackageName()+ "/"+R.raw.xiao_ping_zi;
        Log.d(TAG,"##onCreate##filePath="+filePath);

        if(!TextUtils.isEmpty(filePath)){
            loadVideoFromLocad(filePath);
        }


        this.lbjnGift = new AnimationTextureViewManager.Builder(this.mTextureView,"zalbjn")
                .setFrameInterval(50)
                .setAnimationMode(AnimationTextureViewManager.MODE_ONCE)
                .setScaleType(AnimationTextureViewManager.SCALE_TYPE_FIT_CENTER)
                .build();

        this.whdGift= new AnimationTextureViewManager.Builder(this.mTextureView,"fymhd")
                .setFrameInterval(50)
                .setAnimationMode(AnimationTextureViewManager.MODE_ONCE)
                .setScaleType(AnimationTextureViewManager.SCALE_TYPE_FIT_CENTER)
                .build();

        this.giftLBJN= (Button) findViewById(R.id.btn_gift_lbjn);
        this.giftLBJN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"giftLBJN onClick");
                lbjnGift.start();
            }
        });

        this.giftWHD =(Button) findViewById(R.id.btn_gift_mhd);
        this.giftWHD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG,"giftWHD onClick");
//                giftView.stopGift();
                whdGift.start();

            }
        });


    }

    /**从本地加载视频*/
    private void loadVideoFromLocad(String filePath) {
        this.mVideoView.setVideoPath(filePath);
        this.mVideoView.start();

        this.mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                Toast.makeText(MainActivity.this,"开始播放",Toast.LENGTH_SHORT).show();
            }
        });

        this.mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                Toast.makeText(MainActivity.this,"播放完毕",Toast.LENGTH_SHORT).show();
            }
        });
    }
}

