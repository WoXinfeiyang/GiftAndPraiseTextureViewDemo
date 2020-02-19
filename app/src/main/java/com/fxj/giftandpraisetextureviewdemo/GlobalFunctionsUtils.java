package com.fxj.giftandpraisetextureviewdemo;

import android.content.Context;

/**
 * Created by fuxianjin-hj on 2017/12/3.
 */

public class GlobalFunctionsUtils {
    public static String getAppDir(Context context){
        return FileUtils.getAppDir(context,"GiftAndPraiseTextureViewDemo");
    }
}
