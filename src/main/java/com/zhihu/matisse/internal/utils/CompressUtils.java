package com.zhihu.matisse.internal.utils;

import android.app.Activity;

import java.io.IOException;
import java.util.List;

import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;

/**
 * Created by chengjuechao on 2017/9/14.
 */

public class CompressUtils {

    public static void CompressPhoto(Activity activity, List<String> photos, OnCompressListener onCompressListener) throws IOException {
        Luban.with(activity.getApplicationContext())
                .load(photos)                                   // 传入要压缩的图片列表
                .ignoreBy(100)                                  // 忽略不压缩图片的大小
                .setTargetDir(activity.getApplicationContext().getExternalCacheDir().getPath())   // 设置压缩后文件存储位置
                .setCompressListener(onCompressListener).launch();    //启动压缩
    }

    public static void CompressPhoto(Activity activity, String photo, OnCompressListener onCompressListener) throws IOException {
        Luban.with(activity.getApplicationContext())
                .load(photo)                                   // 传入要压缩的图片列表
                .ignoreBy(100)                                  // 忽略不压缩图片的大小
                .setTargetDir(activity.getApplicationContext().getExternalCacheDir().getPath())   // 设置压缩后文件存储位置
                .setCompressListener(onCompressListener).launch();    //启动压缩
    }


}
