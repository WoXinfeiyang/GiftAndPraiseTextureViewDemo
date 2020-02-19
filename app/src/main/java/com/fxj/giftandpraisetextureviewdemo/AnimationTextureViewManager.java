package com.fxj.giftandpraisetextureviewdemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.TextureView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by fuxianjin-hj on 2017/12/8.
 */

public class AnimationTextureViewManager {

    private static final String TAG= AnimationTextureViewManager.class.getSimpleName();

    private Context mContext;

    private TextureView mTextureView;



    public SurfaceTextureCallback mSurfaceTextureCallback;

    /**是否是从assets目录下获取到的图片资源文件*/
    private boolean isAssets;
    private AssetManager am;

    /**图片路径集合*/
    public List<String> pathList;
    /**图片总数*/
    private int totalCount=0;

    /**图片缓存集合*/
    private SparseArray<Bitmap> mPicCache;
    /**每次缓存大小*/
    private int mCacheCount=5;
    /**单次动画*/
    public static int MODE_ONCE=-1;
    /**有限次数动画*/
    public static int MODE_LIMITED_TIMES=-2;
    /**循环动画*/
    public static int MODE_INFINITE=-3;

    /**动画播放模式*/
    private int mode=MODE_ONCE;

    /**有限次动画执行次数*/
    private int limitedTimes=1;

    /**每一帧动画播放时间间隔(每一张图片播放时间间隔)*/
    private int mFrameInterval = 100;


    private Handler mDecodeHandler;

    private AnimationStateListener mAnimationStateListener;

    private UnexceptedStopListener mUnexceptedStopListener;


    public void setAnimationStateListener(AnimationStateListener animationStateListener){
        this.mAnimationStateListener=animationStateListener;
    }

    /**动画状态监听*/
    public interface AnimationStateListener{
        /**动画开始*/
        void onStart();
        /**动画结束*/
        void onFinish();
    }

    public interface UnexceptedStopListener{
        void unexceptedStop(int position);
    }

    private class SurfaceTextureCallback implements TextureView.SurfaceTextureListener{

        private final String TAG= AnimationTextureViewManager.class.getSimpleName()+"_"+ SurfaceTextureCallback.class.getSimpleName();

        private boolean isAvailable=false;

        private Canvas canvas;
        /**是否正在绘制标志位*/
        private boolean isDrawing=false;
        /**绘制线程*/
        private Thread drawThread;

        private int position;
        /**真实图片资源位置*/
        private int picPosition;

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG,"#onSurfaceTextureAvailable#width="+width+",height="+height);
            isAvailable=true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(TAG,"#onSurfaceTextureSizeChanged#width="+width+",height="+height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(TAG,"#onSurfaceTextureDestroyed#");
            isAvailable=false;
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            Log.d(TAG,"#onSurfaceTextureUpdated#");
        }
        /**开启动画绘制图片,通过绘制线程drawThread在TextureView上利用Canvas一张图片一张图片的去绘制*/
        public void startAnimation(){
            if(mAnimationStateListener!=null){
                mAnimationStateListener.onStart();
            }
            this.isDrawing=true;

            drawThread=new Thread(){
                @SuppressLint("LongLogTag")
                @Override
                public void run() {
                    while(isDrawing){
                        try {
                            long now=System.currentTimeMillis();
                            drawBitmap();

                            Thread.sleep(mFrameInterval-(System.currentTimeMillis()-now)>0?mFrameInterval-(System.currentTimeMillis()-now):0);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            Log.e("AnimationTextureViewManager_SurfaceCallback","InterruptedException:"+e);
                        }
                    }
                }
            };
            if(isAvailable){
                drawThread.start();
            }
        }

        public void stopAnimation(){
            isDrawing=false;
            if(mAnimationStateListener!=null){
                mAnimationStateListener.onFinish();
            }
            position=0;
            drawClear();
            if(drawThread!=null){
                drawThread.interrupt();
            }

        }



        private void drawBitmap(){
            Bitmap bitmap=null;
            if(mode==MODE_ONCE){/*单次播放模式*/
                if(position>=totalCount){
                    /*处理停止播放逻辑*/
                    drawClear();
                    stopAnimation();
                    return;
                }else{
                    picPosition=position;
                }
            }else if(mode==MODE_LIMITED_TIMES){/*有限次播放模式*/
                if(position>=limitedTimes*totalCount){
                    /*处理停止播放逻辑*/
                    drawClear();
                    stopAnimation();
                    return;
                }else if((position>=totalCount)&&(position<limitedTimes*totalCount)){
                    picPosition=position%totalCount;
                }else{
                    picPosition=position;
                }
            }else if(mode==MODE_INFINITE){/*循环执行动画*/
                if(position>=totalCount){
                    picPosition=position%totalCount;
                }else{
                    picPosition=position;
                }
            }

            bitmap=mPicCache.get(picPosition);
            mDecodeHandler.sendEmptyMessage(position);/*发送Handler消息准备进行图片文件的缓存*/
            if(bitmap==null){
                Log.e(TAG,"#drawBitmap#get bitmap in position: " + position + " is null ,animation was forced to stop");
                throw new RuntimeException("get bitmap in position: " + position + " is null ,animation was forced to stop");
            }
            canvas=mTextureView.lockCanvas();
            Log.d(TAG,"#drawBitmap#canvas="+canvas);
            if(canvas==null){
                return;
            }
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);/*清空画布*/
            configureDrawMatrix(bitmap);
            canvas.drawBitmap(bitmap,0,0,null);
            mTextureView.unlockCanvasAndPost(canvas);
            position++;
        }

        /**清除Surface,通过给Surface绘制Color.TRANSPARENT透明色的方式来清除*/
        public void drawClear(){
            canvas=mTextureView.lockCanvas();
            Log.d(TAG,"#drawClear#canvas="+canvas);
            if(canvas!=null){
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                mTextureView.unlockCanvasAndPost(canvas);
            }
        }


    }

    private void init(TextureView textureView) {
        this.mTextureView =textureView;

        this.mTextureView.setOpaque(false);/*设置TextureView是否不透明*/

        this.mSurfaceTextureCallback =new SurfaceTextureCallback();
        this.mTextureView.setSurfaceTextureListener(this.mSurfaceTextureCallback);
        ;
        this.mContext =textureView.getContext();
        this.mPicCache=new SparseArray<Bitmap>();
        this.mDrawMatrix=new Matrix();
    }

    /**
     * 通过assets目录下子目录获取图片资源文件路径集合
     * @param assetsFolder assets目录下的子目录
     * */
    private List<String> getPathList(String assetsFolder){
        List<String> pathList=new ArrayList<>();
        AssetManager assetManager=mContext.getAssets();
        try {
            String[] assetsFiles=assetManager.list(assetsFolder);
            if(assetsFiles.length==0){
                Log.e(TAG,"no file in this asset directory");
                return new ArrayList<>(0);
            }
            for(int i=0;i<assetsFiles.length;i++){
                assetsFiles[i]=assetsFolder+ File.separator+assetsFiles[i];
                pathList.add(assetsFiles[i]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.totalCount=pathList.size();
        isAssets=true;
        this.am=assetManager;
        return pathList;
    }

    /**通过图片资源文件夹File对象resourceFolder获取图片路径集合
     * @param resourceFolder 图片资源文件夹
     * @return
     * */
    private List<String> getPathList(File resourceFolder){
        List<String> pathList=new ArrayList<String>();

        if(resourceFolder!=null){
            if(resourceFolder.exists()&&resourceFolder.isDirectory()){/*File对象存在且为文件夹*/
                File[] files=resourceFolder.listFiles();
                if(files.length==0){
                    Log.e(TAG,"No file in this folder");
                }else{
                    for(int i=0;i<files.length;i++){
                        pathList.add(files[i].getAbsolutePath());
                    }
                }
            }else if(!resourceFolder.exists()){
                Log.e(TAG,"this folder not exists!");
            }else{
                Log.e(TAG,"this file object is not a directory!");
            }
        }else{
            Log.e(TAG,"File Object is null");
        }
        this.totalCount=pathList.size();
        isAssets=false;
        this.am=null;
        return pathList;
    }

    /**初始化图片路径集合,主要任务：对传入的图片路径集合进行排序*/
    private void initPathList(List<String> list){
        this.pathList=list;
        if(list==null){
            throw new NullPointerException("this list is null");
        }
        if(this.mCacheCount>this.pathList.size()){
            this.mCacheCount=this.pathList.size();
        }
        Collections.sort(list);
    }

    /**设置动画模式*/
    private void setAnimationMode(int mode){
        this.mode=mode;
    }

    /**设置动画播放时间间隔*/
    private void setFrameInterval(int frameInterval){
        this.mFrameInterval=frameInterval;
    }

    /**设置有限次播放动画的次数*/
    private void setModeLimitedTimes(int limitedTimes){
        if(mode==MODE_LIMITED_TIMES){
            this.limitedTimes=limitedTimes;
        }else{
            throw new RuntimeException("the current mode is not MODE_LIMITED_TIMES,can not set limitedTimes!");
        }
    }

    private Matrix mDrawMatrix;
    /**
     * 给定的matrix
     */
    private final int SCALE_TYPE_MATRIX = 0;
    /**
     * 完全拉伸，不保持原始图片比例，铺满
     */
    public static final int SCALE_TYPE_FIT_XY = 1;

    /**
     * 保持原始图片比例，整体拉伸图片至少填充满X或者Y轴的一个
     * 并最终依附在视图的上方或者左方
     */
    public static final int SCALE_TYPE_FIT_START = 2;

    /**
     * 保持原始图片比例，整体拉伸图片至少填充满X或者Y轴的一个
     * 并最终依附在视图的中心
     */
    public static final int SCALE_TYPE_FIT_CENTER = 3;

    /**
     * 保持原始图片比例，整体拉伸图片至少填充满X或者Y轴的一个
     * 并最终依附在视图的下方或者右方
     */
    public static final int SCALE_TYPE_FIT_END = 4;

    /**
     * 将图片置于视图中央，不缩放
     */
    public static final int SCALE_TYPE_CENTER = 5;

    /**
     * 整体缩放图片，保持原始比例，将图片置于视图中央，
     * 确保填充满整个视图，超出部分将会被裁剪
     */
    public static final int SCALE_TYPE_CENTER_CROP = 6;

    /**
     * 整体缩放图片，保持原始比例，将图片置于视图中央，
     * 确保X或者Y至少有一个填充满屏幕
     */
    public static final int SCALE_TYPE_CENTER_INSIDE = 7;

    private int mScaleType=SCALE_TYPE_FIT_XY;
    private int mLastFrameWidth = -1;
    private int mLastFrameHeight = -1;
    private int mLastFrameScaleType = -1;
    private int mLastSurfaceWidth;
    private int mLastSurfaceHeight;

    /**
     * 根据ScaleType配置绘制bitmap的Matrix
     * @param bitmap
     */
    private void configureDrawMatrix(Bitmap bitmap){
        final int srcWidth = bitmap.getWidth();
        final int dstWidth = mTextureView.getWidth();
        final int srcHeight = bitmap.getHeight();
        final int dstHeight = mTextureView.getHeight();
        final boolean nothingChanged =
                srcWidth == mLastFrameWidth
                        && srcHeight == mLastFrameHeight
                        && mLastFrameScaleType == mScaleType
                        && mLastSurfaceWidth == dstWidth
                        && mLastSurfaceHeight == dstHeight;
        if (nothingChanged) {
            return;
        }
        mLastFrameScaleType = mScaleType;
        mLastFrameHeight = bitmap.getHeight();
        mLastFrameWidth = bitmap.getWidth();
        mLastSurfaceHeight = mTextureView.getHeight();
        mLastSurfaceWidth = mTextureView.getWidth();
        if (mScaleType == SCALE_TYPE_MATRIX) {
            return;
        } else if (mScaleType == SCALE_TYPE_CENTER) {
            mDrawMatrix.setTranslate(
                    Math.round((dstWidth - srcWidth) * 0.5f),
                    Math.round((dstHeight - srcHeight) * 0.5f));
        } else if (mScaleType == SCALE_TYPE_CENTER_CROP) {
            float scale;
            float dx = 0, dy = 0;
            //按照高缩放
            if (dstHeight * srcWidth > dstWidth * srcHeight) {
                scale = (float) dstHeight / (float) srcHeight;
                dx = (dstWidth - srcWidth * scale) * 0.5f;
            } else {
                scale = (float) dstWidth / (float) srcWidth;
                dy = (dstHeight - srcHeight * scale) * 0.5f;
            }
            mDrawMatrix.setScale(scale, scale);
            mDrawMatrix.postTranslate(dx, dy);
        } else if (mScaleType == SCALE_TYPE_CENTER_INSIDE) {
            float scale;
            float dx;
            float dy;
            //小于dst时不缩放
            if (srcWidth <= dstWidth && srcHeight <= dstHeight) {
                scale = 1.0f;
            } else {
                scale = Math.min((float) dstWidth / (float) srcWidth,
                        (float) dstHeight / (float) srcHeight);
            }
            dx = Math.round((dstWidth - srcWidth * scale) * 0.5f);
            dy = Math.round((dstHeight - srcHeight * scale) * 0.5f);

            mDrawMatrix.setScale(scale, scale);
            mDrawMatrix.postTranslate(dx, dy);
        } else {
            RectF srcRect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            RectF dstRect = new RectF(0, 0, mTextureView.getWidth(), mTextureView.getHeight());
            mDrawMatrix.setRectToRect(srcRect, dstRect, MATRIX_SCALE_ARRAY[mScaleType - 1]);
        }
    }

    private static final Matrix.ScaleToFit[] MATRIX_SCALE_ARRAY = {
            Matrix.ScaleToFit.FILL,
            Matrix.ScaleToFit.START,
            Matrix.ScaleToFit.CENTER,
            Matrix.ScaleToFit.END
    };

    private void setScaleType(int mScaleType){
        this.mScaleType=mScaleType;
    }

    public void setMatrix(Matrix matrix){
        this.mDrawMatrix=matrix;
    }
    /**启动播放动画,主要任务:1、从本地或assets目录下读取文件利用缓存线程decodeThread将文件缓存到内存.
     * 2、利用渲染绘制线程drawThread线程通过Canvas绘制到TextureView上；*/
    public void start(){
        if(pathList==null){
            throw new NullPointerException("the frame list is null. did you have configured the resources? if not please call start(file) or start(assetsPath)");
        }

        if(pathList.isEmpty()){
            Log.e(TAG, "pathList is empty, nothing to display. ensure you have configured the resources correctly. check you file or assets directory ");
            return;
        }
        totalCount=pathList.size();

        startDecodeThread();
    }

    /**开始启动动画消息*/
    private static int MSG_START_ANIMATION=-1;
    /**停止动画消息*/
    private static int MSG_STOP_ANIMATION=-2;
    /**解码图片并缓存到内存线程*/
    private Thread decodeThread;
    /**解码缓存线程,从本地或assets目录下读取图片并进行缓存到内存*/
    private void  startDecodeThread(){
        decodeThread=new Thread(){
            @Override
            public void run() {
                Looper.prepare();

                mDecodeHandler=new Handler(Looper.myLooper()){
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        if(msg.what==MSG_STOP_ANIMATION){
                            decodeBitmap(MSG_STOP_ANIMATION);
                            getLooper().quit();
                            return;
                        }
                        decodeBitmap(msg.what);
                    }
                };

                decodeBitmap(MSG_START_ANIMATION);

                Looper.loop();
            }
        };

        decodeThread.start();
    }

    /**根据不同消息来从本地或者assets目录下读取图片文件进行缓存
     * @param msg 当msg>=0时,msg表示位置,根据位置的变化不断进行缓存.
     *            当msg=MSG_START_ANIMATION,开始启动动画.
     *            当msg=MSG_STOP_ANIMATION,停止动画
     * */
    private void decodeBitmap(int msg){
        if(msg==MSG_START_ANIMATION){/*启动动画*/
            if(mCacheCount>totalCount){
                mCacheCount=totalCount;
            }
            for(int i=0;i<mCacheCount;i++){

                mPicCache.put(i,getBitamp(pathList.get(i)));
            }
            mSurfaceTextureCallback.startAnimation();
        }else if(msg==MSG_STOP_ANIMATION){/**停止动画*/
            mPicCache.clear();
            mSurfaceTextureCallback.stopAnimation();
        }else{
            if(msg+mCacheCount<totalCount){
                mPicCache.put(msg+mCacheCount,getBitamp(pathList.get(msg+mCacheCount)));
            }
        }
    }

    /**根据路径得到Bitmap对象*/
    private Bitmap getBitamp(String path) {
        Bitmap bitmap=null;
        if(isAssets){/*图片资源文件位于assets目录下*/
            if(am!=null){
                try {
                    InputStream is=am.open(path);
                    bitmap=BitmapFactory.decodeStream(is);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                throw new NullPointerException("AssetsManager is null!");
            }
        }else{/*图片资源文件位于本地*/
            bitmap=BitmapFactory.decodeFile(path);
        }
        return bitmap;
    }

    public void stop(){
        decodeBitmap(MSG_STOP_ANIMATION);
    }

    public static class Builder{
        private AnimationTextureViewManager animationTextureViewManager;

        public void Builder(TextureView textureView,List<String> pathList){
            this.animationTextureViewManager =new AnimationTextureViewManager();
            this.animationTextureViewManager.init(textureView);
            this.animationTextureViewManager.initPathList(pathList);
        }

        public Builder(TextureView textureView,String assetsFolder) {
            this.animationTextureViewManager =new AnimationTextureViewManager();
            this.animationTextureViewManager.init(textureView);
            this.animationTextureViewManager.initPathList(this.animationTextureViewManager.getPathList(assetsFolder));
        }

        public Builder(TextureView textureView,File resourceFolder) {
            this.animationTextureViewManager =new AnimationTextureViewManager();
            this.animationTextureViewManager.init(textureView);
            this.animationTextureViewManager.initPathList(this.animationTextureViewManager.getPathList(resourceFolder));
        }

        public Builder setFrameInterval(int timeMillisecond){
            this.animationTextureViewManager.setFrameInterval(timeMillisecond);
            return this;
        }

        public Builder setAnimationMode(int mode){
            this.animationTextureViewManager.setAnimationMode(mode);
            return this;
        }

        public Builder setModeLimitedTimes(int limitedTimes){
            this.animationTextureViewManager.setModeLimitedTimes(limitedTimes);
            return this;
        }

        public Builder setScaleType(int scaleType){
            this.animationTextureViewManager.setScaleType(scaleType);
            return this;
        }

        public Builder setMatrix(Matrix matrix){
            this.animationTextureViewManager.setMatrix(matrix);
            return this;
        }

        public AnimationTextureViewManager build(){
            return animationTextureViewManager;
        }

    }
}
